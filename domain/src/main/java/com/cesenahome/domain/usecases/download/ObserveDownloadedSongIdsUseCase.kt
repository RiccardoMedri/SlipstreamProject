package com.cesenahome.domain.usecases.download

import com.cesenahome.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow

fun interface ObserveDownloadedSongIdsUseCase {
    operator fun invoke(): Flow<Set<String>>
}

class ObserveDownloadedSongIdsUseCaseImpl(
    private val repository: DownloadRepository,
) : ObserveDownloadedSongIdsUseCase {
    override fun invoke(): Flow<Set<String>> = repository.downloadedSongIds
}