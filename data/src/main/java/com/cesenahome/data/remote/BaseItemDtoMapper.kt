package com.cesenahome.data.remote

import com.cesenahome.domain.models.album.Album
import com.cesenahome.domain.models.artist.Artist
import com.cesenahome.domain.models.playlist.Playlist
import com.cesenahome.domain.models.song.Song
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

fun BaseItemDto.toSong(apiClient: JellyfinApiClient): Song {
    val ticks = this.runTimeTicks
    val idStr = this.id?.toString().orEmpty()
    val primaryTag = this.imageTags?.get(ImageType.PRIMARY) ?: this.albumPrimaryImageTag

    return Song(
        id = idStr,
        title = this.name.orEmpty(),
        album = this.album,
        artist = this.artists?.firstOrNull(),
        durationMs = ticks?.let { it / 10_000L },
        artworkUrl = apiClient.getImage(itemId = idStr, imageTag = primaryTag, maxSize = 256),
        isFavorite = this.userData?.isFavorite == true
    )
}

fun BaseItemDto.toAlbum(apiClient: JellyfinApiClient): Album {
    val idStr = this.id?.toString().orEmpty()
    val primaryTag = this.imageTags?.get(ImageType.PRIMARY)
    val albumArtistName = this.albumArtist ?: this.artists?.firstOrNull()

    return Album(
        id = idStr,
        title = this.name.orEmpty(),
        artist = albumArtistName,
        artworkUrl = apiClient.getImage(itemId = idStr, imageTag = primaryTag, maxSize = 1080),
        songs = emptyList()
    )
}

fun BaseItemDto.toArtist(apiClient: JellyfinApiClient): Artist {
    val idStr = this.id?.toString().orEmpty()
    val primaryTag = this.imageTags?.get(ImageType.PRIMARY)

    return Artist(
        id = idStr,
        name = this.name.orEmpty(),
        artworkUrl = apiClient.getImage(itemId = idStr, imageTag = primaryTag, maxSize = 1080),
        albums = emptyList()
    )
}

fun BaseItemDto.toPlaylist(apiClient: JellyfinApiClient): Playlist {
    val idStr = this.id?.toString().orEmpty()
    val primaryTag = this.imageTags?.get(ImageType.PRIMARY)
    val ticks = this.runTimeTicks

    return Playlist(
        id = idStr,
        name = this.name.orEmpty(),
        songCount = this.songCount ?: this.childCount,
        durationMs = ticks?.let { it / 10_000L },
        artworkUrl = apiClient.getImage(itemId = idStr, imageTag = primaryTag, maxSize = 512),
    )
}
