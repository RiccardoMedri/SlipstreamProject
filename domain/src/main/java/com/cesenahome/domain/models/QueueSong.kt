package com.cesenahome.domain.models

import android.os.Parcelable
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.parcelize.Parcelize

@Parcelize
data class QueueSong(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val artworkUrl: String?
) : Parcelable {

    fun toMediaItem(): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUrl?.toUri())

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }
}
