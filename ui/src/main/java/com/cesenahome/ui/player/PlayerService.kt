package com.cesenahome.ui.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.core.net.toUri 
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.Song 
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@UnstableApi
class PlayerService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private val resolveStreamUrlUseCase by lazy { UseCaseProvider.resolveStreamUrlUseCase }
    private val getSimpleSongsListUseCase by lazy { UseCaseProvider.getSimpleSongsListUseCase } // Correctly initialized


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
                val rootMediaItem = MediaItem.Builder()
                    .setMediaId(ROOT_ID)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("Slipstream Library")
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

                val mediaItemsFuture: ListenableFuture<ImmutableList<MediaItem>> = 
                    Futures.submitAsync(
                        {
                            val items = runBlocking(Dispatchers.IO) { 
                                when (parentId) {
                                    ROOT_ID -> {
                                        ImmutableList.of(
                                            browsableNode(NODE_ID_QUEUE, "Current Queue", false),
                                            browsableNode(NODE_ID_ALL_SONGS, "All Songs", false)
                                        )
                                    }
                                    NODE_ID_QUEUE -> {
                                        val queueItems = snapshotQueueAsItems()
                                        paginate(queueItems, page, pageSize)
                                    }
                                    NODE_ID_ALL_SONGS -> {
                                        val songsResult = getSimpleSongsListUseCase(page, pageSize)
                                        if (songsResult.isSuccess) {
                                            songsResult.getOrNull()?.map { songToMediaItem(it) }?.let {
                                                ImmutableList.copyOf(it)
                                            } ?: ImmutableList.of()
                                        } else {
                                            ImmutableList.of()
                                        }
                                    }
                                    else -> ImmutableList.of()
                                }
                            }
                            Futures.immediateFuture(items)
                        },
                        MoreExecutors.directExecutor()
                    )

                return Futures.transform(mediaItemsFuture, 
                    { items -> LibraryResult.ofItemList(items ?: ImmutableList.of(), params) }, 
                    MoreExecutors.directExecutor()
                )
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

    private fun browsableNode(id: String, title: String, playable: Boolean): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(playable)
                    .build()
            )
            .build()
    }
    
    private fun songToMediaItem(song: Song): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setIsPlayable(true) // Songs are playable
            .setIsBrowsable(false) // Individual songs are typically not browsable folders
        song.artworkUrl?.let { metadataBuilder.setArtworkUri(it.toUri()) }
        // You could also set MediaMetadata.Builder#setFolderType(MediaMetadata.FOLDER_TYPE_NONE)

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun snapshotQueueAsItems(): List<MediaItem> {
        if (player.mediaItemCount <= 0) return emptyList()
        val list = ArrayList<MediaItem>(player.mediaItemCount)
        for (i in 0 until player.mediaItemCount) {
            list.add(player.getMediaItemAt(i))
        }
        return list
    }

    private fun paginate(items: List<MediaItem>, page: Int, pageSize: Int): ImmutableList<MediaItem> {
        if (items.isEmpty() || pageSize <= 0) return ImmutableList.copyOf(items)
        val start = page.coerceAtLeast(0) * pageSize
        if (start >= items.size) return ImmutableList.of()
        val endExclusive = (start + pageSize).coerceAtMost(items.size)
        return ImmutableList.copyOf(items.subList(start, endExclusive))
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val ROOT_ID = "root_id"
        private const val NODE_ID_QUEUE = "node_id_queue"
        private const val NODE_ID_ALL_SONGS = "node_id_all_songs"
    }
}
