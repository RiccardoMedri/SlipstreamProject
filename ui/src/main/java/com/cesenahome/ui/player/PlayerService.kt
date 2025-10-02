package com.cesenahome.ui.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.Song
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.coroutines.cancellation.CancellationException

@UnstableApi
class PlayerService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private val resolveStreamUrlUseCase by lazy { UseCaseProvider.resolveStreamUrlUseCase }
    private val getSimpleSongsListUseCase by lazy { UseCaseProvider.getSimpleSongsListUseCase }
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private val songCache: MutableMap<String, Song> = Collections.synchronizedMap(object : LinkedHashMap<String, Song>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Song>?): Boolean {
                return size > CACHE_SIZE
            }
        })
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action && player.isPlaying) {
                player.pause()
            }
        }
    }
    private val playerListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            notifyQueueChildrenChanged()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            notifyQueueChildrenChanged()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        val noisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, noisyFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(noisyReceiver, noisyFilter)
        }

        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                addListener(playerListener)
            }

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .build()
        setMediaNotificationProvider(notificationProvider)

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setId(SESSION_ID)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.playbackState == Player.STATE_IDLE) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        player.removeListener(playerListener)
        session?.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun notifyQueueChildrenChanged() {
        val itemCount = player.mediaItemCount
        val librarySession = session ?: return
        serviceScope.launch {
            librarySession.notifyChildrenChanged(NODE_ID_QUEUE, itemCount, null)
        }
    }

    private fun libraryRootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(ROOT_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Slipstream Library")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()

    private fun browsableNode(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()

    private fun queueSnapshot(): List<MediaItem> {
        if (player.mediaItemCount == 0) return emptyList()
        val items = ArrayList<MediaItem>(player.mediaItemCount)
        for (index in 0 until player.mediaItemCount) {
            items += player.getMediaItemAt(index)
        }
        return items
    }

    private fun queueMediaItems(page: Int, pageSize: Int): ImmutableList<MediaItem> {
        val snapshot = queueSnapshot()
        return paginate(snapshot, page, pageSize)
    }

    private suspend fun loadLibraryPage(page: Int, pageSize: Int): ImmutableList<MediaItem> {
        val safePage = page.coerceAtLeast(0)
        val boundedPageSize = pageSize
            .takeUnless { it == Int.MAX_VALUE }
            ?.coerceAtLeast(1)
            ?.coerceAtMost(MAX_LIBRARY_PAGE_SIZE)
            ?: DEFAULT_LIBRARY_PAGE_SIZE

        val songs = runCatching { getSimpleSongsListUseCase(safePage, boundedPageSize) }.getOrNull()
            ?.getOrNull().orEmpty()
        cacheSongs(songs)
        return ImmutableList.copyOf(songs.map { it.toMediaItem() })
    }

    private suspend fun resolveMediaItems(mediaItems: List<MediaItem>): MutableList<MediaItem> {
        if (mediaItems.isEmpty()) return mutableListOf()
        return withContext(Dispatchers.IO) {
            val resolved = ArrayList<MediaItem>(mediaItems.size)
            for (item in mediaItems) {
                val builder = item.buildUpon()
                val song = songCache[item.mediaId]
                if (song != null) {
                    builder.setMediaMetadata(song.toMediaMetadata())
                }
                val uri = runCatching { resolveStreamUrlUseCase(item.mediaId) }.getOrNull()
                if (!uri.isNullOrBlank()) {
                    builder.setUri(uri)
                }
                resolved += builder.build()
            }
            resolved
        }
    }

    private suspend fun findItem(mediaId: String): MediaItem? {
        for (index in 0 until player.mediaItemCount) {
            val existing = player.getMediaItemAt(index)
            if (existing.mediaId == mediaId) {
                return existing
            }
        }
        val cachedSong = songCache[mediaId]
        if (cachedSong != null) {
            return cachedSong.toMediaItem()
        }
        val uri = withContext(Dispatchers.IO) {
            runCatching { resolveStreamUrlUseCase(mediaId) }.getOrNull()
        }
        if (uri != null) {
            return MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Unknown track")
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        }
        return null
    }

    private fun cacheSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        synchronized(songCache) {
            songs.forEach { song -> songCache[song.id] = song }
        }
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(toMediaMetadata())
        .build()

    private fun Song.toMediaMetadata(): MediaMetadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artworkUrl?.toUri())
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .build()

    private fun paginate(items: List<MediaItem>, page: Int, pageSize: Int): ImmutableList<MediaItem> {
        if (items.isEmpty()) return ImmutableList.of()
        val effectivePageSize = when {
            pageSize == Int.MAX_VALUE || pageSize <= 0 -> items.size
            else -> pageSize
        }
        val safePage = page.coerceAtLeast(0)
        val startIndex = safePage * effectivePageSize
        if (startIndex >= items.size) return ImmutableList.of()
        val endIndex = (startIndex + effectivePageSize).coerceAtMost(items.size)
        return ImmutableList.copyOf(items.subList(startIndex, endIndex))
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                .buildUpon()
                .add(Player.COMMAND_SET_SHUFFLE_MODE)
                .add(Player.COMMAND_SET_REPEAT_MODE)
                .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .build()
            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return serviceFuture {
                LibraryResult.ofItem(libraryRootItem(), params)
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceFuture {
                when (parentId) {
                    ROOT_ID -> {
                        val children = ImmutableList.of(
                            browsableNode(NODE_ID_QUEUE, "Current Queue"),
                            browsableNode(NODE_ID_ALL_SONGS, "All Songs")
                        )
                        LibraryResult.ofItemList(children, params)
                    }
                    NODE_ID_QUEUE -> LibraryResult.ofItemList(queueMediaItems(page, pageSize), params)
                    NODE_ID_ALL_SONGS -> LibraryResult.ofItemList(loadLibraryPage(page, pageSize), params)
                    else -> LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                }
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return serviceFuture {
                val item = findItem(mediaId)
                if (item != null) {
                    LibraryResult.ofItem(item, null)
                } else {
                    LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                }
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            return serviceFuture { resolveMediaItems(mediaItems) }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return serviceFuture {
                val resolved = resolveMediaItems(mediaItems)
                MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs)
            }
        }
    }

    private fun <T> serviceFuture(block: suspend () -> T): ListenableFuture<T> {
        return CallbackToFutureAdapter.getFuture { completer ->
            serviceScope.launch {
                try {
                    completer.set(block())
                } catch (error: Throwable) {
                    if (error is CancellationException) {
                        completer.setCancelled()
                    } else {
                        completer.setException(error)
                    }
                }
            }
            "PlayerService#serviceFuture"
        }
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val SESSION_ID = "slipstream_session"
        private const val ROOT_ID = "root_id"
        private const val NODE_ID_QUEUE = "node_id_queue"
        private const val NODE_ID_ALL_SONGS = "node_id_all_songs"
        private const val DEFAULT_LIBRARY_PAGE_SIZE = 50
        private const val MAX_LIBRARY_PAGE_SIZE = 200
        private const val CACHE_SIZE = 250
        private const val SEEK_BACK_MS = 10_000L
        private const val SEEK_FORWARD_MS = 30_000L
    }
}
