package com.cesenahome.domain.repository

interface PlayerRepository {
    suspend fun resolveStreamUrl(mediaId: String): String?
}