package com.cesenahome.domain.repository

import androidx.paging.PagingData
import com.cesenahome.domain.models.Song
import kotlinx.coroutines.flow.Flow

interface SongsRepository {

    fun pagingSongsAlphabetical(pageSize: Int = 200): Flow<PagingData<Song>>
}