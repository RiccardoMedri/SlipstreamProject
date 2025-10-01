package com.cesenahome.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.cesenahome.data.paging.SongPagingSource
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.data.remote.toSong
import com.cesenahome.domain.models.Song
import com.cesenahome.domain.models.SongPagingRequest
import com.cesenahome.domain.models.SongSortField
import com.cesenahome.domain.models.SortDirection
import com.cesenahome.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.Result

class SongRepositoryImpl(
    private val jellyfinApiClient: JellyfinApiClient
) : SongRepository {

    override fun pagingSongs(pageSize: Int, request: SongPagingRequest): Flow<PagingData<Song>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize * 2,
                prefetchDistance = pageSize,
                enablePlaceholders = false,
                maxSize = pageSize * 5
            ),
            pagingSourceFactory = { SongPagingSource( api = jellyfinApiClient, pageSize = pageSize, request = request) }
        ).flow
    }

    override suspend fun getSongsList(page: Int, pageSize: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val startIndex = page * pageSize
            val dtoList = jellyfinApiClient.fetchSongs(
                startIndex = startIndex,
                limit = pageSize,
                sortField = SongSortField.NAME,
                sortDirection = SortDirection.ASCENDING,
                albumId = null
            )
            val songList = dtoList.map { it.toSong(jellyfinApiClient) }
            Result.success(songList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}