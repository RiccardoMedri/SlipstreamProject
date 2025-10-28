package com.cesenahome.domain.usecases.download

import com.cesenahome.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow

fun interface ObserveDownloadedAlbumIdsUseCase {
    operator fun invoke(): Flow<Set<String>>
}

class ObserveDownloadedAlbumIdsUseCaseImpl(
    private val repository: DownloadRepository,
) : ObserveDownloadedAlbumIdsUseCase {
    override fun invoke(): Flow<Set<String>> = repository.downloadedAlbumIds
}