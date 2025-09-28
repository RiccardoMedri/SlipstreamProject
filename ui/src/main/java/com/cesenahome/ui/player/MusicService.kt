package com.cesenahome.ui.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.media3.common.MediaItem
// import androidx.media3.common.MimeTypes // Removed this import
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.cesenahome.domain.di.UseCaseProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Media3 1.8 MediaLibraryService living in the :player module.
 * - Depends ONLY on :domain via UseCaseProvider.resolveStreamUrlUseCase
 * - Resolves mediaId -> stream Uri inside onAddMediaItems(...)
 */
@UnstableApi
class MusicService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private val resolveStreamUrlUseCase by lazy { UseCaseProvider.resolveStreamUrlUseCase }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()

        // 1) Create the player
        player = ExoPlayer.Builder(this).build()

        // 2) Provide a notification provider TO THE SERVICE (not the builder)
        //    (We keep defaults to avoid module resource lookups.)
        val notifProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .build()
        setMediaNotificationProvider(notifProvider)

        // 3) Session callback: use ListenableFuture signature in 1.8
        val callback = object : MediaLibrarySession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                // Bridge suspend use case to this sync API via runBlocking
                val resolved: MutableList<MediaItem> = runBlocking(Dispatchers.IO) {
                    mediaItems.map { item ->
                        val id = item.mediaId
                        val stream = runCatching { resolveStreamUrlUseCase(id) }.getOrNull()
                        if (stream.isNullOrBlank()) {
                            item // unchanged; playback will fail gracefully if truly unresolved
                        } else {
                            item.buildUpon()
                                // .setMimeType(MimeTypes.A) // Line removed
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
                // Default implementation from MediaLibraryService.Callback.
                // You might need to customize this based on your app's specific needs,
                // for example, by fetching the last played media item from a persistent store.
                player.prepare()
                player.play()
                return super.onPlaybackResumption(mediaSession, controller)
            }
        }

        // 4) Build the MediaLibrarySession
        session = MediaLibrarySession.Builder(this, player, callback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Stop service if not actively playing
        if (!player.playWhenReady || player.playbackState == ExoPlayer.STATE_IDLE) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
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
                    "Playback", // keep simple; or provide from :player/res/values/strings.xml
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "playback"
    }
}
