package com.cesenahome.slipstream.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.cesenahome.data.download.DownloadComponents
import com.cesenahome.ui.download.DownloadInfra
import com.cesenahome.ui.download.DownloadUi

@UnstableApi
class AppDownloadProvider :
    DownloadInfra,
    DownloadUi {

    override val notificationChannelId: String
        get() = DownloadComponents.NOTIFICATION_CHANNEL_ID

    override fun downloadManager(context: Context): DownloadManager {
        return DownloadComponents.getDownloadManager(context)
    }

    override fun downloadCache(context: Context): Cache {
        return DownloadComponents.getDownloadCache(context)
    }

    override fun notificationHelper(context: Context): DownloadNotificationHelper {
        return DownloadComponents.getNotificationHelper(context)
    }
}