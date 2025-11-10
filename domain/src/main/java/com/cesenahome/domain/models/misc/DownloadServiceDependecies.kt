package com.cesenahome.domain.models.misc

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper

@OptIn(UnstableApi::class)
object DownloadServiceDependencies {

    interface Provider {
        val notificationChannelId: String
        fun getDownloadManager(context: Context): DownloadManager
        fun getNotificationHelper(context: Context): DownloadNotificationHelper
    }

    @Volatile
    private var provider: Provider? = null

    fun setProvider(provider: Provider) {
        this.provider = provider
    }

    fun requireProvider(): Provider {
        return provider ?: error("DownloadServiceDependencies provider has not been initialized")
    }
}