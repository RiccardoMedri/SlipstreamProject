package com.cesenahome.domain.di

import com.cesenahome.domain.usecases.*
import com.cesenahome.domain.usecases.auth.GetCurrentUserUseCase
import com.cesenahome.domain.usecases.auth.GetCurrentUserUseCaseImpl
import com.cesenahome.domain.usecases.auth.LoginUseCase
import com.cesenahome.domain.usecases.auth.LoginUseCaseImpl
import com.cesenahome.domain.usecases.auth.LogoutUseCase
import com.cesenahome.domain.usecases.auth.LogoutUseCaseImpl
import com.cesenahome.domain.usecases.auth.RestoreSessionUseCase
import com.cesenahome.domain.usecases.auth.RestoreSessionUseCaseImpl
import com.cesenahome.domain.usecases.download.ObserveDownloadedAlbumIdsUseCase
import com.cesenahome.domain.usecases.download.ObserveDownloadedAlbumIdsUseCaseImpl
import com.cesenahome.domain.usecases.download.ObserveDownloadedPlaylistIdsUseCase
import com.cesenahome.domain.usecases.download.ObserveDownloadedPlaylistIdsUseCaseImpl
import com.cesenahome.domain.usecases.download.ObserveDownloadedSongIdsUseCase
import com.cesenahome.domain.usecases.download.ObserveDownloadedSongIdsUseCaseImpl
import com.cesenahome.domain.usecases.favourites.AddSongToFavouritesUseCase
import com.cesenahome.domain.usecases.favourites.AddSongToFavouritesUseCaseImpl
import com.cesenahome.domain.usecases.homepage.EnsureFavouritePlaylistUseCase
import com.cesenahome.domain.usecases.homepage.EnsureFavouritePlaylistUseCaseImpl
import com.cesenahome.domain.usecases.homepage.GetHomepageMenuUseCase
import com.cesenahome.domain.usecases.homepage.GetHomepageMenuUseCaseImpl
import com.cesenahome.domain.usecases.homepage.GetLibraryCountsUseCase
import com.cesenahome.domain.usecases.homepage.GetLibraryCountsUseCaseImpl
import com.cesenahome.domain.usecases.libraries.GetPagedAlbumUseCase
import com.cesenahome.domain.usecases.libraries.GetPagedAlbumUseCaseImpl
import com.cesenahome.domain.usecases.libraries.GetPagedArtistsUseCase
import com.cesenahome.domain.usecases.libraries.GetPagedArtistsUseCaseImpl
import com.cesenahome.domain.usecases.libraries.GetPagedPlaylistsUseCase
import com.cesenahome.domain.usecases.libraries.GetPagedPlaylistsUseCaseImpl
import com.cesenahome.domain.usecases.libraries.GetPagedSongsUseCase
import com.cesenahome.domain.usecases.libraries.GetPagedSongsUseCaseImpl
import com.cesenahome.domain.usecases.playback.GetRandomSongUseCase
import com.cesenahome.domain.usecases.playback.GetRandomSongUseCaseImpl
import com.cesenahome.domain.usecases.playback.ResolveStreamUrlUseCase
import com.cesenahome.domain.usecases.playback.ResolveStreamUrlUseCaseImpl

object UseCaseProvider {

    lateinit var loginUseCase: LoginUseCase
    lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    lateinit var logoutUseCase: LogoutUseCase
    lateinit var restoreSessionUseCase: RestoreSessionUseCase
    lateinit var getHomepageMenuUseCase: GetHomepageMenuUseCase
    lateinit var getLibraryCountsUseCase: GetLibraryCountsUseCase
    lateinit var getPagedSongsUseCase: GetPagedSongsUseCase
    lateinit var getPagedPlaylistsUseCase: GetPagedPlaylistsUseCase
    lateinit var resolveStreamUrlUseCase: ResolveStreamUrlUseCase
    lateinit var getPagedAlbumUseCase: GetPagedAlbumUseCase
    lateinit var getPagedArtistsUseCase: GetPagedArtistsUseCase
    lateinit var getRandomSongUseCase: GetRandomSongUseCase
    lateinit var addSongToFavouritesUseCase: AddSongToFavouritesUseCase
    lateinit var ensureFavouritePlaylistUseCase: EnsureFavouritePlaylistUseCase
    lateinit var toggleCollectionDownloadUseCase: ToggleCollectionDownloadUseCase
    lateinit var observeDownloadedSongIdsUseCase: ObserveDownloadedSongIdsUseCase
    lateinit var observeDownloadedAlbumIdsUseCase: ObserveDownloadedAlbumIdsUseCase
    lateinit var observeDownloadedPlaylistIdsUseCase: ObserveDownloadedPlaylistIdsUseCase



    fun setup(repositoryProvider: RepositoryProvider) {
        loginUseCase = LoginUseCaseImpl(repositoryProvider.loginRepository)
        getCurrentUserUseCase = GetCurrentUserUseCaseImpl(repositoryProvider.loginRepository)
        logoutUseCase = LogoutUseCaseImpl(repositoryProvider.loginRepository)
        restoreSessionUseCase = RestoreSessionUseCaseImpl(repositoryProvider.loginRepository)
        getHomepageMenuUseCase = GetHomepageMenuUseCaseImpl(repositoryProvider.homeRepository)
        getLibraryCountsUseCase = GetLibraryCountsUseCaseImpl(repositoryProvider.homeRepository)
        getPagedSongsUseCase = GetPagedSongsUseCaseImpl(repositoryProvider.songRepository)
        getPagedPlaylistsUseCase = GetPagedPlaylistsUseCaseImpl(repositoryProvider.playlistRepository)
        resolveStreamUrlUseCase = ResolveStreamUrlUseCaseImpl(repositoryProvider.playerRepository)
        getPagedAlbumUseCase = GetPagedAlbumUseCaseImpl(repositoryProvider.albumRepository)
        getPagedArtistsUseCase = GetPagedArtistsUseCaseImpl(repositoryProvider.artistRepository)
        getRandomSongUseCase = GetRandomSongUseCaseImpl(repositoryProvider.songRepository)
        addSongToFavouritesUseCase = AddSongToFavouritesUseCaseImpl(
            repositoryProvider.songRepository,
            repositoryProvider.playlistRepository,
        )
        ensureFavouritePlaylistUseCase = EnsureFavouritePlaylistUseCaseImpl(
            repositoryProvider.playlistRepository,
        )
        toggleCollectionDownloadUseCase = ToggleCollectionDownloadUseCaseImpl(repositoryProvider.downloadRepository)
        observeDownloadedSongIdsUseCase = ObserveDownloadedSongIdsUseCaseImpl(repositoryProvider.downloadRepository)
        observeDownloadedAlbumIdsUseCase = ObserveDownloadedAlbumIdsUseCaseImpl(repositoryProvider.downloadRepository)
        observeDownloadedPlaylistIdsUseCase = ObserveDownloadedPlaylistIdsUseCaseImpl(repositoryProvider.downloadRepository)
    }
}