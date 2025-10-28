package com.cesenahome.domain.models.homepage

enum class HomeDestination {
    ARTISTS, ALBUMS, PLAYLISTS, SONGS
}

data class HomeMenuItem(
    val id: String,
    val title: String,
    val destination: HomeDestination,
    val count: Int? = null
)