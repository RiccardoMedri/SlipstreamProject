package com.cesenahome.domain.repository

import androidx.paging.PagingData
import com.cesenahome.domain.models.Song
import com.cesenahome.domain.models.SongPagingRequest
import kotlinx.coroutines.flow.Flow
import kotlin.Result

interface SongRepository {

    fun pagingSongs(pageSize: Int = 200, request: SongPagingRequest): Flow<PagingData<Song>>
    suspend fun getSongsList(page: Int, pageSize: Int): Result<List<Song>>
    suspend fun getRandomSong(): Result<Song?>
}