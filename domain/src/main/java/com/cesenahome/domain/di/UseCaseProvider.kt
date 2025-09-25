package com.cesenahome.domain.di

import com.cesenahome.domain.repository.LoginRepository // Assuming this will be passed via setup
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



    fun setup(repositoryProvider: RepositoryProvider) {
        loginUseCase = LoginUseCaseImpl(repositoryProvider.loginRepository)
        getCurrentUserUseCase = GetCurrentUserUseCaseImpl(repositoryProvider.loginRepository)
        isLoggedInUseCase = IsLoggedInUseCaseImpl(repositoryProvider.loginRepository)
        logoutUseCase = LogoutUseCaseImpl(repositoryProvider.loginRepository)
        restoreSessionUseCase = RestoreSessionUseCaseImpl(repositoryProvider.loginRepository)
        getHomepageMenuUseCase = GetHomepageMenuUseCaseImpl(repositoryProvider.homeRepository)
        getLibraryCountsUseCase = GetLibraryCountsUseCaseImpl(repositoryProvider.homeRepository)
        getPagedSongsUseCase = GetPagedSongsUseCaseImpl(repositoryProvider.songsRepository)
        resolveStreamUrlUseCase = ResolveStreamUrlUseCaseImpl(repositoryProvider.playerRepository)
    }
}