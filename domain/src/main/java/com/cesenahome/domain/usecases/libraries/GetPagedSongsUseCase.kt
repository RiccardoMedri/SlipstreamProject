package com.cesenahome.domain.usecases.libraries

import androidx.paging.PagingData
import com.cesenahome.domain.models.song.Song
import com.cesenahome.domain.models.song.SongPagingRequest
import com.cesenahome.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow

fun interface GetPagedSongsUseCase {
    operator fun invoke(request: SongPagingRequest): Flow<PagingData<Song>>
}

class GetPagedSongsUseCaseImpl(
    private val repo: SongRepository
) : GetPagedSongsUseCase {
    override fun invoke(request: SongPagingRequest): Flow<PagingData<Song>> =
        repo.pagingSongs(request = request)
}