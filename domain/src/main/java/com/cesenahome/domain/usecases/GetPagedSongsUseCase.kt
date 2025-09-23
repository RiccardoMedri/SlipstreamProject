package com.cesenahome.domain.usecases

import androidx.paging.PagingData
import com.cesenahome.domain.models.Song
import com.cesenahome.domain.repository.SongsRepository
import kotlinx.coroutines.flow.Flow

fun interface GetPagedSongsUseCase {
    operator fun invoke(): Flow<PagingData<Song>>
}

class GetPagedSongsUseCaseImpl(
    private val repo: SongsRepository
) : GetPagedSongsUseCase {
    override fun invoke(): Flow<PagingData<Song>> = repo.pagingSongsAlphabetical()
}