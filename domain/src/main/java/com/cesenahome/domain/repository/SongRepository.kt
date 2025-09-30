package com.cesenahome.domain.repository

import androidx.paging.PagingData
import com.cesenahome.domain.models.Song
import kotlinx.coroutines.flow.Flow
import kotlin.Result // Added for the new method

interface SongRepository {

    fun pagingSongsAlphabetical(pageSize: Int = 200, albumId: String? = null): Flow<PagingData<Song>>
    suspend fun getSongsList(page: Int, pageSize: Int): Result<List<Song>>
}