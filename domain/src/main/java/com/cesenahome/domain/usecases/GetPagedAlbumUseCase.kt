package com.cesenahome.domain.usecases

import androidx.paging.PagingData
import com.cesenahome.domain.models.Album
import com.cesenahome.domain.models.AlbumPagingRequest
import com.cesenahome.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.Flow

fun interface GetPagedAlbumUseCase {
    operator fun invoke(request: AlbumPagingRequest): Flow<PagingData<Album>>
}

class GetPagedAlbumUseCaseImpl(
    private val repository: AlbumRepository
): GetPagedAlbumUseCase {
    override fun invoke(request: AlbumPagingRequest): Flow<PagingData<Album>> =
        repository.pagingAlbums(request = request)
}