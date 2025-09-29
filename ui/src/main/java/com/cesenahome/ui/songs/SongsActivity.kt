package com.cesenahome.ui.songs

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
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
import com.cesenahome.domain.models.Song
import com.cesenahome.ui.databinding.ActivitySongsBinding
import com.cesenahome.ui.player.PlayerActivity // Import PlayerActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SongsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongsBinding
    private lateinit var adapter: SongsAdapter

    private val viewModel: SongsViewModel by lazy {
        SongsViewModel(UseCaseProvider.getPagedSongsUseCase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySongsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupList()
        observeVm()
    }

    private fun setupList() {
        adapter = SongsAdapter { song -> viewModel.onSongClicked(song) }

        binding.recyclerSongs.layoutManager = LinearLayoutManager(this)
        binding.recyclerSongs.adapter = adapter
        binding.recyclerSongs.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

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
            }
        }
    }


    @OptIn(UnstableApi::class)
    private fun launchPlayerActivity(song: Song) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_SONG_ID, song.id)
            putExtra(PlayerActivity.EXTRA_SONG_TITLE, song.title)
            putExtra(PlayerActivity.EXTRA_SONG_ARTIST, song.artist)
            putExtra(PlayerActivity.EXTRA_SONG_ALBUM, song.album)
            putExtra(PlayerActivity.EXTRA_SONG_ARTWORK_URL, song.artworkUrl)
            putExtra(PlayerActivity.EXTRA_SONG_DURATION_MS, song.durationMs ?: 0L) // Provide a default if null
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
