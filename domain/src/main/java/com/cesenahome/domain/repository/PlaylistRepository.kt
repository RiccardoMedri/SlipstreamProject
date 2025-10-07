package com.cesenahome.domain.repository

import androidx.paging.PagingData
import com.cesenahome.domain.models.playlist.Playlist
import com.cesenahome.domain.models.playlist.PlaylistPagingRequest
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun pagingPlaylists(pageSize: Int = 200, request: PlaylistPagingRequest): Flow<PagingData<Playlist>>
}