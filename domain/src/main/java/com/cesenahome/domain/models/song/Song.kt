package com.cesenahome.domain.models.song

data class Song(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val artworkUrl: String?
)
