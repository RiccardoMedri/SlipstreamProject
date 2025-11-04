package com.cesenahome.data.remote.playlist

import com.cesenahome.data.remote.JellyfinClientFactory
import com.cesenahome.data.remote.media.JellyfinMediaClient
import com.cesenahome.data.remote.session.JellyfinSessionManager
import com.cesenahome.data.remote.util.parseUuidOrNull
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.ItemFilter

class JellyfinPlaylistClient(
    private val clientFactory: JellyfinClientFactory,
    private val sessionManager: JellyfinSessionManager,
    private val mediaClient: JellyfinMediaClient,
) {

    //Locate a playlist whose name matches
    //Using searchTerm narrows results on the server
    //the final equality check ensures an exact name match
    suspend fun findPlaylistByName(name: String): Result<BaseItemDto?> {
        val api = clientFactory.currentApi()
            ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = sessionManager.currentUserId()
            ?: return Result.failure(IllegalStateException("No authenticated user"))

        return runCatching {
            val response by api.itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                searchTerm = name,
                recursive = true,
                limit = 25
            )
            response.items.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }

    //Create a new playlist with an optional initial list of songs
    //Needed to make sure the "Liked Songs" playlist always exists
    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): Result<String> {
        val api = clientFactory.currentApi() ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = sessionManager.currentUserId() ?: return Result.failure(IllegalStateException("No authenticated user"))
        val itemIds = resolveSongIds(songIds).getOrElse { return Result.failure(it) }

        return runCatching {
            val response by api.playlistsApi.createPlaylist(
                data = CreatePlaylistDto(
                    name = name,
                    ids = itemIds,
                    userId = userId,
                    mediaType = null,
                    users = emptyList(),
                    isPublic = false,
                ),
            )
            response.id ?: throw IllegalStateException("Playlist creation returned no identifier")
        }
    }

    //Get all song IDs the user has marked as favourite
    suspend fun fetchFavouriteSongIds(): Result<List<String>> {
        val api = clientFactory.currentApi()
            ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = sessionManager.currentUserId()
            ?: return Result.failure(IllegalStateException("No authenticated user"))

        return runCatching {
            val response by api.itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.AUDIO),
                filters = listOf(ItemFilter.IS_FAVORITE),
                recursive = true,
                limit = 2000
            )
            response.items.mapNotNull { it.id?.toString() }
        }
    }

    suspend fun fetchPlaylistSongIds(playlistId: String): Result<List<String>> {
        val api = clientFactory.currentApi() ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = sessionManager.currentUserId() ?: return Result.failure(IllegalStateException("No authenticated user"))
        val playlistUuid = resolvePlaylistId(playlistId).getOrElse { return Result.failure(it) }
        return runCatching {
            val response by api.playlistsApi.getPlaylistItems(
                playlistId = playlistUuid,
                userId = userId,
            )
            response.items.orEmpty().mapNotNull { it.id?.toString() }
        }
    }

    suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>): Result<Unit> {
        if (songIds.isEmpty()) {
            return Result.success(Unit)
        }
        val api = clientFactory.currentApi() ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = sessionManager.currentUserId() ?: return Result.failure(IllegalStateException("No authenticated user"))
        val playlistUuid = resolvePlaylistId(playlistId).getOrElse { return Result.failure(it) }
        val itemIds = resolveSongIds(songIds).getOrElse { return Result.failure(it) }
        if (itemIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("No valid song identifiers provided"))
        }
        return runCatching {
            api.playlistsApi.addItemToPlaylist(
                playlistId = playlistUuid,
                userId = userId,
                ids = itemIds,
            )
            Unit
        }
    }

    //Jellyfin playlist deletions are by entry id, not item id
    //It first reads the playlist items to map song ids to entry ids
    //then call the remove method
    suspend fun removeSongsFromPlaylist(playlistId: String, songIds: List<String>): Result<Unit> {
        if (songIds.isEmpty()) {
            return Result.success(Unit)
        }
        val api = clientFactory.currentApi() ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val playlistUuid = resolvePlaylistId(playlistId).getOrElse { return Result.failure(it) }
        val userId = sessionManager.currentUserId() ?: return Result.failure(IllegalStateException("No authenticated user"))
        val targetItemIds = resolveSongIds(songIds).getOrElse { return Result.failure(it) }.toSet()
        if (targetItemIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("No valid song identifiers provided"))
        }
        val playlistItems = runCatching {
            val response by api.playlistsApi.getPlaylistItems(
                playlistId = playlistUuid,
                userId = userId,
            )
            response.items.orEmpty()
        }.getOrElse { return Result.failure(it) }

        val entryIds = playlistItems
            .filter { item -> item.id != null && targetItemIds.contains(item.id) }
            .mapNotNull { it.playlistItemId }

        if (entryIds.isEmpty()) {
            return Result.success(Unit)
        }

        return runCatching {
            api.playlistsApi.removeItemFromPlaylist(
                playlistId = playlistUuid.toString(),
                entryIds = entryIds,
            )
            Unit
        }
    }

    suspend fun setAsFavourite(songId: String, isFavourite: Boolean): Result<Unit> {
        val api = clientFactory.currentApi() ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val songUuid = songId.parseUuidOrNull() ?: return Result.failure(IllegalArgumentException("Invalid song identifier"))
        val userUuid = sessionManager.currentUserId() ?: return Result.failure(IllegalStateException("No authenticated user"))
        return runCatching {
            if (isFavourite) {
                api.userLibraryApi.markFavoriteItem(
                    itemId = songUuid,
                    userId = userUuid,
                )
            } else {
                api.userLibraryApi.unmarkFavoriteItem(
                    itemId = songUuid,
                    userId = userUuid,
                )
            }
            Unit
        }
    }

    private fun resolvePlaylistId(playlistId: String): Result<UUID> {
        val uuid = playlistId.parseUuidOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid playlist identifier"))
        return Result.success(uuid)
    }

    private fun resolveSongIds(songIds: List<String>): Result<List<UUID>> {
        val uuids = songIds.mapNotNull { it.parseUuidOrNull() }
        return Result.success(uuids)
    }
}
