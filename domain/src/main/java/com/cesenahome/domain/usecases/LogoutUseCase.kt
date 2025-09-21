package com.cesenahome.domain.usecases

import com.cesenahome.domain.repository.LoginRepository

interface LogoutUseCase {
    suspend operator fun invoke()
}

class LogoutUseCaseImpl(
    private val loginRepository: LoginRepository
) : LogoutUseCase {
    override suspend fun invoke() {
        loginRepository.logout()
    }
}