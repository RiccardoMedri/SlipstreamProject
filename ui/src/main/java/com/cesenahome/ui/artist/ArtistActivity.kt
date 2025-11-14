package com.cesenahome.ui.artist

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.artist.ArtistSortField
import com.cesenahome.domain.models.misc.SortDirection
import com.cesenahome.ui.R
import com.cesenahome.ui.album.AlbumActivity
import com.cesenahome.ui.common.NowPlayingFabController
import com.cesenahome.ui.common.applySystemBarsInsets
import com.cesenahome.ui.common.setupSearchMenu
import com.cesenahome.ui.databinding.ActivityArtistBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ArtistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArtistBinding
    private lateinit var artistsAdapter: ArtistsAdapter
    private lateinit var nowPlayingFabController: NowPlayingFabController
    private val viewModel: ArtistViewModel by lazy {
        ArtistViewModel(UseCaseProvider.getPagedArtistsUseCase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArtistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val initialFabBottomMargin = (binding.nowPlayingFab.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        applySystemBarsInsets(binding.root) { insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.nowPlayingFab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = initialFabBottomMargin + systemBars.bottom
            }        }

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
        binding.toolbar.setupSearchMenu(
            queryHint = getString(R.string.search_hint_artists),
            initialQuery = viewModel.searchQuery.value,
            onQueryChanged = viewModel::onSearchQueryChanged
        )
        binding.artistToolbarFilters.buttonSortField.setOnClickListener { view ->
            showSortFieldMenu(view)
        }
        binding.artistToolbarFilters.buttonSortOrder.setOnClickListener { view ->
            showSortOrderMenu(view)
        }
    }

    private fun setupRecyclerView() {
        artistsAdapter = ArtistsAdapter { artist ->
            val intent = Intent(this, AlbumActivity::class.java).apply {
                putExtra(AlbumActivity.EXTRA_ARTIST_ID, artist.id)
            }
            startActivity(intent)
        }
        binding.artistsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ArtistActivity)
            adapter = artistsAdapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pagedArtists.collectLatest { pagingData ->
                        artistsAdapter.submitData(pagingData)
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
                artistsAdapter.loadStateFlow.collectLatest { loadStates ->
                    binding.progressBar.isVisible = loadStates.refresh is LoadState.Loading

                val errorState = loadStates.refresh as? LoadState.Error
                    ?: loadStates.append as? LoadState.Error
                    ?: loadStates.prepend as? LoadState.Error

                    errorState?.let {
                        Toast.makeText(
                            this@ArtistActivity,
                            "Error: ${it.error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun updateSortButtons(field: ArtistSortField, direction: SortDirection) {
        val sortLabelRes = when (field) {
            ArtistSortField.NAME -> R.string.artists_sort_by_name
            ArtistSortField.DATE_ADDED -> R.string.artists_sort_by_date_added
        }
        val orderLabelRes = when (direction) {
            SortDirection.ASCENDING -> R.string.sort_order_ascending
            SortDirection.DESCENDING -> R.string.sort_order_descending
        }
        binding.artistToolbarFilters.buttonSortField.text =
            getString(R.string.sort_field_label, getString(sortLabelRes))
        binding.artistToolbarFilters.buttonSortOrder.text =
            getString(R.string.sort_order_label, getString(orderLabelRes))
        binding.artistToolbarFilters.buttonSortField.contentDescription =
            binding.artistToolbarFilters.buttonSortField.text
        binding.artistToolbarFilters.buttonSortOrder.contentDescription =
            binding.artistToolbarFilters.buttonSortOrder.text
    }

    private fun showSortFieldMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_artist_sort_field, popup.menu)
        when (viewModel.sortState.value.field) {
            ArtistSortField.NAME -> popup.menu.findItem(R.id.sort_by_name)?.isChecked = true
            ArtistSortField.DATE_ADDED -> popup.menu.findItem(R.id.sort_by_date_added)?.isChecked = true
        }
        popup.setOnMenuItemClickListener { item ->
            val selected = when (item.itemId) {
                R.id.sort_by_name -> ArtistSortField.NAME
                R.id.sort_by_date_added -> ArtistSortField.DATE_ADDED
                else -> null
            }
            selected?.let { field ->
                if (field != viewModel.sortState.value.field) {
                    viewModel.onSortFieldSelected(field)
                    artistsAdapter.refresh()
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
                    artistsAdapter.refresh()
                }
            }
            true
        }
        popup.show()
    }
}
