package com.cesenahome.domain.usecases

import com.cesenahome.domain.repository.DownloadRepository

fun interface RemovePlaylistDownloadUseCase {
    suspend operator fun invoke(playlistId: String): Result<Unit>
}

class RemovePlaylistDownloadUseCaseImpl(
    private val repository: DownloadRepository,
) : RemovePlaylistDownloadUseCase {
    override suspend fun invoke(playlistId: String): Result<Unit> = repository.removePlaylistDownload(playlistId)
}