package com.cesenahome.ui.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadManager

@OptIn(UnstableApi::class)
interface DownloadInfra {
    fun downloadManager(context: Context): DownloadManager
    fun downloadCache(context: Context): Cache
}