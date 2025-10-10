package com.cesenahome.domain.models.song

enum class SongSortField {
    NAME,
    ALBUM_ARTIST,
    DATE_ADDED
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
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