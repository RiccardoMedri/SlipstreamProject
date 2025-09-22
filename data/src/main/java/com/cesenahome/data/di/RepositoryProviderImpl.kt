package com.cesenahome.data.di

import android.content.Context
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.data.repository.HomepageRepositoryImpl
import com.cesenahome.data.repository.LoginRepositoryImpl
import com.cesenahome.domain.repository.HomepageRepository
import com.cesenahome.data.session.SessionDataStore
import com.cesenahome.domain.di.RepositoryProvider
import com.cesenahome.domain.repository.LoginRepository

class RepositoryProviderImpl(
    private val context: Context
): RepositoryProvider{

    val sessionStore = SessionDataStore(context)

    private val jellyfinApiClient: JellyfinApiClient by lazy {
        JellyfinApiClient(appContext = context)
    }

    // Provide LoginRepository
    override val loginRepository: LoginRepository by lazy {
        LoginRepositoryImpl(
            jellyfinApiClient = jellyfinApiClient,
            sessionStore = sessionStore)
    }

    override val homeRepository: HomepageRepository by lazy {
        HomepageRepositoryImpl(
            jellyfinApiClient = jellyfinApiClient
        )
    }

}