package com.cesenahome.slipstream

import android.app.Application
import com.cesenahome.data.di.RepositoryProviderImpl
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.player.PlayerDownloadDependencies
import com.cesenahome.slipstream.player.AppDownloadProvider

class CustomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UseCaseProvider.setup(RepositoryProviderImpl(context = this.applicationContext))
        PlayerDownloadDependencies.setProvider(AppDownloadProvider())
    }
}