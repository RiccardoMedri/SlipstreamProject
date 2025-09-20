package com.cesenahome.data.repository

import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.domain.models.LoginResult
import com.cesenahome.domain.models.SessionData
import com.cesenahome.domain.models.User
import com.cesenahome.domain.repository.LoginRepository
import com.cesenahome.domain.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LoginRepositoryImpl(
    private val jellyfinApiClient: JellyfinApiClient,
    private val sessionStore: SessionStore? = null
) : LoginRepository {

    // Holds the current user state. Emits null if no user is logged in.
    private val _currentUser = MutableStateFlow<User?>(null)            //DO I NEED THE NON UNDERSCORED VERSION OF THIS VARIABLE?

    override fun getCurrentUser(): Flow<User?> = _currentUser.asStateFlow()

    override fun isLoggedIn(): Flow<Boolean> = _currentUser.asStateFlow().map { it != null }

    override suspend fun login(serverUrl: String, username: String, password: String): LoginResult {
        return try {

            jellyfinApiClient.initializeOrUpdateClient(serverUrl)

            val user: User = jellyfinApiClient.login(username, password).getOrThrow()

            val token = jellyfinApiClient.currentApi()?.accessToken ?: error("Missing access token after login")

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
            jellyfinApiClient.initializeOrUpdateClient(saved.serverUrl)
            jellyfinApiClient.currentApi()?.update(accessToken = saved.accessToken)
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
        jellyfinApiClient.clearSession()
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