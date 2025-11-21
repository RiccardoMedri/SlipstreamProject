package com.cesenahome.ui.player

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.ui.download.DownloadDependenciesRegistry
import com.cesenahome.domain.models.song.Song
import com.cesenahome.ui.player.PlayerServiceConfig.CACHE_SIZE
import com.cesenahome.ui.player.PlayerServiceConfig.CHANNEL_ID
import com.cesenahome.ui.player.PlayerServiceConfig.SEEK_BACK_MS
import com.cesenahome.ui.player.PlayerServiceConfig.SEEK_FORWARD_MS
import com.cesenahome.ui.player.PlayerServiceConfig.SESSION_ID
import com.cesenahome.ui.player.PlayerServiceConfig.SHUFFLE_BUFFER_TARGET
import com.cesenahome.ui.player.PlayerServiceConfig.SHUFFLE_HISTORY_LIMIT
import com.cesenahome.ui.player.PlayerServiceConfig.SHUFFLE_PRIME_BATCH
import com.cesenahome.ui.player.PlayerServiceConfig.SHUFFLE_RANDOM_ATTEMPTS
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Collections
import java.util.LinkedHashMap
import kotlin.coroutines.cancellation.CancellationException

@UnstableApi
class PlayerService : MediaLibraryService() {

    //The single player instance for foreground/background playback
    private lateinit var player: ExoPlayer

    //The Media3 session that controllers bind to
    private var session: MediaLibrarySession? = null

    //These bridge to domain to fetch stream URLs, list pages, and random songs for shuffle
    private val resolveStreamUrlUseCase by lazy { UseCaseProvider.resolveStreamUrlUseCase }
    private val getRandomSongUseCase by lazy { UseCaseProvider.getRandomSongUseCase }

    //Lazily instatiate Media3's offline engine: queues downloads, persists their index/state
    //in a DB, runs them on a worker thread pool, and exposes APIs to query them later
    private val downloadProvider by lazy { DownloadDependenciesRegistry.requireInfra() }
    private val downloadManager: DownloadManager by lazy {
        downloadProvider.downloadManager(applicationContext)
    }

