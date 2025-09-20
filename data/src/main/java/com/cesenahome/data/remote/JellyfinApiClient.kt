package com.cesenahome.data.remote

import android.content.Context
import com.cesenahome.domain.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo

class JellyfinApiClient(
    appContext: Context,
    clientName: String = "CesenaHome",
    clientVersion: String = "0.1.0"
) {
    private val jellyfin: Jellyfin = createJellyfin {
        context = appContext // required on Android
        clientInfo = ClientInfo(name = clientName, version = clientVersion)
    }

    @Volatile
    private var api: ApiClient? = null

    fun initializeOrUpdateClient(serverUrl: String) {
        val base = normalizeServerUrl(serverUrl)
        api = jellyfin.createApi(baseUrl = base) // you can also pass a saved accessToken here
    }

    suspend fun login(username: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        val apiClient = api ?: return@withContext Result.failure(
            IllegalStateException("ApiClient not initialized. Call initializeOrUpdateClient() first.")
        )

        try {
            // Perform the request; the 'by' delegate executes the call and either returns
            // the AuthenticationResult or throws InvalidStatusException on non-2xx.
            val auth by apiClient.userApi.authenticateUserByName(
                username = username,
                password = password // empty string allowed for passwordless users
            )

            // Store token on the ApiClient so subsequent calls are authenticated
            apiClient.update(accessToken = auth.accessToken)

            val user = User(
                userId = auth.user?.id.toString() ?: "",           // prefer non-null id from server
                name = auth.user?.name,
                serverUrl = apiClient.baseUrl.toString()
            )

            Result.success(user)

        } catch (e: InvalidStatusException) {
            // 401 is invalid credentials; other codes bubble up here too
            val message = if (e.status == 401) "Invalid username or password." else "Login failed: HTTP ${e.status}"
            Result.failure(Exception(message, e))
        } catch (t: Throwable) {
            Result.failure(Exception("Login failed: ${t.message}", t))
        }
    }

    fun currentApi(): ApiClient? = api

    fun clearSession() {
        api?.update(accessToken = null)
        // optionally: api = null // if you want to force re-init base URL after logout
    }

    private fun normalizeServerUrl(input: String): String {
        require(input.isNotBlank()) { "Server URL is blank." }
        require(Regex("^https?://.+").matches(input)) { "Server URL must start with http:// or https://." }
        return if (input.endsWith("/")) input else "$input/"
    }
}
