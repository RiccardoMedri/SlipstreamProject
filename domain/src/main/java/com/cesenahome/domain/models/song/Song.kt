package com.cesenahome.domain.models.song

data class Song(
    val id: String,
    val title: String,
    val artist: String?,
    val artistId: String? = null,
    val album: String?,
    val albumId: String? = null,
    val durationMs: Long?,
    val artworkUrl: String?,
    val isFavorite: Boolean,
    val isDownloaded: Boolean = false,
)