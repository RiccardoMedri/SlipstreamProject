package com.cesenahome.domain.models

data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int?,
    val durationMs: Long?,
    val artworkUrl: String?,
)