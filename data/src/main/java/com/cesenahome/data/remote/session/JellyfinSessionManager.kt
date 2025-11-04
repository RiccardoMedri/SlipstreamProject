package com.cesenahome.data.remote.session

import com.cesenahome.data.remote.JellyfinClientFactory
import com.cesenahome.data.remote.util.parseUuidOrNull
import com.cesenahome.domain.models.login.User
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.UUID

class JellyfinSessionManager(
    private val clientFactory: JellyfinClientFactory,
) {

    //The currently authenticated Jellyfin user
    @Volatile
    private var currentUserId: UUID? = null

    //Create or swap the ApiClient to point at a new server
    //This lets you validate a server URL before prompting for credentials
    fun initializeOrUpdateClient(serverUrl: String) {
        clientFactory.initializeOrUpdateClient(serverUrl)
    }

    fun currentApi(): ApiClient? = clientFactory.currentApi()

    fun requireApi(): ApiClient = clientFactory.requireApi()

    fun currentUserId(): UUID? = currentUserId

    fun requireUserId(): UUID = currentUserId ?: error("No authenticated user")

    fun accessToken(): String? = clientFactory.currentApi()?.accessToken

    fun updateAuthenticatedUser(userId: UUID?) {
        currentUserId = userId
    }

    fun clearSession() {
        clientFactory.clearSession()
        currentUserId = null
    }

    //Authenticate with the server using username/password and cache the session info
    suspend fun login(username: String, password: String): Result<User> {
        val api = runCatching { requireApi() }.getOrElse { return Result.failure(it) }
        return try {
            val auth by api.userApi.authenticateUserByName(
                username = username,
                password = password,
            )

            val accessToken = auth.accessToken ?: error("Missing access token in authentication response")
            api.update(accessToken = accessToken)

            val userUuid = auth.user?.id ?: error("Missing user identifier in authentication response")
            updateAuthenticatedUser(userUuid)

            val user = User(
                userId = userUuid.toString(),
                name = auth.user?.name,
                serverUrl = api.baseUrl.toString(),
            )
            Result.success(user)
        } catch (e: InvalidStatusException) {
            val message = if (e.status == 401) {
                "Invalid username or password."
            } else {
                "Login failed: HTTP ${e.status}"
            }
            Result.failure(Exception(message, e))
        } catch (t: Throwable) {
            Result.failure(Exception("Login failed: ${t.message}", t))
        }
    }

    //Allow authentication without asking the server to log in again
    fun restoreSession(accessToken: String?, userId: String?): Result<Unit> {
        val api = runCatching { requireApi() }.getOrElse { return Result.failure(it) }
        if (!accessToken.isNullOrBlank()) {
            api.update(accessToken = accessToken)
        }
        val uuid = userId.parseUuidOrNull() ?: return Result.failure(IllegalArgumentException("Invalid stored user identifier"))
        updateAuthenticatedUser(uuid)
        return Result.success(Unit)
    }
}