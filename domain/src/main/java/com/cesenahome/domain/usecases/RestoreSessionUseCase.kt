package com.cesenahome.domain.usecases

import com.cesenahome.domain.models.LoginResult
import com.cesenahome.domain.repository.LoginRepository

interface RestoreSessionUseCase {
    suspend operator fun invoke(): LoginResult
}

class RestoreSessionUseCaseImpl(
    private val loginRepository: LoginRepository
) : RestoreSessionUseCase {
    override suspend fun invoke(): LoginResult {
        return loginRepository.restoreSession()
    }
}