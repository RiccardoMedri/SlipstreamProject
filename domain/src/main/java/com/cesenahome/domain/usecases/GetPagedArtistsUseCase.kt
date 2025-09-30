package com.cesenahome.domain.usecases

import androidx.paging.PagingData
import com.cesenahome.domain.models.Artist
import com.cesenahome.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.Flow

fun interface GetPagedArtistsUseCase {
    operator fun invoke(): Flow<PagingData<Artist>>
}

class GetPagedArtistsUseCaseImpl(
    private val repository: ArtistRepository
): GetPagedArtistsUseCase {
    override fun invoke(): Flow<PagingData<Artist>> = repository.pagingArtistsAlphabetical()
}