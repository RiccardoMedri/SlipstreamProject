package com.cesenahome.domain.usecases.playback

import com.cesenahome.domain.repository.PlayerRepository

interface ResolveStreamUrlUseCase {
    suspend operator fun invoke(mediaId: String): String?
}

class ResolveStreamUrlUseCaseImpl(
    private val playerRepository: PlayerRepository
) : ResolveStreamUrlUseCase {
    override suspend operator fun invoke(mediaId: String): String? = playerRepository.resolveStreamUrl(mediaId)
}