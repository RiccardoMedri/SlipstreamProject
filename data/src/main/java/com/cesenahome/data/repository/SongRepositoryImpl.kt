package com.cesenahome.data.repository

import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.domain.models.Song
import com.cesenahome.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingData
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.cesenahome.data.paging.SongPagingSource
import com.cesenahome.data.remote.toSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.Result

class SongRepositoryImpl(
    private val jellyfinApiClient: JellyfinApiClient
) : SongRepository {

    override fun pagingSongsAlphabetical(pageSize: Int, albumId: String?): Flow<PagingData<Song>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize * 2,
                prefetchDistance = pageSize,
                enablePlaceholders = false,
                maxSize = pageSize * 5
            ),
            pagingSourceFactory = { SongPagingSource(jellyfinApiClient, pageSize, albumId) }
        ).flow
    }

    override suspend fun getSongsList(page: Int, pageSize: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val startIndex = page * pageSize
            // This method might also need an albumId if you intend to use it for specific albums elsewhere.
            // For now, it remains unchanged as per the current request focusing on paging.
            val dtoList = jellyfinApiClient.fetchSongsAlphabetical(
                startIndex = startIndex,
                limit = pageSize
            )
            val songList = dtoList.map { it.toSong(jellyfinApiClient) }
            Result.success(songList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}