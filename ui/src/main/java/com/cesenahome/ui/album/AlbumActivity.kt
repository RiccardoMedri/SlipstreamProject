package com.cesenahome.ui.album

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.GridLayoutManager
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.album.AlbumSortField
import com.cesenahome.domain.models.song.SortDirection
import com.cesenahome.ui.R
import com.cesenahome.ui.common.NowPlayingFabController
import com.cesenahome.ui.common.setupSearchMenu
import com.cesenahome.ui.databinding.ActivityAlbumBinding
import com.cesenahome.ui.songs.SongsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AlbumActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlbumBinding
    private lateinit var albumsAdapter: AlbumsAdapter
    private lateinit var nowPlayingFabController: NowPlayingFabController
    private val artistId: String? by lazy {
        intent.getStringExtra(EXTRA_ARTIST_ID)
    }
    private val viewModel: AlbumViewModel by lazy {
        AlbumViewModel(UseCaseProvider.getPagedAlbumUseCase, artistId)
    }
    companion object {
        const val EXTRA_ARTIST_ID = "extra_artist_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAlbumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nowPlayingFabController = NowPlayingFabController(this, binding.nowPlayingFab)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        observeLoadState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        if (artistId == null) {
            binding.toolbar.setupSearchMenu(
                queryHint = getString(R.string.search_hint_albums),
                initialQuery = viewModel.searchQuery.value,
                onQueryChanged = viewModel::onSearchQueryChanged
            )
        } else {
            binding.toolbar.menu.clear()
        }
        binding.albumToolbarFilters.buttonSortField.setOnClickListener { view ->
            showSortFieldMenu(view)
        }
        binding.albumToolbarFilters.buttonSortOrder.setOnClickListener { view ->
            showSortOrderMenu(view)
        }
    }

    private fun setupRecyclerView() {
        albumsAdapter = AlbumsAdapter { album ->
            val intent = Intent(this, SongsActivity::class.java).apply {
                putExtra(SongsActivity.EXTRA_ALBUM_ID, album.id)
                putExtra(SongsActivity.EXTRA_ALBUM_TITLE, album.title)
            }
            startActivity(intent)
        }
        binding.albumsRecyclerView.apply {
            layoutManager = GridLayoutManager(this@AlbumActivity, 2)
            adapter = albumsAdapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pagedAlbums.collectLatest { pagingData ->
                        albumsAdapter.submitData(pagingData)
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

    private fun observeLoadState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                albumsAdapter.loadStateFlow.collectLatest { loadStates ->
                    binding.progressBar.isVisible = loadStates.refresh is LoadState.Loading

                val errorState = loadStates.refresh as? LoadState.Error
                    ?: loadStates.append as? LoadState.Error
                    ?: loadStates.prepend as? LoadState.Error

                    errorState?.let {
                        Toast.makeText(
                            this@AlbumActivity,
                            "Error: ${it.error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun updateSortButtons(field: AlbumSortField, direction: SortDirection) {
        val sortLabelRes = when (field) {
            AlbumSortField.TITLE -> R.string.albums_sort_by_title
            AlbumSortField.ARTIST -> R.string.albums_sort_by_artist
            AlbumSortField.YEAR -> R.string.albums_sort_by_year
            AlbumSortField.DATE_ADDED -> R.string.albums_sort_by_date_added
        }
        val orderLabelRes = when (direction) {
            SortDirection.ASCENDING -> R.string.sort_order_ascending
            SortDirection.DESCENDING -> R.string.sort_order_descending
        }
        binding.albumToolbarFilters.buttonSortField.text =
            getString(R.string.sort_field_label, getString(sortLabelRes))
        binding.albumToolbarFilters.buttonSortOrder.text =
            getString(R.string.sort_order_label, getString(orderLabelRes))
        binding.albumToolbarFilters.buttonSortField.contentDescription =
            binding.albumToolbarFilters.buttonSortField.text
        binding.albumToolbarFilters.buttonSortOrder.contentDescription =
            binding.albumToolbarFilters.buttonSortOrder.text
    }

    private fun showSortFieldMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_album_sort_field, popup.menu)
        when (viewModel.sortState.value.field) {
            AlbumSortField.TITLE -> popup.menu.findItem(R.id.sort_by_title)?.isChecked = true
            AlbumSortField.ARTIST -> popup.menu.findItem(R.id.sort_by_artist)?.isChecked = true
            AlbumSortField.YEAR -> popup.menu.findItem(R.id.sort_by_year)?.isChecked = true
            AlbumSortField.DATE_ADDED -> popup.menu.findItem(R.id.sort_by_date_added)?.isChecked = true
        }
        popup.setOnMenuItemClickListener { item ->
            val selected = when (item.itemId) {
                R.id.sort_by_title -> AlbumSortField.TITLE
                R.id.sort_by_artist -> AlbumSortField.ARTIST
                R.id.sort_by_year -> AlbumSortField.YEAR
                R.id.sort_by_date_added -> AlbumSortField.DATE_ADDED
                else -> null
            }
            selected?.let { field ->
                if (field != viewModel.sortState.value.field) {
                    viewModel.onSortFieldSelected(field)
                    albumsAdapter.refresh()
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
                    albumsAdapter.refresh()
                }
            }
            true
        }
        popup.show()
    }
}
