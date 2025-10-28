package com.cesenahome.domain.models.song

import com.cesenahome.domain.models.SortDirection

enum class SongSortField {
    NAME,
    ALBUM_ARTIST,
    DATE_ADDED
}

data class SongSortOption(
    val field: SongSortField = SongSortField.NAME,
    val direction: SortDirection = SortDirection.ASCENDING
)

data class SongPagingRequest(
    val albumId: String? = null,
    val playlistId: String? = null,
    val sortOption: SongSortOption = SongSortOption(),
    val searchQuery: String? = null
)