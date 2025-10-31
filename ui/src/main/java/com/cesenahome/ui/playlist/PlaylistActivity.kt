package com.cesenahome.ui.playlist

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.playlist.Playlist
import com.cesenahome.domain.models.playlist.PlaylistSortField
import com.cesenahome.domain.models.misc.SortDirection
import com.cesenahome.ui.R
import com.cesenahome.ui.common.NowPlayingFabController
import com.cesenahome.ui.common.setupSearchMenu
import com.cesenahome.ui.databinding.ActivityPlaylistsBinding
import com.cesenahome.ui.songs.SongsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistsBinding
    private lateinit var adapter: PlaylistAdapter
    private lateinit var nowPlayingFabController: NowPlayingFabController

    private val viewModel: PlaylistViewModel by lazy {
        PlaylistViewModel(
            getPagedPlaylistsUseCase = UseCaseProvider.getPagedPlaylistsUseCase,
            observeDownloadedPlaylistIdsUseCase = UseCaseProvider.observeDownloadedPlaylistIdsUseCase,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        nowPlayingFabController = NowPlayingFabController(this, binding.nowPlayingFab)

        setupToolbar()
        setupList()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.playlistToolbarFilters.root.isVisible = true
        binding.toolbar.setupSearchMenu(
            queryHint = getString(R.string.search_hint_playlists),
            initialQuery = viewModel.searchQuery.value,
            onQueryChanged = viewModel::onSearchQueryChanged,
        )
        binding.playlistToolbarFilters.buttonSortField.setOnClickListener { anchor ->
            showSortFieldMenu(anchor)
        }
        binding.playlistToolbarFilters.buttonSortOrder.setOnClickListener { anchor ->
            showSortOrderMenu(anchor)
        }
    }

    private fun setupList() {
        adapter = PlaylistAdapter { playlist ->
            viewModel.onPlaylistClicked(playlist)
        }
        binding.recyclerPlaylists.layoutManager = LinearLayoutManager(this)
        binding.recyclerPlaylists.adapter = adapter
        binding.recyclerPlaylists.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        adapter.addLoadStateListener { loadStates ->
            val refresh = loadStates.refresh
            binding.progress.isVisible = refresh is LoadState.Loading
            binding.emptyView.root.isVisible =
                refresh is LoadState.NotLoading && adapter.itemCount == 0

            val error = (refresh as? LoadState.Error)
                ?: (loadStates.append as? LoadState.Error)
                ?: (loadStates.prepend as? LoadState.Error)
            error?.let {
                Toast.makeText(this, it.error.localizedMessage ?: getString(R.string.unknown), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pagedPlaylists.collectLatest { pagingData: PagingData<Playlist> ->
                        adapter.submitData(pagingData)
                    }
                }
                launch {
                    viewModel.commands.collect { command ->
                        when (command) {
                            is PlaylistViewModel.Command.OpenPlaylist -> {
                                val intent = Intent(this@PlaylistActivity, SongsActivity::class.java).apply {
                                    putExtra(SongsActivity.EXTRA_PLAYLIST_ID, command.playlist.id)
                                    putExtra(SongsActivity.EXTRA_PLAYLIST_NAME, command.playlist.name)
                                }
                                startActivity(intent)
                            }
                        }
                    }
                }
                launch {
                    viewModel.sortOption.collect { sortOption ->
                        updateSortButtons(sortOption.field, sortOption.direction)
                    }
                }
            }
        }
    }

    private fun updateSortButtons(field: PlaylistSortField, direction: SortDirection) {
        val sortLabelRes = when (field) {
            PlaylistSortField.NAME -> R.string.playlists_sort_by_name
            PlaylistSortField.DATE_ADDED -> R.string.playlists_sort_by_date_added
        }
        val orderLabelRes = when (direction) {
            SortDirection.ASCENDING -> R.string.sort_order_ascending
            SortDirection.DESCENDING -> R.string.sort_order_descending
        }
        binding.playlistToolbarFilters.buttonSortField.text =
            getString(R.string.sort_field_label, getString(sortLabelRes))
        binding.playlistToolbarFilters.buttonSortOrder.text =
            getString(R.string.sort_order_label, getString(orderLabelRes))
        binding.playlistToolbarFilters.buttonSortField.contentDescription =
            binding.playlistToolbarFilters.buttonSortField.text
        binding.playlistToolbarFilters.buttonSortOrder.contentDescription =
            binding.playlistToolbarFilters.buttonSortOrder.text
    }

    private fun showSortFieldMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_playlist_sort_field, popup.menu)
        when (viewModel.sortOption.value.field) {
            PlaylistSortField.NAME -> popup.menu.findItem(R.id.sort_by_name)?.isChecked = true
            PlaylistSortField.DATE_ADDED -> popup.menu.findItem(R.id.sort_by_date_added)?.isChecked = true
        }
        popup.setOnMenuItemClickListener { item ->
            val selected = when (item.itemId) {
                R.id.sort_by_name -> PlaylistSortField.NAME
                R.id.sort_by_date_added -> PlaylistSortField.DATE_ADDED
                else -> null
            }
            selected?.let { field ->
                if (field != viewModel.sortOption.value.field) {
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
        when (viewModel.sortOption.value.direction) {
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
                if (direction != viewModel.sortOption.value.direction) {
                    viewModel.onSortDirectionSelected(direction)
                    adapter.refresh()
                }
            }
            true
        }
        popup.show()
    }
}