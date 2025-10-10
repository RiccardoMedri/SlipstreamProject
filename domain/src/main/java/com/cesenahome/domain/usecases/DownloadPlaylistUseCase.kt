package com.cesenahome.domain.usecases

import com.cesenahome.domain.repository.DownloadRepository

fun interface DownloadPlaylistUseCase {
    suspend operator fun invoke(playlistId: String): Result<Unit>
}

class DownloadPlaylistUseCaseImpl(
    private val repository: DownloadRepository,
) : DownloadPlaylistUseCase {
    override suspend fun invoke(playlistId: String): Result<Unit> = repository.downloadPlaylist(playlistId)
}