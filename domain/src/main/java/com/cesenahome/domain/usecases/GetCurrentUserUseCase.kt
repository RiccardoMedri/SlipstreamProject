package com.cesenahome.domain.usecases

import com.cesenahome.domain.models.User
import com.cesenahome.domain.repository.LoginRepository
import kotlinx.coroutines.flow.Flow

interface GetCurrentUserUseCase {
    operator fun invoke(): Flow<User?>
}

class GetCurrentUserUseCaseImpl(
    private val loginRepository: LoginRepository
) : GetCurrentUserUseCase {
    override fun invoke(): Flow<User?> {
        return loginRepository.getCurrentUser()
    }
}