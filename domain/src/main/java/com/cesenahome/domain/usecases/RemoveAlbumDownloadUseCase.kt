package com.cesenahome.domain.usecases

import com.cesenahome.domain.repository.DownloadRepository

fun interface RemoveAlbumDownloadUseCase {
    suspend operator fun invoke(albumId: String): Result<Unit>
}

class RemoveAlbumDownloadUseCaseImpl(
    private val repository: DownloadRepository,
) : RemoveAlbumDownloadUseCase {
    override suspend fun invoke(albumId: String): Result<Unit> = repository.removeAlbumDownload(albumId)
}