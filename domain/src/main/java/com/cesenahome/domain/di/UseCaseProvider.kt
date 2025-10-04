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
    lateinit var resolveStreamUrlUseCase: ResolveStreamUrlUseCase
    lateinit var getSimpleSongsListUseCase: GetSimpleSongsListUseCase
    lateinit var getPagedAlbumUseCase: GetPagedAlbumUseCase
    lateinit var getPagedArtistsUseCase: GetPagedArtistsUseCase
    lateinit var getRandomSongUseCase: GetRandomSongUseCase



    fun setup(repositoryProvider: RepositoryProvider) {
        loginUseCase = LoginUseCaseImpl(repositoryProvider.loginRepository)
        getCurrentUserUseCase = GetCurrentUserUseCaseImpl(repositoryProvider.loginRepository)
        isLoggedInUseCase = IsLoggedInUseCaseImpl(repositoryProvider.loginRepository)
        logoutUseCase = LogoutUseCaseImpl(repositoryProvider.loginRepository)
        restoreSessionUseCase = RestoreSessionUseCaseImpl(repositoryProvider.loginRepository)
        getHomepageMenuUseCase = GetHomepageMenuUseCaseImpl(repositoryProvider.homeRepository)
        getLibraryCountsUseCase = GetLibraryCountsUseCaseImpl(repositoryProvider.homeRepository)
        getPagedSongsUseCase = GetPagedSongsUseCaseImpl(repositoryProvider.songRepository)
        resolveStreamUrlUseCase = ResolveStreamUrlUseCaseImpl(repositoryProvider.playerRepository)
        getSimpleSongsListUseCase = GetSimpleSongsListUseCaseImpl(repositoryProvider.songRepository)
        getPagedAlbumUseCase = GetPagedAlbumUseCaseImpl(repositoryProvider.albumRepository)
        getPagedArtistsUseCase = GetPagedArtistsUseCaseImpl(repositoryProvider.artistRepository)
        getRandomSongUseCase = GetRandomSongUseCaseImpl(repositoryProvider.songRepository)
    }
}