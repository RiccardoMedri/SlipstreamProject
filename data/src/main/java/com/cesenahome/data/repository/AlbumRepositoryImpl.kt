package com.cesenahome.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.cesenahome.data.paging.AlbumPagingSource
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.domain.models.album.Album
import com.cesenahome.domain.models.album.AlbumPagingRequest
import com.cesenahome.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.Flow

class AlbumRepositoryImpl(
    private val apiClient: JellyfinApiClient
) : AlbumRepository {
    override fun pagingAlbums(pageSize: Int, request: AlbumPagingRequest): Flow<PagingData<Album>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize * 2,
                prefetchDistance = pageSize,
                enablePlaceholders = false,
                maxSize = pageSize * 5
            ),
            pagingSourceFactory = { AlbumPagingSource(apiClient, pageSize, request) }
        ).flow
    }
}