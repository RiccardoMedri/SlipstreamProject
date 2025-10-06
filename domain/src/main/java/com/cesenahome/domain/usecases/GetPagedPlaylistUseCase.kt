package com.cesenahome.domain.usecases

import androidx.paging.PagingData
import com.cesenahome.domain.models.Playlist
import com.cesenahome.domain.models.PlaylistPagingRequest
import com.cesenahome.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow

fun interface GetPagedPlaylistsUseCase {
    operator fun invoke(request: PlaylistPagingRequest): Flow<PagingData<Playlist>>
}

class GetPagedPlaylistsUseCaseImpl(
    private val repository: PlaylistRepository,
) : GetPagedPlaylistsUseCase {
    override fun invoke(request: PlaylistPagingRequest): Flow<PagingData<Playlist>> =
        repository.pagingPlaylists(request = request)
}