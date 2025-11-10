package com.cesenahome.data.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadService
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.data.repository.AlbumRepositoryImpl
import com.cesenahome.data.repository.ArtistRepositoryImpl
import com.cesenahome.data.repository.DownloadRepositoryImpl
import com.cesenahome.data.repository.HomepageRepositoryImpl
import com.cesenahome.data.repository.LoginRepositoryImpl
import com.cesenahome.data.repository.PlayerRepositoryImpl
import com.cesenahome.data.repository.PlaylistRepositoryImpl
import com.cesenahome.data.repository.SongRepositoryImpl
import com.cesenahome.data.session.SessionDataStore
import com.cesenahome.domain.di.RepositoryProvider
import com.cesenahome.domain.repository.AlbumRepository
import com.cesenahome.domain.repository.ArtistRepository
import com.cesenahome.domain.repository.DownloadRepository
import com.cesenahome.domain.repository.HomepageRepository
import com.cesenahome.domain.repository.LoginRepository
import com.cesenahome.domain.repository.PlayerRepository
import com.cesenahome.domain.repository.PlaylistRepository
import com.cesenahome.domain.repository.SongRepository


@UnstableApi
class RepositoryProviderImpl
    (
    private val context: Context,
    private val downloadServiceClass: Class<out DownloadService>,
): RepositoryProvider{

    val sessionStore = SessionDataStore(context)

    private val jellyfinApiClient: JellyfinApiClient by lazy {
        JellyfinApiClient(appContext = context)
    }

    private val sessionManager get() = jellyfinApiClient.session
    private val mediaClient get() = jellyfinApiClient.media
    private val playlistClient get() = jellyfinApiClient.playlist

    override val loginRepository: LoginRepository by lazy {
        LoginRepositoryImpl(
            sessionManager = sessionManager,
            sessionStore = sessionStore
        )
    }

    override val homeRepository: HomepageRepository by lazy {
        HomepageRepositoryImpl(
            mediaClient = mediaClient
        )
    }

    override val songRepository: SongRepository by lazy {
        SongRepositoryImpl(
            mediaClient = mediaClient,
            playlistClient = playlistClient,
        )
    }

    override val playerRepository: PlayerRepository by lazy {
        PlayerRepositoryImpl(
            mediaClient = mediaClient
        )
    }
    override val albumRepository: AlbumRepository by lazy {
        AlbumRepositoryImpl(
            mediaClient = mediaClient
        )
    }
    override val artistRepository: ArtistRepository by lazy {
        ArtistRepositoryImpl(
            mediaClient = mediaClient
        )
    }
    override val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepositoryImpl(
            mediaClient = mediaClient,
            playlistClient = playlistClient,
        )
    }
    override val downloadRepository: DownloadRepository by lazy {
        DownloadRepositoryImpl(
            context = context,
            mediaClient = mediaClient,
            downloadServiceClass = downloadServiceClass,
        )
    }
}
