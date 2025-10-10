package com.cesenahome.ui.songs

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.song.QueueSong
import com.cesenahome.domain.models.song.Song
import com.cesenahome.domain.models.song.SongSortField
import com.cesenahome.domain.models.song.SortDirection
import com.cesenahome.ui.R
import com.cesenahome.ui.common.NowPlayingFabController
import com.cesenahome.ui.common.setupSearchMenu
import com.cesenahome.ui.databinding.ActivitySongsBinding
import com.cesenahome.ui.player.PlayerActivity
import com.cesenahome.ui.player.PlayerService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SongsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongsBinding
    private lateinit var adapter: SongsAdapter
    private lateinit var nowPlayingFabController: NowPlayingFabController
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private val mediaController: MediaController?
        get() = mediaControllerFuture?.takeIf { it.isDone }?.get()
    private var downloadMenuItem: MenuItem? = null
    private val albumId: String? by lazy {
        intent.getStringExtra(EXTRA_ALBUM_ID)
    }
    private val albumTitle: String? by lazy {
        intent.getStringExtra(EXTRA_ALBUM_TITLE)
    }
    private val playlistId: String? by lazy {
        intent.getStringExtra(EXTRA_PLAYLIST_ID)
    }
    private val playlistName: String? by lazy {
        intent.getStringExtra(EXTRA_PLAYLIST_NAME)
    }
    private val viewModel: SongsViewModel by lazy {
        SongsViewModel(
            UseCaseProvider.getPagedSongsUseCase,
            UseCaseProvider.addSongToFavouritesUseCase,
            UseCaseProvider.observeDownloadedSongIdsUseCase,
            UseCaseProvider.observeDownloadedAlbumIdsUseCase,
            UseCaseProvider.observeDownloadedPlaylistIdsUseCase,
            UseCaseProvider.downloadAlbumUseCase,
            UseCaseProvider.removeAlbumDownloadUseCase,
            UseCaseProvider.downloadPlaylistUseCase,
            UseCaseProvider.removePlaylistDownloadUseCase,
            albumId,
            playlistId
        )
    }
    companion object {
        const val EXTRA_ALBUM_ID = "extra_album_id"
        const val EXTRA_ALBUM_TITLE = "extra_album_title"
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
        const val EXTRA_PLAYLIST_NAME = "extra_playlist_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySongsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        nowPlayingFabController = NowPlayingFabController(this, binding.nowPlayingFab)

        setupToolbar()
        setupList()
        observeVm()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        playlistName?.takeIf { it.isNotBlank() }?.let { binding.toolbar.title = it }
        albumTitle?.takeIf { it.isNotBlank() && playlistName.isNullOrBlank() }?.let { binding.toolbar.title = it }
        if (albumId == null && playlistId == null) {
            binding.songToolbarFilters.root.isVisible = true
            binding.toolbar.setupSearchMenu(
                queryHint = getString(R.string.search_hint_songs),
                initialQuery = viewModel.searchQuery.value,
                onQueryChanged = viewModel::onSearchQueryChanged
            )
            binding.songToolbarFilters.buttonSortField.setOnClickListener { view ->
                showSortFieldMenu(view)
            }
            binding.songToolbarFilters.buttonSortOrder.setOnClickListener { view ->
                showSortOrderMenu(view)
            }
        } else {
            binding.songToolbarFilters.root.isVisible = false
            binding.toolbar.menu.clear()
            binding.toolbar.inflateMenu(R.menu.menu_collection_download)
            downloadMenuItem = binding.toolbar.menu.findItem(R.id.action_toggle_download)
            binding.toolbar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_toggle_download) {
                    viewModel.onToggleDownloadRequested()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun setupList() {
        adapter = SongsAdapter(
            onSongClick = { song -> viewModel.onSongClicked(song) },
            onSongOptionsClick = { song, anchor -> showSongOptionsMenu(song, anchor) },
            onFavouriteClick = { song -> viewModel.onFavouriteClick(song) }
        )

        binding.recyclerSongs.layoutManager = LinearLayoutManager(this)
        binding.recyclerSongs.adapter = adapter
        binding.recyclerSongs.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        adapter.addLoadStateListener { loadStates ->
            val refresh = loadStates.refresh
            binding.progress.isVisible = refresh is LoadState.Loading
            binding.emptyView.root.isVisible =
                refresh is LoadState.NotLoading && adapter.itemCount == 0

            val err = (refresh as? LoadState.Error)
                ?: (loadStates.append as? LoadState.Error)
                ?: (loadStates.prepend as? LoadState.Error)
            err?.let {
                Toast.makeText(this, it.error.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeVm() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pagedSongs.collectLatest { pagingData: PagingData<Song> ->
                        adapter.submitData(pagingData)
                    }
                }
                launch {
                    viewModel.playCommands.collect { cmd ->
                        when (cmd) {
                            is SongsViewModel.PlayCommand.PlaySong -> launchPlayerActivity(cmd.song)
                        }
                    }
                }
                launch {
                    viewModel.sortState.collect { sortOption ->
                        updateSortButtons(sortOption.field, sortOption.direction)
                    }
                }
                launch {
                    viewModel.collectionDownloadState.collect { state ->
                        updateDownloadMenu(state)
                    }
                }
                launch {
                    viewModel.downloadEvents.collect { event ->
                        when (event) {
                            is SongsViewModel.DownloadEvent.Success -> {
                                val messageRes = if (event.downloaded) {
                                    R.string.download_started
                                } else {
                                    R.string.download_removed
                                }
                                Toast.makeText(
                                    this@SongsActivity,
                                    getString(messageRes),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is SongsViewModel.DownloadEvent.Failure -> {
                                val detail = event.reason?.takeIf { it.isNotBlank() } ?: getString(R.string.unknown)
                                Toast.makeText(
                                    this@SongsActivity,
                                    getString(R.string.download_failed, detail),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.favouriteEvents.collect { event ->
                        when (event) {
                            is SongsViewModel.FavouriteEvent.Success -> {
                                val messageRes = if (event.isFavourite) {
                                    R.string.song_favourite_add_success
                                } else {
                                    R.string.song_favourite_remove_success
                                }
                                Toast.makeText(
                                    this@SongsActivity,
                                    getString(messageRes, event.song.title),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is SongsViewModel.FavouriteEvent.Failure -> {
                                val baseMessageRes = if (event.isFavourite) {
                                    R.string.song_favourite_add_failure
                                } else {
                                    R.string.song_favourite_remove_failure
                                }
                                val baseMessage = getString(baseMessageRes)
                                val detail = event.reason?.takeIf { it.isNotBlank() }
                                val finalMessage = detail?.let { "$baseMessage: $it" } ?: baseMessage
                                Toast.makeText(this@SongsActivity, finalMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
    }

    override fun onStop() {
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaControllerFuture = null
        super.onStop()
    }

    private fun updateSortButtons(field: SongSortField, direction: SortDirection) {
        val sortLabelRes = when (field) {
            SongSortField.NAME -> R.string.songs_sort_by_name
            SongSortField.ALBUM_ARTIST -> R.string.songs_sort_by_album_artist
            SongSortField.DATE_ADDED -> R.string.songs_sort_by_date_added
        }
        val orderLabelRes = when (direction) {
            SortDirection.ASCENDING -> R.string.sort_order_ascending
            SortDirection.DESCENDING -> R.string.sort_order_descending
        }
        binding.songToolbarFilters.buttonSortField.text = getString(R.string.sort_field_label, getString(sortLabelRes))
        binding.songToolbarFilters.buttonSortOrder.text = getString(R.string.sort_order_label, getString(orderLabelRes))
        binding.songToolbarFilters.buttonSortField.contentDescription = binding.songToolbarFilters.buttonSortField.text
        binding.songToolbarFilters.buttonSortOrder.contentDescription = binding.songToolbarFilters.buttonSortOrder.text
    }

    private fun updateDownloadMenu(state: SongsViewModel.CollectionDownloadState?) {
        val item = downloadMenuItem ?: binding.toolbar.menu.findItem(R.id.action_toggle_download)?.also {
            downloadMenuItem = it
        }
        if (state == null) {
            item?.isVisible = false
            return
        }
        item?.let { menuItem ->
            menuItem.isVisible = true
            menuItem.isEnabled = !state.inProgress
            if (state.isDownloaded) {
                menuItem.setIcon(R.drawable.ic_downloaded)
                menuItem.title = getString(R.string.menu_remove_download)
            } else {
                menuItem.setIcon(R.drawable.ic_download)
                menuItem.title = getString(R.string.menu_download_collection)
            }
        }
    }
    private fun showSortFieldMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_song_sort_field, popup.menu)
        when (viewModel.sortState.value.field) {
            SongSortField.NAME -> popup.menu.findItem(R.id.sort_by_name)?.isChecked = true
            SongSortField.ALBUM_ARTIST -> popup.menu.findItem(R.id.sort_by_album_artist)?.isChecked = true
            SongSortField.DATE_ADDED -> popup.menu.findItem(R.id.sort_by_date_added)?.isChecked = true
        }
        popup.setOnMenuItemClickListener { item ->
            val selected = when (item.itemId) {
                R.id.sort_by_name -> SongSortField.NAME
                R.id.sort_by_album_artist -> SongSortField.ALBUM_ARTIST
                R.id.sort_by_date_added -> SongSortField.DATE_ADDED
                else -> null
            }
            selected?.let { field ->
                if (field != viewModel.sortState.value.field) {
                    viewModel.onSortFieldSelected(field)
                    adapter.refresh()
                }
            }
            true
        }
        popup.show()
    }

    private fun showSortOrderMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_song_sort_order, popup.menu)
        when (viewModel.sortState.value.direction) {
            SortDirection.ASCENDING -> popup.menu.findItem(R.id.order_ascending)?.isChecked = true
            SortDirection.DESCENDING -> popup.menu.findItem(R.id.order_descending)?.isChecked = true
        }
        popup.setOnMenuItemClickListener { item ->
            val selected = when (item.itemId) {
                R.id.order_ascending -> SortDirection.ASCENDING
                R.id.order_descending -> SortDirection.DESCENDING
                else -> null
            }
            selected?.let { direction ->
                if (direction != viewModel.sortState.value.direction) {
                    viewModel.onSortDirectionSelected(direction)
                    adapter.refresh()
                }
            }
            true
        }
        popup.show()
    }

    private fun showSongOptionsMenu(song: Song, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_song_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add_to_queue -> {
                    addSongToQueue(song)
                    true
                }
                R.id.action_play_next -> {
                    playSongNext(song)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun addSongToQueue(song: Song) {
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(this, R.string.queue_action_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val mediaItem = song.toMediaItem()
        if (controller.mediaItemCount == 0) {
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        } else {
            controller.addMediaItem(mediaItem)
        }
        Toast.makeText(this, R.string.queue_song_added_to_end, Toast.LENGTH_SHORT).show()
    }

    private fun playSongNext(song: Song) {
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(this, R.string.queue_action_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val mediaItem = song.toMediaItem()
        if (controller.mediaItemCount == 0) {
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        } else {
            val currentIndex = controller.currentMediaItemIndex.takeIf { it >= 0 } ?: (controller.mediaItemCount - 1)
            val insertIndex = (currentIndex + 1).coerceAtMost(controller.mediaItemCount)
            controller.addMediaItem(insertIndex, mediaItem)
        }
        Toast.makeText(this, R.string.queue_song_added_next, Toast.LENGTH_SHORT).show()
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
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


    @OptIn(UnstableApi::class)
    private fun launchPlayerActivity(song: Song) {
        val snapshotSongs = adapter.snapshot().items
        val queueSongs = ArrayList<QueueSong>(snapshotSongs.size)
        snapshotSongs.forEach { snapshotSong ->
            queueSongs += snapshotSong.toQueueSong()
        }
        val selectedIndex = queueSongs.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_SONG_ID, song.id)
            putExtra(PlayerActivity.EXTRA_SONG_TITLE, song.title)
            putExtra(PlayerActivity.EXTRA_SONG_ARTIST, song.artist)
            putExtra(PlayerActivity.EXTRA_SONG_ALBUM, song.album)
            putExtra(PlayerActivity.EXTRA_SONG_ARTWORK_URL, song.artworkUrl)
            putExtra(PlayerActivity.EXTRA_SONG_DURATION_MS, song.durationMs ?: 0L)
            if (queueSongs.isNotEmpty()) {
                putParcelableArrayListExtra(PlayerActivity.EXTRA_QUEUE_SONGS, queueSongs)
                putExtra(PlayerActivity.EXTRA_QUEUE_SELECTED_INDEX, selectedIndex)
            }
        }
        startActivity(intent)
    }

    private fun Song.toQueueSong(): QueueSong = QueueSong(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl
    )
}
