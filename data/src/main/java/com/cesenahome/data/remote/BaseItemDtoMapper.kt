package com.cesenahome.data.remote

import com.cesenahome.data.remote.media.JellyfinMediaClient
import com.cesenahome.domain.models.album.Album
import com.cesenahome.domain.models.artist.Artist
import com.cesenahome.domain.models.playlist.Playlist
import com.cesenahome.domain.models.song.Song
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

fun BaseItemDto.toSong(mediaClient: JellyfinMediaClient): Song {
    val ticks = this.runTimeTicks
    val idStr = this.id?.toString().orEmpty()
    val primaryTag = this.imageTags?.get(ImageType.PRIMARY) ?: this.albumPrimaryImageTag

    return Song(
        id = idStr,
        title = this.name.orEmpty(),
        artist = this.artists?.firstOrNull(),
        artistId = this.albumArtists?.firstOrNull()?.id?.toString(),
        album = this.album,
        albumId = this.albumId?.toString(),
        durationMs = ticks?.let { it / 10_000L },
        artworkUrl = mediaClient.resolveImageUrl(itemId = idStr, imageTag = primaryTag, maxSize = 256).getOrNull(),
        isFavorite = this.userData?.isFavorite == true,
        isDownloaded = false,
    )
}

fun BaseItemDto.toAlbum(mediaClient: JellyfinMediaClient): Album {
    val idStr = this.id?.toString().orEmpty()
    val primaryTag = this.imageTags?.get(ImageType.PRIMARY)
    val albumArtistName = this.albumArtist ?: this.artists?.firstOrNull()

    return Album(
        id = idStr,
        title = this.name.orEmpty(),
        artist = albumArtistName,
        artworkUrl = mediaClient.resolveImageUrl(itemId = idStr, imageTag = primaryTag, maxSize = 1080).getOrNull(),
        songs = emptyList(),
        isDownloaded = false,
    )
}

fun BaseItemDto.toArtist(mediaClient: JellyfinMediaClient): Artist {
    val idStr = this.id?.toString().orEmpty()
    val primaryTag = this.imageTags?.get(ImageType.PRIMARY)

    return Artist(
        id = idStr,
        name = this.name.orEmpty(),
        artworkUrl = mediaClient.resolveImageUrl(itemId = idStr, imageTag = primaryTag, maxSize = 1080).getOrNull(),
        albums = emptyList()
    )
}

fun BaseItemDto.toPlaylist(mediaClient: JellyfinMediaClient): Playlist {
    val idStr = this.id?.toString().orEmpty()
    val primaryTag = this.imageTags?.get(ImageType.PRIMARY)
    val ticks = this.runTimeTicks

    return Playlist(
        id = idStr,
        name = this.name.orEmpty(),
        songCount = this.songCount ?: this.childCount,
        durationMs = ticks?.let { it / 10_000L },
        artworkUrl = mediaClient.resolveImageUrl(itemId = idStr, imageTag = primaryTag, maxSize = 512).getOrNull(),
        isDownloaded = false,
    )
}
