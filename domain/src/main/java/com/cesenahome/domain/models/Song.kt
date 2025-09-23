package com.cesenahome.domain.models

data class Song(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val artworkUrl: String?
)
