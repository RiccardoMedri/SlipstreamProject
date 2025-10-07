package com.cesenahome.domain.repository

import androidx.paging.PagingData
import com.cesenahome.domain.models.album.Album
import com.cesenahome.domain.models.album.AlbumPagingRequest
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun pagingAlbums(pageSize: Int = 20, request: AlbumPagingRequest = AlbumPagingRequest()): Flow<PagingData<Album>>
}