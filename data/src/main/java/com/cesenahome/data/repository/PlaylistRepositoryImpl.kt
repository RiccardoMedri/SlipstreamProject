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

private const val FAVOURITE_PLAYLIST_NAME = "Favourites Songs"

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

        val existingResult = apiClient.findPlaylistByName(FAVOURITE_PLAYLIST_NAME)
        existingResult.exceptionOrNull()?.let { return Result.failure(it) }
        val existing = existingResult.getOrNull()

        val playlistId = existing?.id?.toString() ?: run {
            val createResult = apiClient.createPlaylist(FAVOURITE_PLAYLIST_NAME)
            createResult.exceptionOrNull()?.let { return Result.failure(it) }
            createResult.getOrNull() ?: return Result.failure(IllegalStateException("Playlist creation did not return an identifier"))
        }

        cachedFavouritePlaylistId = playlistId

        return Result.success(playlistId)
    }
}