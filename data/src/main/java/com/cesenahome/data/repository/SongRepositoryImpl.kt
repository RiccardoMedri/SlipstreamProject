package com.cesenahome.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.cesenahome.data.paging.SongPagingSource
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.data.remote.toSong
import com.cesenahome.domain.models.song.Song
import com.cesenahome.domain.models.song.SongPagingRequest
import com.cesenahome.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.Result
import kotlin.jvm.Volatile
import kotlin.random.Random

class SongRepositoryImpl(
    private val jellyfinApiClient: JellyfinApiClient
) : SongRepository {

    @Volatile
    private var cachedSongsCount: Int? = null

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
                request = SongPagingRequest()
            )
            val songList = dtoList.map { it.toSong(jellyfinApiClient) }
            Result.success(songList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRandomSong(): Result<Song?> = withContext(Dispatchers.IO) {
        try {
            val total = cachedSongsCount?.takeIf { it > 0 }
                ?: jellyfinApiClient.getSongsCount().also { count -> cachedSongsCount = count }
            if (total == null || total <= 0) {
                return@withContext Result.success(null)
            }
            val randomIndex = Random.nextInt(total)
            val dtoList = jellyfinApiClient.fetchSongs(
                startIndex = randomIndex,
                limit = 1,
                request = SongPagingRequest()
            )
            val song = dtoList.firstOrNull()?.toSong(jellyfinApiClient)
            Result.success(song)
        } catch (e: Exception) {
            cachedSongsCount = null
            Result.failure(e)
        }
    }

    override suspend fun addSongToFavourites(songId: String): Result<Unit> {
        return jellyfinApiClient.addSongToFavourite(songId)
    }
}