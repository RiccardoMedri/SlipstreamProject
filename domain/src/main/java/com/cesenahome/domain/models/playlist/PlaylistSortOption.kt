package com.cesenahome.domain.models.playlist

import com.cesenahome.domain.models.SortDirection

enum class PlaylistSortField {
    NAME,
    DATE_ADDED,
}

data class PlaylistSortOption(
    val field: PlaylistSortField = PlaylistSortField.NAME,
    val direction: SortDirection = SortDirection.ASCENDING,
)

data class PlaylistPagingRequest(
    val sortOption: PlaylistSortOption = PlaylistSortOption(),
    val searchQuery: String? = null,
)