package com.cesenahome.ui.player

import android.content.ComponentName
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cesenahome.ui.R
import com.cesenahome.ui.databinding.ActivityPlayerBinding
import com.cesenahome.ui.databinding.DialogQueueBottomSheetBinding
import com.cesenahome.domain.models.QueueSong
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.ArrayList
import java.util.Formatter
import java.util.Locale

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private val mediaController: MediaController? get() = mediaControllerFuture?.takeIf { it.isDone }?.get()
    private var currentSongId: String? = null
    private var isSeeking = false
    private var queueDialog: BottomSheetDialog? = null
    private var queueDialogBinding: DialogQueueBottomSheetBinding? = null
    private var queueAdapter: QueueAdapter? = null
    private var queueDragHelper: ItemTouchHelper? = null
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
        private const val PROGRESS_UPDATE_INTERVAL_MS = 100L
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
                updateQueueDialog()
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        queueDialog?.dismiss()
        queueDialog = null
        queueAdapter = null
        queueDialogBinding = null
        queueDragHelper = null
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
        binding.seekBackwardButton.setOnClickListener { mediaController?.seekBack() }
        binding.seekForwardButton.setOnClickListener { mediaController?.seekForward() }
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
        binding.showQueueButton.setOnClickListener {
            showQueueDialog()
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
            updateQueueDialog()
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
            updateQueueDialog()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateRepeatButton(repeatMode)
            updateQueueDialog()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            // Called when player jumps to a new item or position (e.g. seekToNext, seekToPrevious, seekTo)
            updateProgress(newPosition.positionMs, mediaController?.duration ?: 0)
            updateUiWithCurrentMediaItem(mediaController?.mediaItemCount ?: 0 > 0)
            updateQueueDialog()
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
        binding.seekBackwardButton.isEnabled = hasMediaItem
        binding.seekForwardButton.isEnabled = hasMediaItem
        binding.seekBackwardButton.alpha = if (hasMediaItem) 1f else BUTTON_DISABLED_ALPHA
        binding.seekForwardButton.alpha = if (hasMediaItem) 1f else BUTTON_DISABLED_ALPHA
        val queueEnabled = mediaController != null
        binding.showQueueButton.isEnabled = queueEnabled
        binding.showQueueButton.alpha = if (queueEnabled) 1f else BUTTON_DISABLED_ALPHA
        if (!hasMediaItem) {
            updateShuffleButton(false)
            updateRepeatButton(Player.REPEAT_MODE_OFF)
        }
        val controller = mediaController
        binding.previousButton.alpha = if (controller?.hasPreviousMediaItem() == true) 1f else BUTTON_DISABLED_ALPHA
        binding.nextButton.alpha = if (controller?.hasNextMediaItem() == true) 1f else BUTTON_DISABLED_ALPHA
        binding.previousButton.isEnabled = controller?.hasPreviousMediaItem() == true
        binding.nextButton.isEnabled = controller?.hasNextMediaItem() == true
        updateQueueDialog()
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

    private fun showQueueDialog() {
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(this, R.string.queue_action_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        if (queueDialog?.isShowing == true) {
            return
        }

        val binding = DialogQueueBottomSheetBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(binding.root)

        val initialSongs = controller.buildQueueSongs()
        val adapter = QueueAdapter(initialSongs, controller.currentMediaItem?.mediaId)

        binding.queueRecycler.layoutManager = LinearLayoutManager(this)
        binding.queueRecycler.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            private var dragFrom = RecyclerView.NO_POSITION
            private var dragTo = RecyclerView.NO_POSITION

            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                if (dragFrom == RecyclerView.NO_POSITION) {
                    dragFrom = from
                }
                dragTo = to
                adapter.onItemMove(from, to)
                return true
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (dragFrom != RecyclerView.NO_POSITION && dragTo != RecyclerView.NO_POSITION && dragFrom != dragTo) {
                    controller.moveMediaItem(dragFrom, dragTo)
                    updateQueueDialog()
                }
                dragFrom = RecyclerView.NO_POSITION
                dragTo = RecyclerView.NO_POSITION
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Swipe not supported
            }
        })

        queueDragHelper = itemTouchHelper
        itemTouchHelper.attachToRecyclerView(binding.queueRecycler)
        adapter.setDragStarter { viewHolder -> queueDragHelper?.startDrag(viewHolder) }

        queueDialog = dialog
        queueDialogBinding = binding
        queueAdapter = adapter
        updateQueueDialog()

        dialog.setOnDismissListener {
            queueDialog = null
            queueAdapter = null
            queueDialogBinding = null
            queueDragHelper = null
        }

        dialog.show()
    }

    private fun updateQueueDialog() {
        val binding = queueDialogBinding ?: return
        val adapter = queueAdapter ?: return
        val controller = mediaController ?: return
        val queueSongs = controller.buildQueueSongs()
        adapter.updateItems(queueSongs, controller.currentMediaItem?.mediaId)
        val hasItems = queueSongs.isNotEmpty()
        binding.queueRecycler.isVisible = hasItems
        binding.dragHint.isVisible = queueSongs.size > 1
        binding.emptyText.isVisible = !hasItems
    }

    private fun QueueSong.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUrl?.toUri())
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()

    private fun MediaController.buildQueueSongs(): List<QueueSong> {
        val items = ArrayList<QueueSong>(mediaItemCount)
        for (index in 0 until mediaItemCount) {
            items += getMediaItemAt(index).toQueueSong()
        }
        return items
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
