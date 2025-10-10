package com.cesenahome.domain.usecases

import com.cesenahome.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow

fun interface ObserveDownloadedPlaylistIdsUseCase {
    operator fun invoke(): Flow<Set<String>>
}

class ObserveDownloadedPlaylistIdsUseCaseImpl(
    private val repository: DownloadRepository,
) : ObserveDownloadedPlaylistIdsUseCase {
    override fun invoke(): Flow<Set<String>> = repository.downloadedPlaylistIds
}