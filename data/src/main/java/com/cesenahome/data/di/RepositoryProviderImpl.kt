package com.cesenahome.data.di

import android.content.Context
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.data.repository.AlbumRepositoryImpl
import com.cesenahome.data.repository.ArtistRepositoryImpl
import com.cesenahome.data.repository.HomepageRepositoryImpl
import com.cesenahome.data.repository.LoginRepositoryImpl
import com.cesenahome.domain.repository.HomepageRepository
import com.cesenahome.data.session.SessionDataStore
import com.cesenahome.domain.di.RepositoryProvider
import com.cesenahome.domain.repository.LoginRepository
import com.cesenahome.domain.repository.SongRepository
import com.cesenahome.data.repository.SongRepositoryImpl
import com.cesenahome.domain.repository.PlayerRepository
import com.cesenahome.data.repository.PlayerRepositoryImpl
import com.cesenahome.domain.repository.AlbumRepository
import com.cesenahome.domain.repository.ArtistRepository
import com.cesenahome.data.repository.PlaylistRepositoryImpl
import com.cesenahome.domain.repository.PlaylistRepository

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

    override val songRepository: SongRepository by lazy {
        SongRepositoryImpl(
            jellyfinApiClient = jellyfinApiClient
        )
    }

    override val playerRepository: PlayerRepository by lazy {
        PlayerRepositoryImpl(
            jellyfinApiClient = jellyfinApiClient
        )
    }
    override val albumRepository: AlbumRepository by lazy {
        AlbumRepositoryImpl(
            apiClient = jellyfinApiClient
        )
    }
    override val artistRepository: ArtistRepository by lazy {
        ArtistRepositoryImpl(
            apiClient = jellyfinApiClient
        )
    }
    override val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepositoryImpl(
            apiClient = jellyfinApiClient
        )
    }
}