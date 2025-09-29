package com.cesenahome.ui.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata // Added import
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.cesenahome.domain.di.UseCaseProvider
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@UnstableApi
class MusicService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private val resolveStreamUrlUseCase by lazy { UseCaseProvider.resolveStreamUrlUseCase }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == i.action && player.isPlaying) {
                player.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
            }

        val notifProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .build()

        setMediaNotificationProvider(notifProvider)

        val callback = object : MediaLibrarySession.Callback {

            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                    .buildUpon()
                    .add(Player.COMMAND_SET_SHUFFLE_MODE)
                    .add(Player.COMMAND_SET_REPEAT_MODE)
                    .build()

                return MediaSession.ConnectionResult.accept(
                    sessionCommands,
                    playerCommands
                )
            }

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                // A single browsable root item
                val rootMediaItem = MediaItem.Builder()
                    .setMediaId("root_id")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("My Music Library")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootMediaItem, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {

                val result: ImmutableList<MediaItem> = when (parentId) {

                    // 1) The root exposes two browsable nodes
                    "root_id" -> {
                        ImmutableList.of(
                            browsableNode(
                                id = "node_now_playing",
                                title = "Now Playing",
                                playable = false
                            ),
                            browsableNode(
                                id = "node_recents",
                                title = "Recents",
                                playable = false
                            )
                        )
                    }

                    // 2) Children of "Now Playing" = current queue (paged)
                    "node_now_playing" -> {
                        val all = snapshotQueueAsItems()
                        paginate(all, page, pageSize)
                    }

                    // 3) "Recents" placeholder (empty list for now)
                    "node_recents" -> {
                        ImmutableList.of()
                    }

                    // Unknown parent → return empty list gracefully
                    else -> {
                        ImmutableList.of()
                    }
                }

                return Futures.immediateFuture(LibraryResult.ofItemList(result, params))
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                val resolved: MutableList<MediaItem> = runBlocking(Dispatchers.IO) {
                    mediaItems.map { item ->
                        val id = item.mediaId
                        val stream = runCatching { resolveStreamUrlUseCase(id) }.getOrNull()
                        if (stream.isNullOrBlank()) {
                            item
                        } else {
                            item.buildUpon()
                                .setUri(stream)
                                .build()
                        }
                    }.toMutableList()
                }
                return Futures.immediateFuture(resolved)
            }
            
            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                if (player.mediaItemCount > 0) {
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                    player.playWhenReady = true
                }
                return super.onPlaybackResumption(mediaSession, controller)
            }
        }

        session = MediaLibrarySession.Builder(this, player, callback).build()
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
        session?.release()
        player.release()
        super.onDestroy()
    }



    //HELPERS

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun browsableNode(
        id: String,
        title: String,
        playable: Boolean
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(playable) // false for folders
                    .build()
            )
            .build()
    }

    /**
     * Take a snapshot of the current queue as a list of MediaItems.
     * These MediaItems already carry metadata (title/artist/artwork) that you set in the Activity
     * when building the queue, so we can surface them to browsing clients.
     */
    private fun snapshotQueueAsItems(): List<MediaItem> {
        val count = player.mediaItemCount
        if (count <= 0) return emptyList()
        val list = ArrayList<MediaItem>(count)
        for (i in 0 until count) {
            list.add(player.getMediaItemAt(i))
        }
        return list
    }

    /**
     * Map page/pageSize into a sublist and return as ImmutableList.
     * If pageSize <= 0, return all.
     */
    private fun paginate(
        items: List<MediaItem>,
        page: Int,
        pageSize: Int
    ): ImmutableList<MediaItem> {
        if (items.isEmpty()) return ImmutableList.of()
        if (pageSize <= 0) return ImmutableList.copyOf(items)

        val start = (page.coerceAtLeast(0)) * pageSize
        if (start >= items.size) return ImmutableList.of()

        val endExclusive = (start + pageSize).coerceAtMost(items.size)
        return ImmutableList.copyOf(items.subList(start, endExclusive))
    }

    companion object {
        private const val CHANNEL_ID = "playback"
    }
}
