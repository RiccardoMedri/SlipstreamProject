package com.cesenahome.data.remote.media

import androidx.core.net.toUri
import com.cesenahome.data.remote.JellyfinClientFactory
import com.cesenahome.data.remote.session.JellyfinSessionManager
import com.cesenahome.data.remote.util.*
import com.cesenahome.domain.models.album.AlbumPagingRequest
import com.cesenahome.domain.models.artist.ArtistPagingRequest
import com.cesenahome.domain.models.playlist.PlaylistPagingRequest
import com.cesenahome.domain.models.song.SongPagingRequest
import org.jellyfin.sdk.api.client.extensions.audioApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

class JellyfinMediaClient(
    private val clientFactory: JellyfinClientFactory,
    private val sessionManager: JellyfinSessionManager,
) {

    //All four fetching methods share the same pattern:
    // - Compute any request-specific filters
    // - Call itemsApi.getItems(...) with named parameters
    // - Return Result.success or Result.failure via runCatching
    suspend fun fetchSongs(
        startIndex: Int,
        limit: Int,
        request: SongPagingRequest,
    ): Result<List<BaseItemDto>> {
        val api = clientFactory.currentApi()
            ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = sessionManager.currentUserId()
            ?: return Result.failure(IllegalStateException("No authenticated user"))

        val parentUuid = listOfNotNull(
            request.albumId.parseUuidOrNull(),
            request.playlistId.parseUuidOrNull(),
        ).firstOrNull()
        val hasParent = parentUuid != null

        return runCatching {
            val response by api.itemsApi.getItems(
                userId = userId,
                parentId = parentUuid,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.AUDIO),
                sortBy = buildSongSortBy(request.sortOption.field, hasParent),
                sortOrder = listOf(request.sortOption.direction.toApiSortOrder()),
                startIndex = startIndex,
                limit = limit,
                searchTerm = request.searchQuery.takeIfNotBlank()
            )
            response.items
        }
    }

    suspend fun fetchAlbums(
        startIndex: Int,
        limit: Int,
        request: AlbumPagingRequest,
    ): Result<List<BaseItemDto>> {
        val api = clientFactory.currentApi()
            ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = sessionManager.currentUserId()
            ?: return Result.failure(IllegalStateException("No authenticated user"))

        return runCatching {
            val response by api.itemsApi.getItems(
                userId = userId,
                albumArtistIds = request.artistId.parseUuidOrNull()?.let { listOf(it) },
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                sortBy = listOf(request.sortOption.field.toApiSortBy()),
                sortOrder = listOf(request.sortOption.direction.toApiSortOrder()),
                startIndex = startIndex,
                limit = limit,
                searchTerm = request.searchQuery.takeIfNotBlank()
            )
            response.items
        }
    }

    suspend fun fetchPlaylists(
        startIndex: Int,
        limit: Int,
        request: PlaylistPagingRequest,
    ): Result<List<BaseItemDto>> {
        val api = clientFactory.currentApi()
            ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = sessionManager.currentUserId()
            ?: return Result.failure(IllegalStateException("No authenticated user"))

        return runCatching {
            val response by api.itemsApi.getItems(
                userId = userId,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                sortBy = listOf(request.sortOption.field.toApiSortBy()),
                sortOrder = listOf(request.sortOption.direction.toApiSortOrder()),
                startIndex = startIndex,
                limit = limit,
                searchTerm = request.searchQuery.takeIfNotBlank()
            )
            response.items
        }
    }

    suspend fun fetchArtists(
        startIndex: Int,
        limit: Int,
        request: ArtistPagingRequest,
    ): Result<List<BaseItemDto>> {
        val api = clientFactory.currentApi()
            ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = sessionManager.currentUserId()
            ?: return Result.failure(IllegalStateException("No authenticated user"))

        return runCatching {
            val response by api.itemsApi.getItems(
                userId = userId,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
                sortBy = listOf(request.sortOption.field.toApiSortBy()),
                sortOrder = listOf(request.sortOption.direction.toApiSortOrder()),
                startIndex = startIndex,
                limit = limit,
                searchTerm = request.searchQuery.takeIfNotBlank()
            )
            response.items
        }
    }

    suspend fun getArtistsCount(): Result<Int> = fetchItemCount(BaseItemKind.MUSIC_ARTIST)
    suspend fun getAlbumsCount(): Result<Int> = fetchItemCount(BaseItemKind.MUSIC_ALBUM)
    suspend fun getPlaylistsCount(): Result<Int> = fetchItemCount(BaseItemKind.PLAYLIST)
    suspend fun getSongsCount(): Result<Int> = fetchItemCount(BaseItemKind.AUDIO)

    fun resolveImageUrl(itemId: String, imageTag: String?, maxSize: Int): Result<String?> {
        val api = clientFactory.currentApi()
            ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val id = itemId.parseUuidOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid item identifier"))
        return runCatching {
            api.imageApi.getItemImageUrl(
                itemId = id,
                imageType = ImageType.PRIMARY,
                tag = imageTag,
                maxWidth = maxSize,
                maxHeight = maxSize,
                quality = 100
            )
        }
    }

    //Return the stream URL to hand to ExoPlayer or the downloader.
    fun resolveAudioUrl(itemId: String): Result<String?> {
        val api = clientFactory.currentApi()
            ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val id = itemId.parseUuidOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid song identifier"))

        val streamUrl = api.audioApi.getAudioStreamUrl(
            itemId = id,
            static = true
        ) ?: return Result.success(null)

        val token = sessionManager.accessToken()
        if (token.isNullOrBlank()) return Result.success(streamUrl)

        val uri = streamUrl.toUri()
        val hasToken = uri.getQueryParameter("api_key").isNullOrEmpty().not()
        val resolved = if (hasToken) {
            streamUrl
        } else {
            uri.buildUpon()
                .appendQueryParameter("api_key", token)
                .build()
                .toString()
        }
        return Result.success(resolved)
    }

    private suspend fun fetchItemCount(vararg kinds: BaseItemKind): Result<Int> {
        val api = clientFactory.currentApi()
            ?: return Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = sessionManager.currentUserId()
            ?: return Result.failure(IllegalStateException("No authenticated user"))

        return runCatching {
            val response by api.itemsApi.getItems(
                userId = userId,
                recursive = true,
                includeItemTypes = kinds.toList(),
                startIndex = 0,
                limit = 0
            )
            response.totalRecordCount ?: response.items.size
        }
    }
}
