package com.cesenahome.domain.repository

import androidx.paging.PagingData
import com.cesenahome.domain.models.song.Song
import com.cesenahome.domain.models.song.SongPagingRequest
import kotlinx.coroutines.flow.Flow
import kotlin.Result

interface SongRepository {

    //Fetches and handle the pagination of songs from the API
    fun pagingSongs(pageSize: Int = 200, request: SongPagingRequest): Flow<PagingData<Song>>

    suspend fun getSongsList(page: Int, pageSize: Int): Result<List<Song>>

    suspend fun getRandomSong(): Result<Song?>

    suspend fun addSongToFavourites(songId: String, isFavourite: Boolean, playlistId: String): Result<Unit>

    suspend fun removeSongFromFavourites(songId: String, isFavourite: Boolean, playlistId: String): Result<Unit>
}