    //For async work inside callbacks; failures don’t kill siblings
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    //A tiny LRU map keyed by songId, it caches Song to enrich MediaItem metadata quickly without re-fetching
    //It’s synchronized and evicts eldest entry automatically
    private val songCache: MutableMap<String, Song> = Collections.synchronizedMap(object : LinkedHashMap<String, Song>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Song>?): Boolean {
            return size > CACHE_SIZE
        }
    })

    //It’s a Mutex used to serialize modifications to the queue while managing shuffle
    //Protects queue mutations while priming/filling the shuffle buffer
    //Only one maintenance pass can mutate the player’s timeline at a time
    //no races between rapid timeline/transition events
    private val shuffleBufferMutex = Mutex()

    //Reacts to timeline changes, item transitions, and shuffle toggles to
    //notify the library that the “Queue” node changed and keep the shuffle buffer healthy.
    private val playerListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (player.shuffleModeEnabled) {
                serviceScope.launch { maintainShuffleQueue() }
            }
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (player.shuffleModeEnabled) {
                serviceScope.launch { maintainShuffleQueue() }
            }
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) {
                serviceScope.launch { maintainShuffleQueue(forcePrime = true) }
            }
        }
    }

    //Wires player, data sources, notifications and session
    override fun onCreate() {
        super.onCreate()

        val hasNotificationPermission = hasNotificationPermission()
        if (hasNotificationPermission) {
            ensureNotificationChannel()
        }

        //Sets shared cache behind downloads and network fallback then gives this to DefaultMediaSourceFactory
        //so any media item the player loads will hit cache first and fall back to network automatically
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(downloadProvider.downloadCache(this))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())

        //Creates player and all controls
        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
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

        if (hasNotificationPermission) {
            val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .build()
            setMediaNotificationProvider(notificationProvider)
        }

        //Builds a media library with my custom callback
        //ui modules controllers connect to this
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setId(SESSION_ID)
            .build()
    }

    //Returns session. Needed by controllers
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    //If playback isn’t really active it stops it. Otherwise just pause.
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player.playWhenReady && player.playbackState != Player.STATE_IDLE) {
            player.pause()
        } else {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        player.removeListener(playerListener)
        session?.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    //Creates the playback notification channel, then registers the becoming noisy receiver
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

    //The core of the download-first, streaming-fallback strategy
    private suspend fun resolveMediaItems(mediaItems: List<MediaItem>): MutableList<MediaItem> {
        if (mediaItems.isEmpty()) return mutableListOf()
        return withContext(Dispatchers.IO) {
            val resolved = ArrayList<MediaItem>(mediaItems.size)
            for (item in mediaItems) {
                val builder = item.buildUpon()

                //If the Song is cached in songCache, apply presentational metadata
                val song = songCache[item.mediaId]
                if (song != null) {
                    builder.setMediaMetadata(song.toMediaMetadata())
                }

                //If a completed download exists, apply its uri/mime/customCacheKey to the builder
                //Otherwise fallback to network stream
                val download = getCompletedDownload(item.mediaId)
                if (download != null) {
                    builder.applyDownloadRequest(download)
                } else {
                    val uri = runCatching { resolveStreamUrlUseCase(item.mediaId) }.getOrNull()
                    if (!uri.isNullOrBlank()) {
                        builder.setUri(uri)
                    }
                }
                resolved += builder.build()
            }
            resolved
        }
    }

    //Insert into songCache under lock
    private fun cacheSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        synchronized(songCache) {
            songs.forEach { song -> songCache[song.id] = song }
        }
    }

    //When shuffle is enabled, handles the queue logic
    private suspend fun maintainShuffleQueue(forcePrime: Boolean = false) {
        shuffleBufferMutex.withLock {
            if (!player.shuffleModeEnabled) return
            if (forcePrime) {
                addRandomSongs(SHUFFLE_PRIME_BATCH)
            }

            //Count upcoming items; if fewer than SHUFFLE_BUFFER_TARGET, fetch random songs and append.
            val upcoming = countUpcomingItems()
            if (upcoming < SHUFFLE_BUFFER_TARGET) {
                addRandomSongs(SHUFFLE_BUFFER_TARGET - upcoming)
            }

            //Trim history so the queue doesn’t grow unbounded
            trimShuffleHistoryLocked()
        }
    }

    //Repeatedly calls getRandomSongUseCase(), skips duplicates present in the queue,
    //caches metadata, resolves items and appends to the player
    private suspend fun addRandomSongs(targetCount: Int) {
        var added = 0
        var attempts = 0
        while (added < targetCount && attempts < SHUFFLE_RANDOM_ATTEMPTS) {
            attempts++
            val song = getRandomSongUseCase().getOrNull()
            if (song == null) continue
            if (queueContainsMediaId(song.id)) continue
            cacheSongs(listOf(song))
            val resolved = resolveMediaItems(listOf(song.toMediaItem()))
            if (resolved.isEmpty()) continue
            player.addMediaItems(resolved)
            added += resolved.size
        }
    }

    //Removes items at the front if they are far behind the current index
    private fun trimShuffleHistoryLocked() {
        if (!player.shuffleModeEnabled) return
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0) return
        val removable = currentIndex - SHUFFLE_HISTORY_LIMIT
        if (removable <= 0) return
        repeat(removable.coerceAtMost(player.mediaItemCount)) {
            if (player.mediaItemCount == 0) return
            player.removeMediaItem(0)
        }
    }

    private fun countUpcomingItems(): Int {
        if (player.mediaItemCount == 0) return 0
        val currentIndex = player.currentMediaItemIndex
        return if (currentIndex < 0) {
            player.mediaItemCount
        } else {
            (player.mediaItemCount - currentIndex - 1).coerceAtLeast(0)
        }
    }

    //Translates song id to the request id
    //Queries the shared downloadIndex, returns only if STATE_COMPLETED
    private suspend fun getCompletedDownload(mediaId: String): Download? {
        if (mediaId.isBlank()) return null
        val requestId = requestIdForSong(mediaId)
        val download = withContext(Dispatchers.IO) {
            try {
                downloadManager.downloadIndex.getDownload(requestId)
            } catch (ioe: IOException) {
                null
            }
        }
        return download?.takeIf { it.state == Download.STATE_COMPLETED }
    }

    private fun requestIdForSong(mediaId: String): String = "song:$mediaId"

    private fun queueContainsMediaId(mediaId: String): Boolean {
        for (index in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(index).mediaId == mediaId) {
                return true
            }
        }
        return false
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        //Grants default player/session commands + explicitly allows shuffle, repeat, and seek-to-item
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

        //Before the player sees items, the service resolves each to a playable item
        //and returns those to Media3; this is where download-or-stream decisions are applied.
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
}