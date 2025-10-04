package com.cesenahome.ui.songs

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.QueueSong
import com.cesenahome.domain.models.Song
import com.cesenahome.domain.models.SongSortField
import com.cesenahome.domain.models.SortDirection
import com.cesenahome.ui.R
import com.cesenahome.ui.common.setupSearchMenu
import com.cesenahome.ui.databinding.ActivitySongsBinding
import com.cesenahome.ui.player.PlayerActivity
import com.cesenahome.ui.common.NowPlayingFabController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SongsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongsBinding
    private lateinit var adapter: SongsAdapter
    private lateinit var nowPlayingFabController: NowPlayingFabController
    private val albumId: String? by lazy {
        intent.getStringExtra(EXTRA_ALBUM_ID)
    }
    private val viewModel: SongsViewModel by lazy {
        SongsViewModel(UseCaseProvider.getPagedSongsUseCase, albumId)
    }
    companion object {
        const val EXTRA_ALBUM_ID = "extra_album_id"
        const val EXTRA_ALBUM_TITLE = "extra_album_title"
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
        if (albumId == null) {
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
        }
    }

    private fun setupList() {
        adapter = SongsAdapter { song -> viewModel.onSongClicked(song) }

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
            }
        }
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
        binding.songToolbarFilters.buttonSortField.text =
            getString(R.string.sort_field_label, getString(sortLabelRes))
        binding.songToolbarFilters.buttonSortOrder.text =
            getString(R.string.sort_order_label, getString(orderLabelRes))
        binding.songToolbarFilters.buttonSortField.contentDescription =
            binding.songToolbarFilters.buttonSortField.text
        binding.songToolbarFilters.buttonSortOrder.contentDescription =
            binding.songToolbarFilters.buttonSortOrder.text
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

    override fun onDestroy() {
        super.onDestroy()
    }
}
