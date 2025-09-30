package com.cesenahome.domain.repository

import androidx.paging.PagingData
import com.cesenahome.domain.models.Album
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun pagingAlbumsAlphabetical(pageSize: Int = 20, artistId: String? = null): Flow<PagingData<Album>>
}