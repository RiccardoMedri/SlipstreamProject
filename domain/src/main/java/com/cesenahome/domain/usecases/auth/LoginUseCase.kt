package com.cesenahome.domain.usecases.auth

import com.cesenahome.domain.models.login.LoginResult
import com.cesenahome.domain.repository.LoginRepository

interface LoginUseCase {
    suspend operator fun invoke(serverUrl: String, username: String, password: String): LoginResult
}

class LoginUseCaseImpl(
    private val loginRepository: LoginRepository
) : LoginUseCase {
    override suspend fun invoke(serverUrl: String, username: String, password: String): LoginResult {
        if (serverUrl.isBlank() || username.isBlank()) { // Password can be blank for some Jellyfin setups
            return LoginResult.Error("Server URL and Username cannot be empty.")
        }
        return loginRepository.login(serverUrl, username, password)
    }
}