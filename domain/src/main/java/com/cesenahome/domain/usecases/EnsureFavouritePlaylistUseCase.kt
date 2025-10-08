package com.cesenahome.domain.usecases

import com.cesenahome.domain.repository.PlaylistRepository

interface EnsureFavouritePlaylistUseCase {
    suspend operator fun invoke(): Result<String>
}

class EnsureFavouritePlaylistUseCaseImpl(
    private val playlistRepository: PlaylistRepository,
) : EnsureFavouritePlaylistUseCase {
    override suspend fun invoke(): Result<String> = playlistRepository.ensureFavouritePlaylistId()
}