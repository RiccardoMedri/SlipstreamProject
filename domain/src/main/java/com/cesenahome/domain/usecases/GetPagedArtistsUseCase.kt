package com.cesenahome.domain.usecases

import androidx.paging.PagingData
import com.cesenahome.domain.models.Artist
import com.cesenahome.domain.models.ArtistPagingRequest
import com.cesenahome.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.Flow

fun interface GetPagedArtistsUseCase {
    operator fun invoke(request: ArtistPagingRequest): Flow<PagingData<Artist>>
}

class GetPagedArtistsUseCaseImpl(
    private val repository: ArtistRepository
): GetPagedArtistsUseCase {
    override fun invoke(request: ArtistPagingRequest): Flow<PagingData<Artist>> =
        repository.pagingArtists(request = request)
}