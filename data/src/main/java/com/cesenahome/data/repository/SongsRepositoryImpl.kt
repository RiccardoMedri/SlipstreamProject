package com.cesenahome.data.repository

import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.domain.models.Song
import com.cesenahome.domain.repository.SongsRepository
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.BaseItemDto
import androidx.paging.PagingData
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.cesenahome.data.paging.SongsPagingSource

class SongsRepositoryImpl(
    private val jellyfinApiClient: JellyfinApiClient
) : SongsRepository {

    override fun pagingSongsAlphabetical(pageSize: Int): Flow<PagingData<Song>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize * 2,
                prefetchDistance = pageSize, // prefetch next page
                enablePlaceholders = false,
                maxSize = pageSize * 5
            ),
            pagingSourceFactory = { SongsPagingSource(jellyfinApiClient, pageSize) }
        ).flow
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