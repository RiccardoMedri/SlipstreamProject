package com.cesenahome.domain.models

data class Album(
    val id: String,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
    val songs: List<Song>
)
