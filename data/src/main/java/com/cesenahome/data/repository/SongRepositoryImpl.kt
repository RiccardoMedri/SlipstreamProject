package com.cesenahome.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.cesenahome.data.paging.SongPagingSource
import com.cesenahome.data.remote.media.JellyfinMediaClient
import com.cesenahome.data.remote.playlist.JellyfinPlaylistClient
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
    private val mediaClient: JellyfinMediaClient,
    private val playlistClient: JellyfinPlaylistClient,
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
            pagingSourceFactory = {
                SongPagingSource(
                    mediaClient = mediaClient,
                    pageSize = pageSize,
                    request = request,
                )
            }
        ).flow
    }

    override suspend fun getSongsList(page: Int, pageSize: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val startIndex = page * pageSize
            val dtoList = mediaClient.fetchSongs(
                startIndex = startIndex,
                limit = pageSize,
                request = SongPagingRequest()
            ).getOrThrow()
            val songList = dtoList.map { it.toSong(mediaClient) }
            Result.success(songList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRandomSong(): Result<Song?> = withContext(Dispatchers.IO) {
        try {
            val total = cachedSongsCount?.takeIf { it > 0 }
                ?: mediaClient.getSongsCount().getOrThrow().also { count -> cachedSongsCount = count }
            if (total == null || total <= 0) {
                return@withContext Result.success(null)
            }
            val randomIndex = Random.nextInt(total)
            val dtoList = mediaClient.fetchSongs(
                startIndex = randomIndex,
                limit = 1,
                request = SongPagingRequest()
            ).getOrThrow()
            val song = dtoList.firstOrNull()?.toSong(mediaClient)
            Result.success(song)
        } catch (e: Exception) {
            cachedSongsCount = null
            Result.failure(e)
        }
    }

    //Adds songs to favourite and automatically append it to the F.S. playlist created at homepage launch
    override suspend fun addSongToFavourites(songId: String, isFavourite: Boolean, playlistId: String): Result<Unit> {
        val markResult = playlistClient.setAsFavourite(songId, isFavourite)
        markResult.exceptionOrNull()?.let { return Result.failure(it) }
        val addResult = playlistClient.addSongsToPlaylist(playlistId, listOf(songId))
        addResult.exceptionOrNull()?.let { return Result.failure(it) }

        return Result.success(Unit)
    }

    override suspend fun removeSongFromFavourites(songId: String, isFavourite: Boolean, playlistId: String): Result<Unit> {
        val markResult = playlistClient.setAsFavourite(songId, isFavourite)
        markResult.exceptionOrNull()?.let { return Result.failure(it) }

        val removeResult = playlistClient.removeSongsFromPlaylist(playlistId, listOf(songId))
        removeResult.exceptionOrNull()?.let { return Result.failure(it) }

        return Result.success(Unit)
    }
}
