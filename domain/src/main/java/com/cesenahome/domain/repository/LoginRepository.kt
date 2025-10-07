package com.cesenahome.domain.repository

import com.cesenahome.domain.models.login.LoginResult
import com.cesenahome.domain.models.login.User
import kotlinx.coroutines.flow.Flow

interface LoginRepository {

    suspend fun login(serverUrl: String, username: String, password: String): LoginResult

    fun getCurrentUser(): Flow<User?>

    suspend fun logout()

    suspend fun restoreSession(): LoginResult

    fun isLoggedIn(): Flow<Boolean>

}