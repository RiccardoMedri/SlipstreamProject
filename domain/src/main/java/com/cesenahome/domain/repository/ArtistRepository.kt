package com.cesenahome.domain.repository

import androidx.paging.PagingData
import com.cesenahome.domain.models.Artist
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {
    fun pagingArtistsAlphabetical(pageSize: Int = 20): Flow<PagingData<Artist>>
}