package com.cesenahome.ui.album

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.GridLayoutManager
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.ui.databinding.ActivityAlbumBinding
import com.cesenahome.ui.songs.SongsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AlbumActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlbumBinding
    private lateinit var albumsAdapter: AlbumsAdapter
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

        setupRecyclerView()
        observeAlbums()
        observeLoadState()
    }

    private fun setupRecyclerView() {
        albumsAdapter = AlbumsAdapter { album ->
            val intent = Intent(this, SongsActivity::class.java).apply {
                putExtra(SongsActivity.EXTRA_ALBUM_ID, album.id)
                putExtra(SongsActivity.EXTRA_ALBUM_TITLE, album.title) // Pass album title
            }
            startActivity(intent)
        }
        binding.albumsRecyclerView.apply {
            layoutManager = GridLayoutManager(this@AlbumActivity, 2)
            adapter = albumsAdapter
        }
    }

    private fun observeAlbums() {
        lifecycleScope.launch {
            viewModel.pagedAlbums.collectLatest { pagingData ->
                albumsAdapter.submitData(pagingData)
            }
        }
    }

    private fun observeLoadState() {
        lifecycleScope.launch {
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
