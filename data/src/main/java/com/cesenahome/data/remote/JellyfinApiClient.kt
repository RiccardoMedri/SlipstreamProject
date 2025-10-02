package com.cesenahome.data.remote

import android.content.Context
import com.cesenahome.domain.models.AlbumPagingRequest
import com.cesenahome.domain.models.AlbumSortField
import com.cesenahome.domain.models.ArtistPagingRequest
import com.cesenahome.domain.models.ArtistSortField
import com.cesenahome.domain.models.SortDirection
import com.cesenahome.domain.models.SongPagingRequest
import com.cesenahome.domain.models.SongSortField
import com.cesenahome.domain.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.audioApi
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.ImageType

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
        val currentApi = currentApi() ?: error("ApiClient not initialized")
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
    fun parseUuidOrNull(id: String?): UUID? = id?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    fun currentApi(): ApiClient? = apiClient
    suspend fun getArtistsCount(): Int = getCountForKinds(BaseItemKind.MUSIC_ARTIST)
    suspend fun getAlbumsCount(): Int = getCountForKinds(BaseItemKind.MUSIC_ALBUM)
    suspend fun getPlaylistsCount(): Int = getCountForKinds(BaseItemKind.PLAYLIST)
    suspend fun getSongsCount(): Int = getCountForKinds(BaseItemKind.AUDIO)
    fun getImage (itemId: String, imageTag: String?, maxSize: Int): String? {
        val api = currentApi() ?: return null
        val id = parseUuidOrNull(itemId) ?: return null
        return api.imageApi.getItemImageUrl(
            itemId = id,
            imageType = ImageType.PRIMARY,
            tag = imageTag,
            maxWidth = maxSize,
            maxHeight = maxSize,
            quality = 100
        )
    }
    fun getAudio (itemId: String): String? {
        val api = currentApi() ?: return null
        val id = parseUuidOrNull(itemId) ?: return null
        return api.audioApi.getAudioStreamUrl(
            itemId = id,
            static = true
        )
    }
    suspend fun fetchSongs(startIndex: Int, limit: Int, request: SongPagingRequest
    ): List<BaseItemDto> = withContext(Dispatchers.IO) {
        val currentApi = currentApi() ?: error("ApiClient not initialized")
        val parentUuid = parseUuidOrNull(request.albumId)
        val response by currentApi.itemsApi.getItems(
            userId = getCurrentUserId(),
            parentId = parentUuid,
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            sortBy = buildSongSortBy(request.sortOption.field, parentUuid != null),
            sortOrder = listOf(request.sortOption.direction.toApiSortOrder()),
            startIndex = startIndex,
            limit = limit,
            searchTerm = request.searchQuery.takeIfNotBlank()
        )
        response.items
    }
    suspend fun fetchAlbums(startIndex: Int, limit: Int, request: AlbumPagingRequest): List<BaseItemDto> = withContext(Dispatchers.IO) {
        val currentApi = currentApi() ?: error("ApiClient not initialized")
        val artistUuid = parseUuidOrNull(request.artistId)
        val response by currentApi.itemsApi.getItems(
            userId = getCurrentUserId(),
            albumArtistIds = artistUuid?.let { listOf(it) },
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            sortBy = listOf(request.sortOption.field.toApiSortBy()),
            sortOrder = listOf(request.sortOption.direction.toApiSortOrder()),
            startIndex = startIndex,
            limit = limit,
            searchTerm = request.searchQuery.takeIfNotBlank()
        )
        response.items
    }

    suspend fun fetchArtists(startIndex: Int, limit: Int, request: ArtistPagingRequest): List<BaseItemDto> = withContext(Dispatchers.IO) {
        val currentApi = currentApi() ?: error("ApiClient not initialized")
        val response by currentApi.itemsApi.getItems(
            userId = getCurrentUserId(),
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
            sortBy = listOf(request.sortOption.field.toApiSortBy()),
            sortOrder = listOf(request.sortOption.direction.toApiSortOrder()),
            startIndex = startIndex,
            limit = limit,
            searchTerm = request.searchQuery.takeIfNotBlank()
        )
        response.items
    }
    fun clearSession() {
        apiClient?.update(accessToken = null)
    }





    //HELPER FUNCTIONS
    private fun normalizeServerUrl(input: String): String {
        require(input.isNotBlank()) { "Server URL is blank." }
        require(Regex("^https?://.+").matches(input)) { "Server URL must start with http:// or https://." }
        return if (input.endsWith("/")) input else "$input/"
    }
    private fun buildSongSortBy(sortField: SongSortField, hasParent: Boolean): List<ItemSortBy> {
        return when {
            hasParent && sortField == SongSortField.NAME -> listOf(ItemSortBy.INDEX_NUMBER)
            else -> listOf(sortField.toApiSortBy())
        }
    }
    private fun SongSortField.toApiSortBy(): ItemSortBy = when (this) {
        SongSortField.NAME -> ItemSortBy.NAME
        SongSortField.ALBUM_ARTIST -> ItemSortBy.ALBUM_ARTIST
        SongSortField.DATE_ADDED -> ItemSortBy.DATE_CREATED
    }
    private fun SortDirection.toApiSortOrder(): SortOrder = when (this) {
        SortDirection.ASCENDING -> SortOrder.ASCENDING
        SortDirection.DESCENDING -> SortOrder.DESCENDING
    }
    private fun AlbumSortField.toApiSortBy(): ItemSortBy = when (this) {
        AlbumSortField.TITLE -> ItemSortBy.NAME
        AlbumSortField.ARTIST -> ItemSortBy.ALBUM_ARTIST
        AlbumSortField.YEAR -> ItemSortBy.PRODUCTION_YEAR
        AlbumSortField.DATE_ADDED -> ItemSortBy.DATE_CREATED
    }

    private fun ArtistSortField.toApiSortBy(): ItemSortBy = when (this) {
        ArtistSortField.NAME -> ItemSortBy.NAME
        ArtistSortField.DATE_ADDED -> ItemSortBy.DATE_CREATED
    }
    private fun String?.takeIfNotBlank(): String? = this?.takeIf { it.isNotBlank() }
    private suspend fun getCountForKinds(vararg kinds: BaseItemKind): Int = withContext(Dispatchers.IO) {
        val currentApi = currentApi() ?: error("ApiClient not initialized")
        val response by currentApi.itemsApi.getItems(
            userId = getCurrentUserId(),   // now UUID?
            recursive = true,
            includeItemTypes = kinds.toList(),
            startIndex = 0,
            limit = 0
        )
        response.totalRecordCount ?: response.items.size
    }
    private fun getCurrentUserId(): UUID? = currentUserId
    private fun accessToken(): String? = apiClient?.accessToken

}
