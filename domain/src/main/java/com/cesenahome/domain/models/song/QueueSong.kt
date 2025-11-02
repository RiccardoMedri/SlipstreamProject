package com.cesenahome.domain.models.song

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class QueueSong(
    val id: String,
    val title: String,
    val artist: String?,
    val artistId: String? = null,
    val album: String?,
    val albumId: String? = null,
    val durationMs: Long?,
    val artworkUrl: String?
) : Parcelable