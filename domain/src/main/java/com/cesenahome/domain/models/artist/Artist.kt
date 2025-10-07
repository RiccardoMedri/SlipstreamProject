package com.cesenahome.domain.models.artist

import com.cesenahome.domain.models.album.Album

data class Artist(
    val id: String,
    val name: String,
    val artworkUrl: String?,
    val albums: List<Album>
)
