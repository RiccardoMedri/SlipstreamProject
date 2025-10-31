package com.cesenahome.domain.models.misc

sealed interface DownloadCollectionTarget {
    val id: String

    data class Album(override val id: String) : DownloadCollectionTarget
    data class Playlist(override val id: String) : DownloadCollectionTarget
}