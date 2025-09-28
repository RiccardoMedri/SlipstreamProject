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
    private var apiClient: ApiClient? = null

    @Volatile
    private var currentUserId: UUID? = null

    fun initializeOrUpdateClient(serverUrl: String) {
        val base = normalizeServerUrl(serverUrl)
        apiClient = jellyfin.createApi(baseUrl = base)
    }

    suspend fun login(username: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        val currentApi = apiClient ?: return@withContext Result.failure(
            IllegalStateException("ApiClient not initialized. Call initializeOrUpdateClient() first.")
        )
        try {
            val auth by currentApi.userApi.authenticateUserByName(
                username = username,
                password = password
            )

            currentApi.update(accessToken = auth.accessToken)

            currentUserId = auth.user?.id

            val uidString = auth.user?.id?.toString().orEmpty()

            val user = User(
                userId = uidString,
                name = auth.user?.name,
                serverUrl = currentApi.baseUrl.toString()
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
        val currentApi = apiClient ?: error("ApiClient not initialized")
        val response by currentApi.itemsApi.getItems(
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
    fun currentApi(): ApiClient? = apiClient
    suspend fun getArtistsCount(): Int = getCountForKinds(BaseItemKind.MUSIC_ARTIST)
    suspend fun getAlbumsCount(): Int = getCountForKinds(BaseItemKind.MUSIC_ALBUM)
    suspend fun getPlaylistsCount(): Int = getCountForKinds(BaseItemKind.PLAYLIST)
    suspend fun getSongsCount(): Int = getCountForKinds(BaseItemKind.AUDIO)
    fun baseUrl(): String = apiClient?.baseUrl?.toString().orEmpty()
    fun accessToken(): String? = apiClient?.accessToken
    fun currentUserIdString(): String? = currentUserId?.toString()
    suspend fun fetchSongsAlphabetical(startIndex: Int, limit: Int): List<BaseItemDto> = withContext(Dispatchers.IO) {
        val currentApi = currentApi() ?: error("ApiClient not initialized")
        val response by currentApi.itemsApi.getItems(
            userId = getCurrentUserId(),
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            sortBy = listOf(ItemSortBy.NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
            startIndex = startIndex,
            limit = limit
        )
        response.items
    }
    fun buildImageUrl(itemId: String, imageTag: String?, maxSize: Int = 256): String? {
        val base = baseUrl().takeIf { it.isNotBlank() } ?: return null
        val token = accessToken() ?: return null
        val tagParam = imageTag?.let { "&tag=$it" }.orEmpty()
        return "${base}Items/$itemId/Images/Primary?quality=90&maxWidth=$maxSize$tagParam&api_key=$token"
    }

    fun buildAudioStreamUrl(itemId: String): String? {
        val base = baseUrl().takeIf { it.isNotBlank() } ?: return null
        val token = accessToken() ?: return null
        // val user = currentUserIdString() ?: return null // Not needed for direct URL

        // 1) Try direct/original file (no transcoding):
        // Works if the original container is playable by ExoPlayer on the device.
        // ExoPlayer supports mp3/aac/flac/wav/ogg/m4a etc. If this 401s or format is weird, fallback.
        val direct = "${base}Items/$itemId/File?api_key=$token"
        return direct // Return the direct URL
        // Universal endpoint picks a suitable container/codec automatically.
        // return "${base}Audio/$itemId/universal?UserId=$user&api_key=$token"
    }

    fun clearSession() {
        apiClient?.update(accessToken = null)
    }

    private fun normalizeServerUrl(input: String): String {
        require(input.isNotBlank()) { "Server URL is blank." }
        require(Regex("^https?://.+").matches(input)) { "Server URL must start with http:// or https://." }
        return if (input.endsWith("/")) input else "$input/"
    }
}
