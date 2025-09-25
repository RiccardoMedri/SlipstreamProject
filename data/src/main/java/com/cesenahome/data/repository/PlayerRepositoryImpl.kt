package com.cesenahome.data.repository

import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.domain.repository.PlayerRepository

class PlayerRepositoryImpl (
    private val jellyfinApiClient: JellyfinApiClient
) : PlayerRepository {
    override suspend fun resolveStreamUrl(mediaId: String): String? =
        jellyfinApiClient.buildAudioStreamUrl(mediaId)
}