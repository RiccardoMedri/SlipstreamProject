package com.cesenahome.domain.models.artist

import com.cesenahome.domain.models.SortDirection

enum class ArtistSortField {
    NAME,
    DATE_ADDED
}

data class ArtistSortOption(
    val field: ArtistSortField = ArtistSortField.NAME,
    val direction: SortDirection = SortDirection.ASCENDING
)

data class ArtistPagingRequest(
    val sortOption: ArtistSortOption = ArtistSortOption(),
    val searchQuery: String? = null,
)