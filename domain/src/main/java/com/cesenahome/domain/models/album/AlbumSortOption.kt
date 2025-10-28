package com.cesenahome.domain.models.album

import com.cesenahome.domain.models.SortDirection

enum class AlbumSortField {
    TITLE,
    ARTIST,
    YEAR,
    DATE_ADDED
}

data class AlbumSortOption(
    val field: AlbumSortField = AlbumSortField.TITLE,
    val direction: SortDirection = SortDirection.ASCENDING
)

data class AlbumPagingRequest(
    val artistId: String? = null,
    val sortOption: AlbumSortOption = AlbumSortOption(),
    val searchQuery: String? = null,
)