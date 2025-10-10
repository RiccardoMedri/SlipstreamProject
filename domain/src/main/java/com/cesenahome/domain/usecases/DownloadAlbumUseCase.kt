package com.cesenahome.domain.usecases

import com.cesenahome.domain.repository.DownloadRepository

fun interface DownloadAlbumUseCase {
    suspend operator fun invoke(albumId: String): Result<Unit>
}

class DownloadAlbumUseCaseImpl(
    private val repository: DownloadRepository,
) : DownloadAlbumUseCase {
    override suspend fun invoke(albumId: String): Result<Unit> = repository.downloadAlbum(albumId)
}