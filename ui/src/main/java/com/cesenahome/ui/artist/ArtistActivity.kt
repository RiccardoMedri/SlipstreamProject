package com.cesenahome.ui.artist

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.ui.album.AlbumActivity
import com.cesenahome.ui.databinding.ActivityArtistBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ArtistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArtistBinding
    private lateinit var artistsAdapter: ArtistsAdapter
    private val viewModel: ArtistViewModel by lazy {
        ArtistViewModel(UseCaseProvider.getPagedArtistsUseCase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityArtistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        observeArtists()
        observeLoadState()
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

    private fun observeArtists() {
        lifecycleScope.launch {
            viewModel.pagedArtists.collectLatest { pagingData ->
                artistsAdapter.submitData(pagingData)
            }
        }
    }

    private fun observeLoadState() {
        lifecycleScope.launch {
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
