package com.cesenahome.ui.homepage

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.ui.common.NowPlayingFabController
import com.cesenahome.ui.databinding.ActivityHomepageBinding
import com.cesenahome.domain.models.homepage.HomeDestination
import com.cesenahome.ui.album.AlbumActivity
import com.cesenahome.ui.artist.ArtistActivity
import com.cesenahome.ui.songs.SongsActivity
import com.cesenahome.ui.playlist.PlaylistActivity
import kotlinx.coroutines.launch

class HomepageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomepageBinding
    private lateinit var nowPlayingFabController: NowPlayingFabController

    private val viewModel: HomepageViewModel by lazy {
        HomepageViewModel(
            getHomepageMenuUseCase = UseCaseProvider.getHomepageMenuUseCase,
            getLibraryCountsUseCase = UseCaseProvider.getLibraryCountsUseCase,
            ensureFavouritePlaylistUseCase = UseCaseProvider.ensureFavouritePlaylistUseCase,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        nowPlayingFabController = NowPlayingFabController(this, binding.nowPlayingFab)

        setupClicks()
        observeVm()
    }

    private fun setupClicks() {
        binding.btnArtists.setOnClickListener { onDestination(HomeDestination.ARTISTS) }
        binding.btnAlbums.setOnClickListener { onDestination(HomeDestination.ALBUMS) }
        binding.btnPlaylists.setOnClickListener { onDestination(HomeDestination.PLAYLISTS) }
        binding.btnSongs.setOnClickListener { onDestination(HomeDestination.SONGS) }
    }

    private fun observeVm() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.menu.collect { items ->
                        items.forEach { item ->
                            when (item.destination) {
                                HomeDestination.ARTISTS ->
                                    binding.btnArtists.text = labelWithCount("Artists", item.count)
                                HomeDestination.ALBUMS ->
                                    binding.btnAlbums.text = labelWithCount("Albums", item.count)
                                HomeDestination.PLAYLISTS ->
                                    binding.btnPlaylists.text = labelWithCount("Playlists", item.count)
                                HomeDestination.SONGS ->
                                    binding.btnSongs.text = labelWithCount("Songs", item.count)
                            }
                        }
                    }
                }
                launch {
                    viewModel.isLoadingCounts.collect { loading ->
                        binding.progressCounts.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun labelWithCount(title: String, count: Int?): CharSequence {
        return if (count != null) "$title ($count)" else title
    }

    private fun onDestination(dest: HomeDestination) {
        when (dest) {
            HomeDestination.ARTISTS   -> startActivity(Intent(this, ArtistActivity::class.java))
            HomeDestination.ALBUMS    -> startActivity(Intent(this, AlbumActivity::class.java))
            HomeDestination.PLAYLISTS -> startActivity(Intent(this, PlaylistActivity::class.java))
            HomeDestination.SONGS     -> startActivity(Intent(this, SongsActivity::class.java))
        }
    }
}
