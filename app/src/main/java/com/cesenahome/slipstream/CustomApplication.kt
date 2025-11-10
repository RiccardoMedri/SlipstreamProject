package com.cesenahome.slipstream

import android.app.Application
import com.cesenahome.data.di.RepositoryProviderImpl
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.misc.DownloadServiceDependencies
import com.cesenahome.domain.player.PlayerDownloadDependencies
import com.cesenahome.slipstream.download.AppDownloadProvider
import com.cesenahome.ui.download.SlipstreamDownloadService

class CustomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val downloadProvider = AppDownloadProvider()
        UseCaseProvider.setup(
            RepositoryProviderImpl(
                context = this.applicationContext,
                downloadServiceClass = SlipstreamDownloadService::class.java,
            ),
        )
        PlayerDownloadDependencies.setProvider(downloadProvider)
        DownloadServiceDependencies.setProvider(downloadProvider)
    }
}