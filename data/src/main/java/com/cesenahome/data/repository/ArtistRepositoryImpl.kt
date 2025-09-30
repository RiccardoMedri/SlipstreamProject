package com.cesenahome.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.cesenahome.data.paging.ArtistPagingSource // Corrected import
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.domain.models.Artist
import com.cesenahome.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.Flow

class ArtistRepositoryImpl(
    private val apiClient: JellyfinApiClient
): ArtistRepository {
    override fun pagingArtistsAlphabetical(pageSize: Int): Flow<PagingData<Artist>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize * 2,
                prefetchDistance = pageSize,
                enablePlaceholders = false,
                maxSize = pageSize * 5
            ),
            pagingSourceFactory = { ArtistPagingSource(apiClient, pageSize) }
        ).flow
    }
}
