package com.cesenahome.ui.player

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
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
import com.cesenahome.domain.models.song.QueueSong
import com.cesenahome.ui.album.AlbumActivity
import com.cesenahome.ui.R
import com.cesenahome.ui.databinding.ActivityPlayerBinding
import com.cesenahome.ui.databinding.DialogQueueBottomSheetBinding
import com.cesenahome.ui.player.player_config.PlayerActivityExtras
import com.cesenahome.ui.songs.SongsActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.Formatter
import java.util.Locale

private const val PROGRESS_UPDATE_INTERVAL_MS = 100L
private const val BUTTON_DISABLED_ALPHA = 0.4f

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding

    //Holds the async result of building a MediaController
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private val mediaController: MediaController? get() = mediaControllerFuture?.takeIf { it.isDone }?.get()

    //Tracks which song the Activity believes is “current” (from the intent or the service after transitions)
    //Used to decide whether to push a new item to the service.
    private var currentSongId: String? = null
    private var currentAlbumId: String? = null
    private var currentArtistId: String? = null

    //It's a guard flag to prevent UI update while dragging the SeekBar
    private var isSeeking = false

    //These back the bottom-sheet queue UI (open, render, allow drag to reorder)
    private var queueDialog: BottomSheetDialog? = null
    private var queueDialogBinding: DialogQueueBottomSheetBinding? = null
    private var queueAdapter: QueueAdapter? = null
    private var queueDragHelper: ItemTouchHelper? = null

    //Posts itself every 100ms (while started) to refresh the elapsed/total time
    //and SeekBar position only if connected and not seeking
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

    //Reads all extras Sets quick UI defaults immediately (cover, artist, title, etc etc) from intent
    //so the screen isn’t blank while the controller binds, the actual metadata for playback
    //still comes from the service after the controller connects.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve initial song details from intent
        currentSongId = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_ID)
        val title = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_TITLE)
        val artist = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_ARTIST)
        val artistId = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_ARTIST_ID)
        val album = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_ALBUM)
        val albumId = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_ALBUM_ID)
        val artworkUrl = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_ARTWORK_URL)
        val durationMs = intent.getLongExtra(PlayerActivityExtras.EXTRA_SONG_DURATION_MS, 0)

        binding.titleTextView.text = title
        updateAlbumInfo(album, albumId)
        updateArtistInfo(artist, artistId)
        artworkUrl?.let {
            Glide.with(this).load(it.toUri()).into(binding.artworkImageView)
        }
        binding.totalDurationTextView.text = formatDuration(durationMs)
        binding.seekBar.max = durationMs.toInt()

        setupClickListeners()
    }

    //Build a session token that points at PlayerService
    //Adds a listener to the mediaController which register playerListener to receive state changes
    //Decide what to do with the initial song:
    // - If the service is playing something else or nothing, push that media item
    // - Else update UI with current state from service
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

    //Dismiss the queue dialog, remove the listener
    //and release the controller future
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

    //Builds a MediaMetadata and then a MediaItem with its specific songId
    //If the intent includes a full queue it maps each to a MediaItem
    //finds the proper start index (either the matching ID or a fallback extra)
    //and sets the media items in the player with its index
    //If it's only one item it calls setMediaItems directly
    //It ultimately prepares and plays the player
    private fun playNewSongFromIntent(controller: MediaController) {
        val songId = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_ID) ?: return
        val title = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_TITLE)
        val artist = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_ARTIST)
        val album = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_ALBUM)
        val artistId = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_ARTIST_ID)
        val albumId = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_ALBUM_ID)
        val artworkUrl = intent.getStringExtra(PlayerActivityExtras.EXTRA_SONG_ARTWORK_URL)

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUrl?.toUri())
            .setIsBrowsable(false)
            .setIsPlayable(true)

        val extras = android.os.Bundle()
        albumId?.let { extras.putString(MEDIA_METADATA_KEY_ALBUM_ID, it) }
        artistId?.let { extras.putString(MEDIA_METADATA_KEY_ARTIST_ID, it) }
        if (!extras.isEmpty) {
            metadataBuilder.setExtras(extras)
        }

        val metadata = metadataBuilder.build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(songId)
            .setMediaMetadata(metadata)
            .build()

        val queueSongs = IntentCompat.getParcelableArrayListExtra(
            intent,
            PlayerActivityExtras.EXTRA_QUEUE_SONGS,
            QueueSong::class.java
        )
        if (!queueSongs.isNullOrEmpty()) {
            val mediaItems = queueSongs.map { it.toMediaItem() }
            val startIndex = queueSongs.indexOfFirst { it.id == songId }
                .takeIf { it >= 0 }
                ?: intent.getIntExtra(PlayerActivityExtras.EXTRA_QUEUE_SELECTED_INDEX, 0)
                    .coerceIn(0, mediaItems.lastIndex)
            controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        } else {
            controller.setMediaItem(mediaItem)
        }
        controller.prepare()
        controller.play()
    }

    //Wires buttons directly to controller methods, seekbar object is defined
    //to better handle seekbar changes on start, while dragging and on stop
    //The activity never fetches directly audio data it simply sends commands to the session
    private fun setupClickListeners() {
        binding.albumTextView.setOnClickListener { openAlbumPage() }
        binding.artistTextView.setOnClickListener { openArtistPage() }
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

    //Reacts to session/player events, when:
    // - MediaMetada change: refresh metadata from the session’s current item, update currentSongId, refresh queue dialog
    // - User start/stop player: update play/pause button
    // - When playback changes state: update progress bar
    // - Shuffle command or repeat modeis toggled: Update icons and queue dialog
    // - WHen player skips: refresh progress, metadata and queue
    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            binding.titleTextView.text = mediaMetadata.title
            updateAlbumInfo(
                mediaMetadata.albumTitle,
                mediaMetadata.extras?.getString(MEDIA_METADATA_KEY_ALBUM_ID)
            )
            updateArtistInfo(
                mediaMetadata.artist,
                mediaMetadata.extras?.getString(MEDIA_METADATA_KEY_ARTIST_ID)
            )
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
            // Called when player jumps to a new item or position
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
                updateAlbumInfo(metadata.albumTitle, metadata.extras?.getString(MEDIA_METADATA_KEY_ALBUM_ID))
                updateArtistInfo(metadata.artist, metadata.extras?.getString(MEDIA_METADATA_KEY_ARTIST_ID))
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
            // Clear UI if no media item
            binding.titleTextView.text = "-"
            updateAlbumInfo(null, null)
            updateArtistInfo("-", null)
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

    // Periodic UI updates for progress bar, especially if player was active in background
    override fun onResume() {
        super.onResume()
        binding.root.post(progressUpdater)
    }

    //Stops the periodic UI updates
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

    private fun updateAlbumInfo(albumTitle: CharSequence?, albumId: String?) {
        val normalizedId = albumId?.takeIf { it.isNotBlank() }
        currentAlbumId = normalizedId
        val title = albumTitle?.toString().orEmpty()
        val hasTitle = title.isNotBlank()
        binding.albumTextView.text = title
        binding.albumTextView.isVisible = hasTitle
        val enabled = hasTitle && normalizedId != null
        binding.albumTextView.isEnabled = enabled
        binding.albumTextView.alpha = if (enabled) 1f else BUTTON_DISABLED_ALPHA
    }

    private fun updateArtistInfo(artistName: CharSequence?, artistId: String?) {
        val normalizedId = artistId?.takeIf { it.isNotBlank() }
        currentArtistId = normalizedId
        val name = artistName?.toString().orEmpty()
        val hasName = name.isNotBlank()
        binding.artistTextView.text = name
        binding.artistTextView.isVisible = hasName
        val enabled = hasName && normalizedId != null
        binding.artistTextView.isEnabled = enabled
        binding.artistTextView.alpha = if (enabled) 1f else BUTTON_DISABLED_ALPHA
    }

    private fun openAlbumPage() {
        val albumId = currentAlbumId ?: return
        val albumTitle = binding.albumTextView.text?.toString()
        val intent = Intent(this, SongsActivity::class.java).apply {
            putExtra(SongsActivity.EXTRA_ALBUM_ID, albumId)
            if (!albumTitle.isNullOrBlank()) {
                putExtra(SongsActivity.EXTRA_ALBUM_TITLE, albumTitle)
            }
        }
        startActivity(intent)
    }

    private fun openArtistPage() {
        val artistId = currentArtistId ?: return
        val artistName = binding.artistTextView.text?.toString()
        val intent = Intent(this, AlbumActivity::class.java).apply {
            putExtra(AlbumActivity.EXTRA_ARTIST_ID, artistId)
            if (!artistName.isNullOrBlank()) {
                putExtra(AlbumActivity.EXTRA_ARTIST_NAME, artistName)
            }
        }
        startActivity(intent)
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

        val layoutManager = LinearLayoutManager(this)
        binding.queueRecycler.layoutManager = layoutManager
        binding.queueRecycler.adapter = adapter

        //Scroll queue so the current song is visible at the top
        val currentMediaId = controller.currentMediaItem?.mediaId
        if (currentMediaId != null) {
            val currentIndex = initialSongs.indexOfFirst { it.id == currentMediaId }
            if (currentIndex >= 0) {
                binding.queueRecycler.post {
                    layoutManager.scrollToPositionWithOffset(currentIndex, 0)
                }
            }
        }

        //Enable drag in UP/DOWN directions, no swipe actions
        //dragFrom / dragTo hold indices of the current drag
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            private var dragFrom = RecyclerView.NO_POSITION
            private var dragTo = RecyclerView.NO_POSITION

            override fun isLongPressDragEnabled(): Boolean = false

            //Track original and target positions
            //Tell QueueAdapter to update its list so rows visually move
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

            //Called when drag ends
            //If a valid move occurred it calls moveMediaItem to reorder the real playback queue
            //Refresh queue dialog (updateQueueDialog()) and reset drag indices
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

        //Attach ItemTouchHelper to the recycler
        //setDragStarter sets a callback that when invoked triggers startDrag
        queueDragHelper = itemTouchHelper
        itemTouchHelper.attachToRecyclerView(binding.queueRecycler)
        adapter.setDragStarter { viewHolder -> queueDragHelper?.startDrag(viewHolder) }

        //Keep references so updateQueueDialog() can later refresh contents
        //Clear them when dialog is dismissed
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

    //Rebuilds queue from the controller
    //Tells QueueAdapter to update its list + current item
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
}
