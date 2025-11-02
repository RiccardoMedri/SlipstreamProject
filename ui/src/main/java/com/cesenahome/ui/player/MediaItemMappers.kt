package com.cesenahome.ui.player

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.cesenahome.domain.models.song.QueueSong
import com.cesenahome.domain.models.song.Song

const val MEDIA_METADATA_KEY_ALBUM_ID = "media_metadata_album_id"
const val MEDIA_METADATA_KEY_ARTIST_ID = "media_metadata_artist_id"

private const val MEDIA_METADATA_KEY_TITLE = "media_metadata_title"
private const val MEDIA_METADATA_KEY_ARTIST = "media_metadata_artist"
private const val MEDIA_METADATA_KEY_ALBUM = "media_metadata_album"
private const val MEDIA_METADATA_KEY_DURATION_MS = "media_metadata_duration_ms"
private const val MEDIA_METADATA_KEY_ARTWORK_URL = "media_metadata_artwork_url"

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setMediaMetadata(toMediaMetadata())
    .build()

fun Song.toMediaMetadata(): MediaMetadata = MediaMetadata.Builder()
    .setTitle(title)
    .setArtist(artist)
    .setAlbumTitle(album)
    .setArtworkUri(artworkUrl?.toUri())
    .setIsBrowsable(false)
    .setIsPlayable(true)
    .apply { setExtrasIfPresent(artistId, albumId) }
    .build()

fun QueueSong.toMediaItem(): MediaItem {
    val metadataBuilder = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artworkUrl?.toUri())
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .apply { setExtrasIfPresent(artistId, albumId) }

    return MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(metadataBuilder.build())
        .build()
}

fun MediaItem.toQueueSong(): QueueSong {
    val metadata = mediaMetadata
    val extras = metadata.extras

    val title = metadata.title?.toString()
        ?: extras?.getString(MEDIA_METADATA_KEY_TITLE)
        ?: ""

    val artist = metadata.artist?.toString()
        ?: extras?.getString(MEDIA_METADATA_KEY_ARTIST)

    val album = metadata.albumTitle?.toString()
        ?: extras?.getString(MEDIA_METADATA_KEY_ALBUM)

    val durationMs = extras?.let { bundle ->
        if (bundle.containsKey(MEDIA_METADATA_KEY_DURATION_MS)) {
            bundle.getLong(MEDIA_METADATA_KEY_DURATION_MS)
        } else {
            null
        }
    }

    val artworkUrl = metadata.artworkUri?.toString()
        ?: extras?.getString(MEDIA_METADATA_KEY_ARTWORK_URL)

    return QueueSong(
        id = mediaId,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
    )
}

fun MediaController.buildQueueSongs(): List<QueueSong> {
    val items = ArrayList<QueueSong>(mediaItemCount)
    for (index in 0 until mediaItemCount) {
        items += getMediaItemAt(index).toQueueSong()
    }
    return items
}

private fun MediaMetadata.Builder.setExtrasIfPresent(artistId: String?, albumId: String?) {
    if (artistId == null && albumId == null) {
        return
    }
    val extras = Bundle()
    albumId?.let { extras.putString(MEDIA_METADATA_KEY_ALBUM_ID, it) }
    artistId?.let { extras.putString(MEDIA_METADATA_KEY_ARTIST_ID, it) }
    if (!extras.isEmpty) {
        setExtras(extras)
    }
}