package com.cesenahome.data.remote


import android.content.Context
import com.cesenahome.domain.models.album.AlbumPagingRequest
import com.cesenahome.domain.models.album.AlbumSortField
import com.cesenahome.domain.models.artist.ArtistPagingRequest
import com.cesenahome.domain.models.artist.ArtistSortField
import com.cesenahome.domain.models.playlist.PlaylistPagingRequest
import com.cesenahome.domain.models.playlist.PlaylistSortField
import com.cesenahome.domain.models.song.SortDirection
import com.cesenahome.domain.models.song.SongPagingRequest
import com.cesenahome.domain.models.song.SongSortField
import com.cesenahome.domain.models.login.User
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
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlaylistCreationResult
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFilter

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

            updateAuthenticatedUser(auth.user?.id)

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

    suspend fun fetchPlaylists(startIndex: Int, limit: Int, request: PlaylistPagingRequest): List<BaseItemDto> = withContext(Dispatchers.IO) {
        val currentApi = currentApi() ?: error("ApiClient not initialized")
        val response by currentApi.itemsApi.getItems(        //playlistApi.getPlaylist has not been used as it would return a single item
            userId = getCurrentUserId(),
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
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
        val response by currentApi.itemsApi.getItems(       //think about implemting it with artistApi.getAlbumArtist or any other didicated method
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

    suspend fun findPlaylistByName(name: String): Result<BaseItemDto?> = withContext(Dispatchers.IO) {
        val api = currentApi() ?: return@withContext Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = getCurrentUserId() ?: return@withContext Result.failure(IllegalStateException("No authenticated user"))

        runCatching {
            val response by api.itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                searchTerm = name,
                recursive = true,
                limit = 25
            )
            response.items.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }

    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): Result<String> = withContext(Dispatchers.IO) {
        val api = currentApi() ?: return@withContext Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = getCurrentUserId() ?: return@withContext Result.failure(IllegalStateException("No authenticated user"))
        val itemIds = songIds.mapNotNull { parseUuidOrNull(it) }

        runCatching {
            val response by api.playlistsApi.createPlaylist(
                data = CreatePlaylistDto(
                    name = name,
                    ids = itemIds,
                    userId = userId,
                    mediaType = null,
                    users = emptyList(),
                    isPublic = false,
                )
            )
            extractPlaylistId(response)
        }
    }

    suspend fun fetchFavouriteSongIds(): Result<List<String>> = withContext(Dispatchers.IO) {
        val api = currentApi() ?: return@withContext Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = getCurrentUserId() ?: return@withContext Result.failure(IllegalStateException("No authenticated user"))

        runCatching {
            val response by api.itemsApi.getItems(
                userId = userId,
                includeItemTypes = listOf(BaseItemKind.AUDIO),
                filters = listOf(ItemFilter.IS_FAVORITE),
                recursive = true,
                limit = 2000,
            )
            response.items.mapNotNull { it.id?.toString() }
        }
    }

    suspend fun fetchPlaylistSongIds(playlistId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val api = currentApi() ?: return@withContext Result.failure(IllegalStateException("ApiClient not initialized"))
        val playlistUuid = parseUuidOrNull(playlistId)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid playlist identifier"))
        val userId = getCurrentUserId() ?: return@withContext Result.failure(IllegalStateException("No authenticated user"))

        runCatching {
            val response by api.playlistsApi.getPlaylistItems(
                playlistId = playlistUuid,
                userId = userId,
            )
            response.items.orEmpty().mapNotNull { it.id?.toString() }
        }
    }

    suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        if (songIds.isEmpty()) {
            return@withContext Result.success(Unit)
        }

        val api = currentApi() ?: return@withContext Result.failure(IllegalStateException("ApiClient not initialized"))
        val userId = getCurrentUserId() ?: return@withContext Result.failure(IllegalStateException("No authenticated user"))
        val playlistUuid = parseUuidOrNull(playlistId) ?: return@withContext Result.failure(IllegalArgumentException("Invalid playlist identifier"))
        val itemIds = songIds.mapNotNull { parseUuidOrNull(it) }
        if (itemIds.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No valid song identifiers provided"))
        }

        runCatching {
            api.playlistsApi.addItemToPlaylist(
                playlistId = playlistUuid,
                userId = userId,
                ids = itemIds
            )
            Unit
        }
    }

    suspend fun removeSongsFromPlaylist(playlistId: String, songIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        if (songIds.isEmpty()) {
            return@withContext Result.success(Unit)
        }

        val api = currentApi() ?: return@withContext Result.failure(IllegalStateException("ApiClient not initialized"))
        val playlistUuid = parseUuidOrNull(playlistId)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid playlist identifier"))
        val userId = getCurrentUserId() ?: return@withContext Result.failure(IllegalStateException("No authenticated user"))

        val targetItemIds = songIds.mapNotNull { parseUuidOrNull(it) }.toSet()
        if (targetItemIds.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No valid song identifiers provided"))
        }

        val playlistItems = runCatching {
            val response by api.playlistsApi.getPlaylistItems(
                playlistId = playlistUuid,
                userId = userId,
            )
            response.items.orEmpty()
        }.getOrElse { error ->
            return@withContext Result.failure(error)
        }

        val entryIds = playlistItems
            .filter { item -> item.id != null && targetItemIds.contains(item.id) }
            .mapNotNull { it.playlistItemId }

        if (entryIds.isEmpty()) {
            return@withContext Result.success(Unit)
        }

        runCatching {
            api.playlistsApi.removeItemFromPlaylist(
                playlistId = playlistUuid.toString(),
                entryIds = entryIds
            )
            Unit
        }
    }

    suspend fun setAsFavourite(songId: String, isFavourite: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val api = currentApi() ?: return@withContext Result.failure(IllegalStateException("ApiClient not initialized"))
        val songUuid = parseUuidOrNull(songId) ?: return@withContext Result.failure(IllegalArgumentException("Invalid song identifier"))
        val userUuid = getCurrentUserId() ?: return@withContext Result.failure(IllegalStateException("No authenticated user"))

        runCatching {
            if (isFavourite) {
                api.userLibraryApi.markFavoriteItem(
                    itemId = songUuid,
                    userId = userUuid,
                )
            } else {
                api.userLibraryApi.unmarkFavoriteItem(
                    itemId = songUuid,
                    userId = userUuid,
                )
            }
            Unit
        }
    }
    fun clearSession() {
        apiClient?.update(accessToken = null)
        updateAuthenticatedUser(null)
    }
    fun updateAuthenticatedUser(userId: UUID?) {
        currentUserId = userId
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
    private fun PlaylistSortField.toApiSortBy(): ItemSortBy = when (this) {
        PlaylistSortField.NAME -> ItemSortBy.NAME
        PlaylistSortField.DATE_ADDED -> ItemSortBy.DATE_CREATED
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
    private fun extractPlaylistId(result: PlaylistCreationResult): String {
        return result.id ?: throw IllegalStateException("Playlist creation returned no identifier")
    }
}
