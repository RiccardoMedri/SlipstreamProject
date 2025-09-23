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
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

class JellyfinApiClient(
    appContext: Context,
    clientName: String = "CesenaHome",
    clientVersion: String = "0.1.0"
) {
    private val jellyfin: Jellyfin = createJellyfin {
        context = appContext
        clientInfo = ClientInfo(name = clientName, version = clientVersion)
    }

    @Volatile
    private var api: ApiClient? = null

    @Volatile
    private var currentUserId: UUID? = null

    fun initializeOrUpdateClient(serverUrl: String) {
        val base = normalizeServerUrl(serverUrl)
        api = jellyfin.createApi(baseUrl = base)
    }

    suspend fun login(username: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        val apiClient = api ?: return@withContext Result.failure(
            IllegalStateException("ApiClient not initialized. Call initializeOrUpdateClient() first.")
        )
        try {
            val auth by apiClient.userApi.authenticateUserByName(
                username = username,
                password = password
            )

            apiClient.update(accessToken = auth.accessToken)

            currentUserId = auth.user?.id

            val uidString = auth.user?.id?.toString().orEmpty()

            val user = User(
                userId = uidString,
                name = auth.user?.name,
                serverUrl = apiClient.baseUrl.toString()
            )
            Result.success(user)
        } catch (e: InvalidStatusException) {
            val message = if (e.status == 401) "Invalid username or password." else "Login failed: HTTP ${e.status}"
            Result.failure(Exception(message, e))
        } catch (t: Throwable) {
            Result.failure(Exception("Login failed: ${t.message}", t))
        }
    }

    private suspend fun getCountForKinds(vararg kinds: BaseItemKind): Int = withContext(Dispatchers.IO) {
        val apiClient = api ?: error("ApiClient not initialized")
        val response by apiClient.itemsApi.getItems(
            userId = getCurrentUserId(),   // now UUID?
            recursive = true,
            includeItemTypes = kinds.toList(),
            startIndex = 0,
            limit = 0
        )
        response.totalRecordCount ?: response.items.size
    }

    fun getCurrentUserId(): UUID? = currentUserId

    fun setCurrentUserIdFromString(id: String?) {
        currentUserId = id?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    suspend fun getArtistsCount(): Int = getCountForKinds(BaseItemKind.MUSIC_ARTIST)
    suspend fun getAlbumsCount(): Int = getCountForKinds(BaseItemKind.MUSIC_ALBUM)
    suspend fun getPlaylistsCount(): Int = getCountForKinds(BaseItemKind.PLAYLIST)
    suspend fun getSongsCount(): Int = getCountForKinds(BaseItemKind.AUDIO)

    suspend fun fetchSongsAlphabetical(startIndex: Int, limit: Int): List<BaseItemDto> = withContext(Dispatchers.IO) {
        val apiClient = currentApi() ?: error("ApiClient not initialized")
        val response by apiClient.itemsApi.getItems(
            userId = getCurrentUserId(),
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            startIndex = startIndex,
            limit = limit
        )
        response.items
    }

    fun currentApi(): ApiClient? = api

    fun clearSession() {
        api?.update(accessToken = null)
    }

    private fun normalizeServerUrl(input: String): String {
        require(input.isNotBlank()) { "Server URL is blank." }
        require(Regex("^https?://.+").matches(input)) { "Server URL must start with http:// or https://." }
        return if (input.endsWith("/")) input else "$input/"
    }
}
