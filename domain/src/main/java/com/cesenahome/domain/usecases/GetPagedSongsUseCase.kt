package com.cesenahome.domain.usecases

import androidx.paging.PagingData
import com.cesenahome.domain.models.Song
import com.cesenahome.domain.models.SongPagingRequest
import com.cesenahome.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow

fun interface GetPagedSongsUseCase {
    operator fun invoke(request: SongPagingRequest): Flow<PagingData<Song>>
}

class GetPagedSongsUseCaseImpl(
    private val repo: SongRepository
) : GetPagedSongsUseCase {
    override fun invoke(request: SongPagingRequest): Flow<PagingData<Song>> = repo.pagingSongs(request = request)
}