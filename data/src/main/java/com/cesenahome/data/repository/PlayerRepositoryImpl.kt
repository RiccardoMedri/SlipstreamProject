package com.cesenahome.data.repository

import com.cesenahome.data.remote.media.JellyfinMediaClient
import com.cesenahome.domain.repository.PlayerRepository

class PlayerRepositoryImpl (
    private val mediaClient: JellyfinMediaClient
) : PlayerRepository {
    override suspend fun resolveStreamUrl(mediaId: String): String? =
        mediaClient.resolveAudioUrl(mediaId).getOrNull()
}
