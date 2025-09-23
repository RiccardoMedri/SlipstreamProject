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
}