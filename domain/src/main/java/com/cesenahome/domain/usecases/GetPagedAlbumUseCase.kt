package com.cesenahome.domain.usecases

import androidx.paging.PagingData
import com.cesenahome.domain.models.Album
import com.cesenahome.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.Flow

fun interface GetPagedAlbumUseCase {
    operator fun invoke(artistId: String?): Flow<PagingData<Album>>
}

class GetPagedAlbumUseCaseImpl(
    private val repository: AlbumRepository
): GetPagedAlbumUseCase {
    override fun invoke(artistId: String?): Flow<PagingData<Album>> = repository.pagingAlbumsAlphabetical(artistId = artistId)
}