package com.cesenahome.data.remote.util

import com.cesenahome.domain.models.album.AlbumSortField
import com.cesenahome.domain.models.artist.ArtistSortField
import com.cesenahome.domain.models.misc.SortDirection
import com.cesenahome.domain.models.playlist.PlaylistSortField
import com.cesenahome.domain.models.song.SongSortField
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

fun AlbumSortField.toApiSortBy(): ItemSortBy = when (this) {
    AlbumSortField.TITLE -> ItemSortBy.NAME
    AlbumSortField.ARTIST -> ItemSortBy.ALBUM_ARTIST
    AlbumSortField.YEAR -> ItemSortBy.PRODUCTION_YEAR
    AlbumSortField.DATE_ADDED -> ItemSortBy.DATE_CREATED
}

fun ArtistSortField.toApiSortBy(): ItemSortBy = when (this) {
    ArtistSortField.NAME -> ItemSortBy.NAME
    ArtistSortField.DATE_ADDED -> ItemSortBy.DATE_CREATED
}

fun PlaylistSortField.toApiSortBy(): ItemSortBy = when (this) {
    PlaylistSortField.NAME -> ItemSortBy.NAME
    PlaylistSortField.DATE_ADDED -> ItemSortBy.DATE_CREATED
}

fun SongSortField.toApiSortBy(): ItemSortBy = when (this) {
    SongSortField.NAME -> ItemSortBy.NAME
    SongSortField.ALBUM_ARTIST -> ItemSortBy.ALBUM_ARTIST
    SongSortField.DATE_ADDED -> ItemSortBy.DATE_CREATED
}

fun SortDirection.toApiSortOrder(): SortOrder = when (this) {
    SortDirection.ASCENDING -> SortOrder.ASCENDING
    SortDirection.DESCENDING -> SortOrder.DESCENDING
}

fun buildSongSortBy(sortField: SongSortField, hasParent: Boolean): List<ItemSortBy> = when {
    hasParent && sortField == SongSortField.NAME -> listOf(ItemSortBy.INDEX_NUMBER)
    else -> listOf(sortField.toApiSortBy())
}