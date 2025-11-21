package com.cesenahome.ui.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadNotificationHelper

@OptIn(UnstableApi::class)
interface DownloadUi {
    val notificationChannelId: String
    fun notificationHelper(context: Context): DownloadNotificationHelper
}