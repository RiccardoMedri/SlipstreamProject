package com.cesenahome.domain.models.album

import com.cesenahome.domain.models.song.Song

data class Album(
    val id: String,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
    val songs: List<Song>,
    val isDownloaded: Boolean = false,
)
