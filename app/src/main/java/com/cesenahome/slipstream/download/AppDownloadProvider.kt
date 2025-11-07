package com.cesenahome.slipstream.download

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadManager
import com.cesenahome.data.download.DownloadComponents
import com.cesenahome.domain.player.PlayerDownloadDependencies

@UnstableApi
class AppDownloadProvider : PlayerDownloadDependencies.Provider {
    override fun getDownloadManager(context: Context): DownloadManager {
        return DownloadComponents.getDownloadManager(context)
    }

    override fun getDownloadCache(context: Context): Cache {
        return DownloadComponents.getDownloadCache(context)
    }
}