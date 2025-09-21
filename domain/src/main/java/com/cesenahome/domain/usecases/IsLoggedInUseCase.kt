package com.cesenahome.domain.usecases

import com.cesenahome.domain.repository.LoginRepository
import kotlinx.coroutines.flow.Flow

interface IsLoggedInUseCase {
    operator fun invoke(): Flow<Boolean>
}

class IsLoggedInUseCaseImpl(
    private val loginRepository: LoginRepository
) : IsLoggedInUseCase {
    override fun invoke() = loginRepository.isLoggedIn()
}
