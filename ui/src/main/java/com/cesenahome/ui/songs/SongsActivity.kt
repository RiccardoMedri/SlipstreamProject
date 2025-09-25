package com.cesenahome.ui.songs

import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.Song
import com.cesenahome.ui.databinding.ActivitySongsBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import com.cesenahome.ui.player.MusicService


class SongsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongsBinding
    private lateinit var adapter: SongsAdapter

    private val viewModel: SongsViewModel by lazy {
        SongsViewModel(UseCaseProvider.getPagedSongsUseCase)
    }
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySongsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupController()
        setupList()
        observeVm()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun setupController() {
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener(
                { controller = future.get() },
                MoreExecutors.directExecutor()
            )
        }
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
                            is SongsViewModel.PlayCommand.PlaySong -> play(cmd.song)
                        }
                    }
                }
            }
        }
    }

    private fun play(song: Song) {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .apply { song.artworkUrl?.let { setArtworkUri(it.toUri()) } }
            .build()

        val item = MediaItem.Builder()
            .setMediaId(song.id) // Service resolves URI from this mediaId
            .setMediaMetadata(metadata)
            .build()

        controller?.apply {
            setMediaItem(item)
            prepare()
            play()
        } ?: Toast.makeText(this, "Player not ready yet", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        controllerFuture?.cancel(true)
        controllerFuture = null
        super.onDestroy()
    }
}
