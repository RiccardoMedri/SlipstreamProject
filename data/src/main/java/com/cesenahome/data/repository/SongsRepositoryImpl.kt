package com.cesenahome.data.repository

import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.domain.models.Song
import com.cesenahome.domain.repository.SongsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto

class SongsRepositoryImpl(
    private val jellyfinApiClient: JellyfinApiClient
) : SongsRepository {

    override suspend fun getAllSongsAlphabetical(): List<Song> = withContext(Dispatchers.IO) {
        val pageSize = 200
        val out = ArrayList<Song>(1024)
        var start = 0
        while (true) {
            val page: List<BaseItemDto> = jellyfinApiClient.fetchSongsAlphabetical(startIndex = start, limit = pageSize)
            if (page.isEmpty()) break
            out += page.map { it.toDomain() }
            start += page.size
            if (page.size < pageSize) break // last page
        }
        out
    }

    private fun BaseItemDto.toDomain(): Song {
        val ticks = runTimeTicks // 1 tick = 100ns; 10_000 ticks = 1 ms
        return Song(
            id = id?.toString().orEmpty(),
            title = name.orEmpty(),
            album = album,
            artist = artists?.firstOrNull(),
            durationMs = ticks?.let { it / 10_000L }
        )
    }
}