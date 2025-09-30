package com.cesenahome.domain.models

data class Artist(
    val id: String,
    val name: String,
    val artworkUrl: String?,
    val albums: List<Album>
)
