package com.cesenahome.domain.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadManager

// Provides download-related infrastructure needed by the player while keeping the UI
// module decoupled from the data layer implementation.
@OptIn(UnstableApi::class)
object PlayerDownloadDependencies {

    interface Provider {

        fun getDownloadManager(context: Context): DownloadManager
        fun getDownloadCache(context: Context): Cache
    }

    @Volatile
    private var provider: Provider? = null

    fun setProvider(provider: Provider) {
        this.provider = provider
    }

    fun requireProvider(): Provider {
        return provider ?: error("PlayerDownloadDependencies provider has not been initialized")
    }
}