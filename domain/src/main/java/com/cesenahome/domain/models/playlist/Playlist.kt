package com.cesenahome.domain.models.playlist

data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int?,
    val durationMs: Long?,
    val artworkUrl: String?,
    val isDownloaded: Boolean = false,
)