package com.cesenahome.domain.repository

import androidx.paging.PagingData
import com.cesenahome.domain.models.artist.Artist
import com.cesenahome.domain.models.artist.ArtistPagingRequest
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    fun pagingArtists(pageSize: Int = 20, request: ArtistPagingRequest = ArtistPagingRequest()): Flow<PagingData<Artist>>
}