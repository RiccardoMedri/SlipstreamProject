package com.cesenahome.data.repository

import com.cesenahome.data.remote.session.JellyfinSessionManager
import com.cesenahome.domain.models.login.LoginResult
import com.cesenahome.domain.models.login.SessionData
import com.cesenahome.domain.models.login.User
import com.cesenahome.domain.repository.LoginRepository
import com.cesenahome.domain.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LoginRepositoryImpl(
    private val sessionManager: JellyfinSessionManager,
    private val sessionStore: SessionStore? = null
) : LoginRepository {

    // Holds the current user state. Emits null if no user is logged in.
    private val _currentUser = MutableStateFlow<User?>(null)

    override fun getCurrentUser(): Flow<User?> = _currentUser.asStateFlow()

    override fun isLoggedIn(): Flow<Boolean> = _currentUser.asStateFlow().map { it != null }

    override suspend fun login(serverUrl: String, username: String, password: String): LoginResult {
        return try {

            sessionManager.initializeOrUpdateClient(serverUrl)

            val user: User = sessionManager.login(username, password).getOrThrow()

            val token = sessionManager.currentApi()?.accessToken ?: error("Missing access token after login")

            sessionStore?.let { store ->
                withContext(Dispatchers.IO) {
                    store.save(
                        SessionData(
                            serverUrl = user.serverUrl,
                            accessToken = token,
                            userId = user.userId,
                            userName = user.name
                        )
                    )
                }
            }

            _currentUser.value = user
            LoginResult.Success(user)

        } catch (e: Exception) {
            LoginResult.Error(mapLoginError(e))
        }
    }

    override suspend fun restoreSession(): LoginResult {
        val saved = withContext(Dispatchers.IO) { sessionStore?.load() } ?: return LoginResult.Error("No saved session")
        return runCatching {
            sessionManager.initializeOrUpdateClient(saved.serverUrl)
            sessionManager.restoreSession(saved.accessToken, saved.userId).getOrThrow()
            val user = User(
                userId = saved.userId,
                name = saved.userName,
                serverUrl = saved.serverUrl
            )
            _currentUser.value = user
            user
        }.fold(
            onSuccess = { LoginResult.Success(it) },
            onFailure = { LoginResult.Error(mapLoginError(it)) }
        )
    }

    override suspend fun logout() {
        _currentUser.value = null
        sessionManager.clearSession()
        withContext(Dispatchers.IO) { sessionStore?.clear() }
    }
    private fun mapLoginError(t: Throwable): String {
        val msg = t.message ?: "Unknown error"
        return when {
            msg.contains("401") || msg.contains("Invalid username or password", ignoreCase = true) ->
                "Invalid username or password"
            msg.contains("SSL", ignoreCase = true) -> "SSL error while connecting to the server"
            msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("Failed to connect", ignoreCase = true) -> "Server unreachable"
            msg.contains("Invalid server URL", ignoreCase = true) -> "Invalid server URL"
            else -> msg
        }
    }
}
