package com.cesenahome.domain.di

import com.cesenahome.domain.usecases.*

object UseCaseProvider {

    lateinit var loginUseCase: LoginUseCase
    lateinit var getCurrentUserUseCase: GetCurrentUserUseCase
    lateinit var isLoggedInUseCase: IsLoggedInUseCase
    lateinit var logoutUseCase: LogoutUseCase
    lateinit var restoreSessionUseCase: RestoreSessionUseCase
    lateinit var getHomepageMenuUseCase: GetHomepageMenuUseCase
    lateinit var getLibraryCountsUseCase: GetLibraryCountsUseCase
    lateinit var getPagedSongsUseCase: GetPagedSongsUseCase
    lateinit var getPagedPlaylistsUseCase: GetPagedPlaylistsUseCase
    lateinit var resolveStreamUrlUseCase: ResolveStreamUrlUseCase
    lateinit var getSimpleSongsListUseCase: GetSimpleSongsListUseCase
    lateinit var getPagedAlbumUseCase: GetPagedAlbumUseCase
    lateinit var getPagedArtistsUseCase: GetPagedArtistsUseCase
    lateinit var getRandomSongUseCase: GetRandomSongUseCase
    lateinit var addSongToFavouritesUseCase: AddSongToFavouritesUseCase
    lateinit var ensureFavouritePlaylistUseCase: EnsureFavouritePlaylistUseCase
    lateinit var downloadAlbumUseCase: DownloadAlbumUseCase
    lateinit var removeAlbumDownloadUseCase: RemoveAlbumDownloadUseCase
    lateinit var downloadPlaylistUseCase: DownloadPlaylistUseCase
    lateinit var removePlaylistDownloadUseCase: RemovePlaylistDownloadUseCase
    lateinit var observeDownloadedSongIdsUseCase: ObserveDownloadedSongIdsUseCase
    lateinit var observeDownloadedAlbumIdsUseCase: ObserveDownloadedAlbumIdsUseCase
    lateinit var observeDownloadedPlaylistIdsUseCase: ObserveDownloadedPlaylistIdsUseCase




    fun setup(repositoryProvider: RepositoryProvider) {
        loginUseCase = LoginUseCaseImpl(repositoryProvider.loginRepository)
        getCurrentUserUseCase = GetCurrentUserUseCaseImpl(repositoryProvider.loginRepository)
        isLoggedInUseCase = IsLoggedInUseCaseImpl(repositoryProvider.loginRepository)
        logoutUseCase = LogoutUseCaseImpl(repositoryProvider.loginRepository)
        restoreSessionUseCase = RestoreSessionUseCaseImpl(repositoryProvider.loginRepository)
        getHomepageMenuUseCase = GetHomepageMenuUseCaseImpl(repositoryProvider.homeRepository)
        getLibraryCountsUseCase = GetLibraryCountsUseCaseImpl(repositoryProvider.homeRepository)
        getPagedSongsUseCase = GetPagedSongsUseCaseImpl(repositoryProvider.songRepository)
        getPagedPlaylistsUseCase = GetPagedPlaylistsUseCaseImpl(repositoryProvider.playlistRepository)
        resolveStreamUrlUseCase = ResolveStreamUrlUseCaseImpl(repositoryProvider.playerRepository)
        getSimpleSongsListUseCase = GetSimpleSongsListUseCaseImpl(repositoryProvider.songRepository)
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
        downloadAlbumUseCase = DownloadAlbumUseCaseImpl(repositoryProvider.downloadRepository)
        removeAlbumDownloadUseCase = RemoveAlbumDownloadUseCaseImpl(repositoryProvider.downloadRepository)
        downloadPlaylistUseCase = DownloadPlaylistUseCaseImpl(repositoryProvider.downloadRepository)
        removePlaylistDownloadUseCase = RemovePlaylistDownloadUseCaseImpl(repositoryProvider.downloadRepository)
        observeDownloadedSongIdsUseCase = ObserveDownloadedSongIdsUseCaseImpl(repositoryProvider.downloadRepository)
        observeDownloadedAlbumIdsUseCase = ObserveDownloadedAlbumIdsUseCaseImpl(repositoryProvider.downloadRepository)
        observeDownloadedPlaylistIdsUseCase = ObserveDownloadedPlaylistIdsUseCaseImpl(repositoryProvider.downloadRepository)
    }
}