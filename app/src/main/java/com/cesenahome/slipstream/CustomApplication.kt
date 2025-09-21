package com.cesenahome.slipstream

import android.app.Application
import com.cesenahome.data.di.RepositoryProviderImpl
import com.cesenahome.domain.di.UseCaseProvider

class CustomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UseCaseProvider.setup(RepositoryProviderImpl(context = this.applicationContext))
    }
}