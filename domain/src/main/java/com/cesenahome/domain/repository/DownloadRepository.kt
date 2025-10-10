package com.cesenahome.domain.repository

import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    val downloadedSongIds: Flow<Set<String>>
    val downloadedAlbumIds: Flow<Set<String>>
    val downloadedPlaylistIds: Flow<Set<String>>

    suspend fun downloadAlbum(albumId: String): Result<Unit>
    suspend fun removeAlbumDownload(albumId: String): Result<Unit>

    suspend fun downloadPlaylist(playlistId: String): Result<Unit>
    suspend fun removePlaylistDownload(playlistId: String): Result<Unit>
}