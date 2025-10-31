package com.cesenahome.domain.usecases

import com.cesenahome.domain.models.misc.DownloadCollectionTarget
import com.cesenahome.domain.repository.DownloadRepository

fun interface ToggleCollectionDownloadUseCase {
    suspend operator fun invoke(target: DownloadCollectionTarget, shouldDownload: Boolean): Result<Unit>
}

class ToggleCollectionDownloadUseCaseImpl(
    private val repository: DownloadRepository,
) : ToggleCollectionDownloadUseCase {

    override suspend fun invoke(target: DownloadCollectionTarget, shouldDownload: Boolean): Result<Unit> {
        return when (target) {
            is DownloadCollectionTarget.Album -> {
                if (shouldDownload) {
                    repository.downloadAlbum(target.id)
                } else {
                    repository.removeAlbumDownload(target.id)
                }
            }

            is DownloadCollectionTarget.Playlist -> {
                if (shouldDownload) {
                    repository.downloadPlaylist(target.id)
                } else {
                    repository.removePlaylistDownload(target.id)
                }
            }
        }
    }
}