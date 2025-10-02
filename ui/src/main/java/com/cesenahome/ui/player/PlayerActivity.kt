package com.cesenahome.ui.player

import android.content.ComponentName
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bumptech.glide.Glide
import com.cesenahome.ui.R
import com.cesenahome.ui.databinding.ActivityPlayerBinding
import com.cesenahome.domain.models.QueueSong
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.Formatter
import java.util.Locale

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private val mediaController: MediaController? get() = mediaControllerFuture?.takeIf { it.isDone }?.get()
    private var currentSongId: String? = null
    private var isSeeking = false
    private val progressUpdater = object : Runnable {
        override fun run() {
            mediaController?.let {
                if (it.isConnected && !isSeeking) {
                    updateProgress(it.currentPosition, it.duration)
                }
            }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                binding.root.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }
    companion object {
        const val EXTRA_SONG_ID = "extra_song_id"
        const val EXTRA_SONG_TITLE = "extra_song_title"
        const val EXTRA_SONG_ARTIST = "extra_song_artist"
        const val EXTRA_SONG_ALBUM = "extra_song_album"
        const val EXTRA_SONG_ARTWORK_URL = "extra_song_artwork_url"
        const val EXTRA_SONG_DURATION_MS = "extra_song_duration_ms"
        const val EXTRA_QUEUE_SONGS = "extra_queue_songs"
        const val EXTRA_QUEUE_SELECTED_INDEX = "extra_queue_selected_index"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        private const val BUTTON_DISABLED_ALPHA = 0.4f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve initial song details from intent
        currentSongId = intent.getStringExtra(EXTRA_SONG_ID)
        val title = intent.getStringExtra(EXTRA_SONG_TITLE)
        val artist = intent.getStringExtra(EXTRA_SONG_ARTIST)
        val album = intent.getStringExtra(EXTRA_SONG_ALBUM)
        val artworkUrl = intent.getStringExtra(EXTRA_SONG_ARTWORK_URL)
        val durationMs = intent.getLongExtra(EXTRA_SONG_DURATION_MS, 0)

        binding.titleTextView.text = title
        binding.artistTextView.text = artist
        artworkUrl?.let {
            Glide.with(this).load(it.toUri()).into(binding.artworkImageView)
        }
        binding.totalDurationTextView.text = formatDuration(durationMs)
        binding.seekBar.max = durationMs.toInt()

        setupClickListeners()
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener(
            {
                val controller = this.mediaController ?: return@addListener
                controller.addListener(playerListener)
                // If the intent song is different from current or no song is playing,
                // set the new media item and play.
                if (controller.currentMediaItem?.mediaId != currentSongId && currentSongId != null) {
                    playNewSongFromIntent(controller)
                } else {
                    // Otherwise, just update UI with current state from service
                    updateUiWithCurrentMediaItem(controller.mediaItemCount > 0)
                    updatePlayPauseButton(controller.isPlaying)
                    updateProgress(controller.currentPosition, controller.duration)
                }
                updateShuffleButton(controller.shuffleModeEnabled)
                updateRepeatButton(controller.repeatMode)
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        mediaController?.removeListener(playerListener)
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaControllerFuture = null
        super.onStop()
    }

    private fun playNewSongFromIntent(controller: MediaController) {
        val songId = intent.getStringExtra(EXTRA_SONG_ID) ?: return
        val title = intent.getStringExtra(EXTRA_SONG_TITLE)
        val artist = intent.getStringExtra(EXTRA_SONG_ARTIST)
        val album = intent.getStringExtra(EXTRA_SONG_ALBUM)
        val artworkUrl = intent.getStringExtra(EXTRA_SONG_ARTWORK_URL)

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUrl?.toUri())
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(songId)
            .setMediaMetadata(metadata)
            .build()

        val queueSongs = intent.getParcelableArrayListExtra<QueueSong>(EXTRA_QUEUE_SONGS)
        if (!queueSongs.isNullOrEmpty()) {
            val mediaItems = queueSongs.map { it.toMediaItem() }
            val startIndex = queueSongs.indexOfFirst { it.id == songId }
                .takeIf { it >= 0 }
                ?: intent.getIntExtra(EXTRA_QUEUE_SELECTED_INDEX, 0)
                    .coerceIn(0, mediaItems.lastIndex)
            controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        } else {
            controller.setMediaItem(mediaItem)
        }
        controller.prepare()
        controller.play()
    }
    private fun setupClickListeners() {
        binding.playPauseButton.setOnClickListener {
            mediaController?.let {
                if (it.isPlaying) {
                    it.pause()
                } else {
                    it.play()
                }
            }
        }
        binding.nextButton.setOnClickListener { mediaController?.seekToNextMediaItem() }
        binding.previousButton.setOnClickListener { mediaController?.seekToPreviousMediaItem() }
        binding.restartButton.setOnClickListener {
            mediaController?.let {
                it.seekToDefaultPosition()
                it.play()
            }
        }
        binding.shuffleButton.setOnClickListener {
            mediaController?.let {
                val enabled = !it.shuffleModeEnabled
                it.shuffleModeEnabled = enabled
                updateShuffleButton(enabled)
            }
        }
        binding.repeatButton.setOnClickListener {
            mediaController?.let {
                val nextMode = nextRepeatMode(it.repeatMode)
                it.repeatMode = nextMode
                updateRepeatButton(nextMode)
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.currentTimeTextView.text = formatDuration(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                mediaController?.seekTo(seekBar?.progress?.toLong() ?: 0)
                isSeeking = false
            }
        })
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            binding.titleTextView.text = mediaMetadata.title
            binding.artistTextView.text = mediaMetadata.artist
            // binding.albumTextView.text = mediaMetadata.albumTitle // If you add an album TextView
            Glide.with(this@PlayerActivity)
                .load(mediaMetadata.artworkUri)
                .into(binding.artworkImageView)
            currentSongId = mediaController?.currentMediaItem?.mediaId
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseButton(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Can be used to show loading indicators, error messages etc.
            // For example, if playbackState == Player.STATE_BUFFERING
            updateProgress(mediaController?.currentPosition ?: 0, mediaController?.duration ?: 0)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateShuffleButton(shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateRepeatButton(repeatMode)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            // Called when player jumps to a new item or position (e.g. seekToNext, seekToPrevious, seekTo)
            updateProgress(newPosition.positionMs, mediaController?.duration ?: 0)
            updateUiWithCurrentMediaItem(mediaController?.mediaItemCount ?: 0 > 0)
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.playPauseButton.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun updateProgress(currentPositionMs: Long, durationMs: Long) {
        if (!isSeeking) {
            binding.seekBar.progress = currentPositionMs.toInt()
        }
        binding.currentTimeTextView.text = formatDuration(currentPositionMs)
        binding.totalDurationTextView.text = formatDuration(durationMs)
        binding.seekBar.max = durationMs.toInt().coerceAtLeast(0)
    }

    private fun updateUiWithCurrentMediaItem(hasMediaItem: Boolean) {
        if (hasMediaItem) {
            mediaController?.currentMediaItem?.mediaMetadata?.let { metadata ->
                binding.titleTextView.text = metadata.title
                binding.artistTextView.text = metadata.artist
                Glide.with(this@PlayerActivity)
                    .load(metadata.artworkUri)
                    .into(binding.artworkImageView)
                binding.totalDurationTextView.text = formatDuration(mediaController?.duration ?: 0)
                binding.seekBar.max = (mediaController?.duration ?: 0).toInt().coerceAtLeast(0)
                mediaController?.let {
                    updateShuffleButton(it.shuffleModeEnabled)
                    updateRepeatButton(it.repeatMode)
                }
            }
        } else {
            // Clear UI if no media item (e.g., queue ended)
            binding.titleTextView.text = "-"
            binding.artistTextView.text = "-"
            binding.currentTimeTextView.text = formatDuration(0)
            binding.totalDurationTextView.text = formatDuration(0)
            binding.seekBar.progress = 0
            binding.seekBar.max = 0
        }
        binding.playPauseButton.isEnabled = hasMediaItem
        binding.seekBar.isEnabled = hasMediaItem
        binding.restartButton.isEnabled = hasMediaItem
        binding.shuffleButton.isEnabled = hasMediaItem
        binding.repeatButton.isEnabled = hasMediaItem
        if (!hasMediaItem) {
            updateShuffleButton(false)
            updateRepeatButton(Player.REPEAT_MODE_OFF)
        }
        val controller = mediaController
        binding.previousButton.alpha = if (controller?.hasPreviousMediaItem() == true) 1f else BUTTON_DISABLED_ALPHA
        binding.nextButton.alpha = if (controller?.hasNextMediaItem() == true) 1f else BUTTON_DISABLED_ALPHA
        binding.previousButton.isEnabled = controller?.hasPreviousMediaItem() == true
        binding.nextButton.isEnabled = controller?.hasNextMediaItem() == true
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val formatter = Formatter(Locale.getDefault())
        return formatter.format("%02d:%02d", minutes, seconds).toString()
    }

    override fun onResume() {
        super.onResume()
        // Periodic UI updates for progress bar, especially if player was active in background
        binding.root.post(progressUpdater)
    }

    override fun onPause() {
        binding.root.removeCallbacks(progressUpdater)
        super.onPause()
    }

    private fun updateShuffleButton(enabled: Boolean) {
        binding.shuffleButton.setImageResource(R.drawable.ic_shuffle)
        binding.shuffleButton.alpha = if (enabled) 1f else BUTTON_DISABLED_ALPHA
        binding.shuffleButton.contentDescription = getString(
            if (enabled) R.string.cd_shuffle_on else R.string.cd_shuffle_off
        )
    }
    private fun updateRepeatButton(repeatMode: Int) {
        val (icon, description, alpha) = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> Triple(R.drawable.ic_repeat_one, R.string.cd_repeat_one, 1f)
            Player.REPEAT_MODE_ALL -> Triple(R.drawable.ic_repeat, R.string.cd_repeat_all, 1f)
            else -> Triple(R.drawable.ic_repeat, R.string.cd_repeat_off, BUTTON_DISABLED_ALPHA)
        }
        binding.repeatButton.setImageResource(icon)
        binding.repeatButton.alpha = alpha
        binding.repeatButton.contentDescription = getString(description)
    }
    private fun nextRepeatMode(currentMode: Int): Int = when (currentMode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }
}
