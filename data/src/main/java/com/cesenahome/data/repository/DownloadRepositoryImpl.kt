package com.cesenahome.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.cesenahome.data.download.DownloadComponents
import com.cesenahome.data.download.DownloadMetadataStore
import com.cesenahome.data.download.SlipstreamDownloadService
import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.data.remote.toSong
import com.cesenahome.domain.models.song.SongPagingRequest
import com.cesenahome.domain.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

@UnstableApi
class DownloadRepositoryImpl(
    context: Context,
    private val jellyfinApiClient: JellyfinApiClient,
) : DownloadRepository {

    private val appContext = context.applicationContext
    private val downloadManager: DownloadManager = DownloadComponents.getDownloadManager(appContext)
    private val metadataStore = DownloadMetadataStore(appContext)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadedSongsState = MutableStateFlow<Set<String>>(emptySet())

    private val albumIdsState: StateFlow<Set<String>> = combine(
        metadataStore.albumSongsFlow,
        downloadedSongsState,
    ) { albumSongs, downloadedSongs ->
        albumSongs.filterValues { songs -> songs.isNotEmpty() && songs.all(downloadedSongs::contains) }
            .keys
            .toSet()
    }.stateIn(repositoryScope, SharingStarted.Eagerly, emptySet())

    private val playlistIdsState: StateFlow<Set<String>> = combine(
        metadataStore.playlistSongsFlow,
        downloadedSongsState,
    ) { playlistSongs, downloadedSongs ->
        playlistSongs.filterValues { songs -> songs.isNotEmpty() && songs.all(downloadedSongs::contains) }
            .keys
            .toSet()
    }.stateIn(repositoryScope, SharingStarted.Eagerly, emptySet())

    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            repositoryScope.launch { refreshDownloadedSongs() }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            repositoryScope.launch { refreshDownloadedSongs() }
        }
    }

    init {
        downloadManager.addListener(downloadListener)
        repositoryScope.launch { refreshDownloadedSongs() }
    }

    override val downloadedSongIds: Flow<Set<String>> = downloadedSongsState

    override val downloadedAlbumIds: Flow<Set<String>> = albumIdsState

    override val downloadedPlaylistIds: Flow<Set<String>> = playlistIdsState

    override suspend fun downloadAlbum(albumId: String): Result<Unit> =
        downloadCollection(CollectionType.ALBUM, albumId)

    override suspend fun removeAlbumDownload(albumId: String): Result<Unit> =
        removeCollection(CollectionType.ALBUM, albumId)

    override suspend fun downloadPlaylist(playlistId: String): Result<Unit> =
        downloadCollection(CollectionType.PLAYLIST, playlistId)

    override suspend fun removePlaylistDownload(playlistId: String): Result<Unit> =
        removeCollection(CollectionType.PLAYLIST, playlistId)

    private suspend fun downloadCollection(type: CollectionType, collectionId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val songs = fetchAllSongs(type, collectionId)
                if (songs.isEmpty()) {
                    error("No songs found for ${type.name.lowercase()} $collectionId")
                }
                val songIds = songs.map { it.id }.toSet()
                when (type) {
                    CollectionType.ALBUM -> metadataStore.setAlbumSongs(collectionId, songIds)
                    CollectionType.PLAYLIST -> metadataStore.setPlaylistSongs(collectionId, songIds)
                }
                songs.forEach { song ->
                    enqueueDownload(song, type, collectionId)
                }
            }
        }

    private suspend fun removeCollection(type: CollectionType, collectionId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val songIds = when (type) {
                    CollectionType.ALBUM -> metadataStore.getAlbumSongs(collectionId)
                    CollectionType.PLAYLIST -> metadataStore.getPlaylistSongs(collectionId)
                }
                when (type) {
                    CollectionType.ALBUM -> metadataStore.removeAlbum(collectionId)
                    CollectionType.PLAYLIST -> metadataStore.removePlaylist(collectionId)
                }
                val remainingAlbums = metadataStore.getAllAlbumEntries()
                val remainingPlaylists = metadataStore.getAllPlaylistEntries()

                songIds.forEach { songId ->
                    val stillReferenced =
                        remainingAlbums.any { (_, songs) -> songId in songs } ||
                                remainingPlaylists.any { (_, songs) -> songId in songs }
                    if (!stillReferenced) {
                        val requestId = requestIdForSong(songId)
                        DownloadService.sendRemoveDownload(
                            appContext,
                            SlipstreamDownloadService::class.java,
                            requestId,
                            /* foreground = */ false
                        )
                    }
                }

                // Update state synchronously (since we're already in a suspend function)
                refreshDownloadedSongs()

                // Make sure the block ends with Unit, not a Job
                Unit
            }
        }

    private suspend fun fetchAllSongs(type: CollectionType, collectionId: String): List<DownloadSong> {
        val pageSize = 50
        val request = when (type) {
            CollectionType.ALBUM -> SongPagingRequest(albumId = collectionId)
            CollectionType.PLAYLIST -> SongPagingRequest(playlistId = collectionId)
        }
        val result = LinkedHashMap<String, DownloadSong>()
        var startIndex = 0
        while (true) {
            val items: List<BaseItemDto> = jellyfinApiClient.fetchSongs(
                startIndex = startIndex,
                limit = pageSize,
                request = request,
            )
            if (items.isEmpty()) break
            items.forEach { item ->
                val song = item.toSong(jellyfinApiClient)
                val url = jellyfinApiClient.getAudio(song.id)
                if (!url.isNullOrBlank()) {
                    result[song.id] = DownloadSong(song.id, url)
                }
            }
            if (items.size < pageSize) break
            startIndex += items.size
        }
        return result.values.toList()
    }

    private fun enqueueDownload(song: DownloadSong, type: CollectionType, collectionId: String) {
        val requestId = requestIdForSong(song.id)
        val existing = try {
            downloadManager.downloadIndex.getDownload(requestId)
        } catch (ioe: IOException) {
            null
        }
        if (existing != null) {
            return
        }
        val request = DownloadRequest.Builder(requestId, song.uri)
            .setMimeType(MimeTypes.AUDIO_UNKNOWN)
            .setCustomCacheKey(song.id)
            .setData(buildRequestMetadata(type, collectionId))
            .build()
        DownloadService.sendAddDownload(
            appContext,
            SlipstreamDownloadService::class.java,
            request,
            /* foreground= */ false
        )
    }

    private fun buildRequestMetadata(type: CollectionType, collectionId: String): ByteArray {
        val payload = "type=${type.name.lowercase()}&collectionId=$collectionId"
        return payload.toByteArray(StandardCharsets.UTF_8)
    }

    private suspend fun refreshDownloadedSongs() {
        val completedIds = mutableSetOf<String>()
        withContext(Dispatchers.IO) {
            val cursor = downloadManager.downloadIndex.getDownloads()
            cursor.use {
                while (it.moveToNext()) {
                    val download = it.download
                    if (download.state == Download.STATE_COMPLETED) {
                        parseSongId(download.request.id)?.let(completedIds::add)
                    }
                }
            }
        }
        downloadedSongsState.update { completedIds.toSet() }
    }

    private fun parseSongId(requestId: String?): String? {
        if (requestId.isNullOrBlank()) return null
        return requestId.removePrefix("song:").takeIf { it.isNotBlank() }
    }

    private fun requestIdForSong(songId: String): String = "song:$songId"

    private enum class CollectionType { ALBUM, PLAYLIST }

    private data class DownloadSong(val id: String, val url: String) {
        val uri: Uri = url.toUri()
    }
}