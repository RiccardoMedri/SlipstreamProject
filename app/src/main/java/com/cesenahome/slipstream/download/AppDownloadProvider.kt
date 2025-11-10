package com.cesenahome.slipstream.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.cesenahome.data.download.DownloadComponents
import com.cesenahome.domain.models.misc.DownloadServiceDependencies
import com.cesenahome.domain.player.PlayerDownloadDependencies

@UnstableApi
class AppDownloadProvider :
    PlayerDownloadDependencies.Provider,
    DownloadServiceDependencies.Provider {

    override val notificationChannelId: String
        get() = DownloadComponents.NOTIFICATION_CHANNEL_ID

    override fun getDownloadManager(context: Context): DownloadManager {
        return DownloadComponents.getDownloadManager(context)
    }

    override fun getDownloadCache(context: Context): Cache {
        return DownloadComponents.getDownloadCache(context)
    }

    override fun getNotificationHelper(context: Context): DownloadNotificationHelper {
        return DownloadComponents.getNotificationHelper(context)
    }
}