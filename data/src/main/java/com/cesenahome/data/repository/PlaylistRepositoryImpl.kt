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

class PlaylistRepositoryImpl(
    private val apiClient: JellyfinApiClient,
) : PlaylistRepository {

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
}