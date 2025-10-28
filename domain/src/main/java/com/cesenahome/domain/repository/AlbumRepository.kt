package com.cesenahome.domain.repository

import androidx.paging.PagingData
import com.cesenahome.domain.models.album.Album
import com.cesenahome.domain.models.album.AlbumPagingRequest
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {

    //Fetches and handle the pagination of albums from the API
    fun pagingAlbums(pageSize: Int = 20, request: AlbumPagingRequest = AlbumPagingRequest()): Flow<PagingData<Album>>
}