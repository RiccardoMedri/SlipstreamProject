package com.cesenahome.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.cesenahome.data.paging.PlaylistPagingSource
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.domain.models.playlist.Playlist
import com.cesenahome.domain.models.playlist.PlaylistPagingRequest
import com.cesenahome.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlin.jvm.Volatile

private const val FAVOURITE_PLAYLIST_NAME = "Favourite Songs"

class PlaylistRepositoryImpl(
    private val apiClient: JellyfinApiClient,
) : PlaylistRepository {

    @Volatile
    private var cachedFavouritePlaylistId: String? = null

    override fun pagingPlaylists(
        pageSize: Int,
        request: PlaylistPagingRequest,
    ): Flow<PagingData<Playlist>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize * 2,
                prefetchDistance = pageSize,
                enablePlaceholders = false,
                maxSize = pageSize * 5,
            ),
            pagingSourceFactory = {
                PlaylistPagingSource(
                    apiClient = apiClient,
                    pageSize = pageSize,
                    request = request,
                )
            }
        ).flow
    }

    override suspend fun ensureFavouritePlaylistId(): Result<String> {
        cachedFavouritePlaylistId?.let { return Result.success(it) }

        val resolveResult = resolveFavouritePlaylistId()
        resolveResult.exceptionOrNull()?.let { return Result.failure(it) }
        val playlistId = resolveResult.getOrNull()
            ?: return Result.failure(IllegalStateException("Unable to resolve favourites playlist identifier"))

        val syncResult = syncFavouritePlaylist(playlistId)
        syncResult.exceptionOrNull()?.let { return Result.failure(it) }

        cachedFavouritePlaylistId = playlistId

        return Result.success(playlistId)
    }

    private suspend fun resolveFavouritePlaylistId(): Result<String> {
        val existingResult = apiClient.findPlaylistByName(FAVOURITE_PLAYLIST_NAME)
        existingResult.exceptionOrNull()?.let { return Result.failure(it) }

        existingResult.getOrNull()?.id?.toString()
            ?.let { normalizePlaylistId(it) }
            ?.let { return Result.success(it) }

        val createResult = apiClient.createPlaylist(FAVOURITE_PLAYLIST_NAME)
        createResult.exceptionOrNull()?.let { return Result.failure(it) }

        val createdId = createResult.getOrNull()?.let { normalizePlaylistId(it) }
        if (createdId != null) {
            return Result.success(createdId)
        }

        val refreshedResult = apiClient.findPlaylistByName(FAVOURITE_PLAYLIST_NAME)
        refreshedResult.exceptionOrNull()?.let { return Result.failure(it) }

        val refreshedId = refreshedResult.getOrNull()?.id?.toString()?.let { normalizePlaylistId(it) }

        return refreshedId?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Playlist creation did not return a valid identifier"))
    }

    private suspend fun syncFavouritePlaylist(playlistId: String): Result<Unit> {
        val favouritesResult = apiClient.fetchFavouriteSongIds()
        favouritesResult.exceptionOrNull()?.let { return Result.failure(it) }
        val favouriteIds = favouritesResult.getOrNull()?.toSet() ?: emptySet()

        val playlistSongsResult = apiClient.fetchPlaylistSongIds(playlistId)
        playlistSongsResult.exceptionOrNull()?.let { return Result.failure(it) }
        val playlistSongIds = playlistSongsResult.getOrNull()?.toSet() ?: emptySet()

        val songsToAdd = favouriteIds - playlistSongIds
        val songsToRemove = playlistSongIds - favouriteIds

        if (songsToAdd.isNotEmpty()) {
            val addResult = apiClient.addSongsToPlaylist(playlistId, songsToAdd.toList())
            addResult.exceptionOrNull()?.let { return Result.failure(it) }
        }

        if (songsToRemove.isNotEmpty()) {
            val removeResult = apiClient.removeSongsFromPlaylist(playlistId, songsToRemove.toList())
            removeResult.exceptionOrNull()?.let { return Result.failure(it) }
        }

        return Result.success(Unit)
    }

    private fun normalizePlaylistId(rawId: String): String? {
        return apiClient.parseUuidOrNull(rawId)?.toString()
    }
}