package com.cesenahome.ui.common

import android.content.ComponentName
import android.content.Intent
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cesenahome.domain.models.song.QueueSong
import com.cesenahome.ui.R
import com.cesenahome.ui.player.PlayerActivity
import com.cesenahome.ui.player.PlayerActivityExtras
import com.cesenahome.ui.player.PlayerService
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

@OptIn(UnstableApi::class)
class NowPlayingFabController(
    private val activity: AppCompatActivity,
    private val fab: ExtendedFloatingActionButton
) : DefaultLifecycleObserver {

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private val mediaController: MediaController?
        get() = mediaControllerFuture?.takeIf { it.isDone }?.get()

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateFabState()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateFabState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateFabState()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateFabState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateFabState()
        }
    }

    init {
        fab.hide()
        fab.setOnClickListener { openPlayer() }
        activity.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val sessionToken = SessionToken(activity, ComponentName(activity, PlayerService::class.java))
        mediaControllerFuture = MediaController.Builder(activity, sessionToken).buildAsync().also { future ->
            future.addListener({
                val controller = mediaController ?: return@addListener
                controller.addListener(playerListener)
                updateFabState()
            }, MoreExecutors.directExecutor())
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        mediaController?.removeListener(playerListener)
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaControllerFuture = null
        fab.hide()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        activity.lifecycle.removeObserver(this)
    }

    private fun updateFabState() {
        fab.post {
            val controller = mediaController
            if (controller?.currentMediaItem != null) {
                val label = activity.getString(R.string.player_fab_label)
                fab.text = label
                fab.contentDescription = label
                fab.show()
            } else {
                fab.hide()
            }
        }
    }

    private fun openPlayer() {
        val controller = mediaController ?: return
        val currentItem = controller.currentMediaItem ?: return
        val queueSongs = ArrayList<QueueSong>(controller.mediaItemCount)
        for (index in 0 until controller.mediaItemCount) {
            queueSongs += controller.getMediaItemAt(index).toQueueSong()
        }
        val durationMs = controller.duration.takeIf { it > 0 } ?: 0L
        val currentIndex = controller.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: 0
        val intent = Intent(activity, PlayerActivity::class.java).apply {
            putExtra(PlayerActivityExtras.EXTRA_SONG_ID, currentItem.mediaId)
            putExtra(PlayerActivityExtras.EXTRA_SONG_TITLE, currentItem.mediaMetadata.title?.toString())
            putExtra(PlayerActivityExtras.EXTRA_SONG_ARTIST, currentItem.mediaMetadata.artist?.toString())
            putExtra(PlayerActivityExtras.EXTRA_SONG_ALBUM, currentItem.mediaMetadata.albumTitle?.toString())
            putExtra(PlayerActivityExtras.EXTRA_SONG_ARTWORK_URL, currentItem.mediaMetadata.artworkUri?.toString())
            putExtra(PlayerActivityExtras.EXTRA_SONG_DURATION_MS, durationMs)
            if (queueSongs.isNotEmpty()) {
                putParcelableArrayListExtra(PlayerActivityExtras.EXTRA_QUEUE_SONGS, queueSongs)
                putExtra(PlayerActivityExtras.EXTRA_QUEUE_SELECTED_INDEX, currentIndex)
            }
        }
        activity.startActivity(intent)
    }

    private fun MediaItem.toQueueSong(): QueueSong = QueueSong(
        id = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString(),
        album = mediaMetadata.albumTitle?.toString(),
        durationMs = null,
        artworkUrl = mediaMetadata.artworkUri?.toString()
    )
}
