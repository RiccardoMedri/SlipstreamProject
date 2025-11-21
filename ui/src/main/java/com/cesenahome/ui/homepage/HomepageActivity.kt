package com.cesenahome.ui.homepage

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.domain.models.homepage.HomeDestination
import com.cesenahome.ui.R
import com.cesenahome.ui.common.NowPlayingFabController
import com.cesenahome.ui.databinding.ActivityHomepageBinding
import com.cesenahome.ui.login.LoginActivity
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
            logoutUseCase = UseCaseProvider.logoutUseCase,
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
        binding.btnLogout.setOnClickListener { viewModel.logout() }
    }

    private fun observeVm() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.menu.collect { items ->
                        items.forEach { item ->
                            when (item.destination) {
                                HomeDestination.ARTISTS -> binding.btnArtistsTitle.text =
                                    labelWithCount(getString(R.string.title_artists), item.count)

                                HomeDestination.ALBUMS -> binding.btnAlbumsTitle.text =
                                    labelWithCount(getString(R.string.title_albums), item.count)

                                HomeDestination.PLAYLISTS -> binding.btnPlaylistsTitle.text =
                                    labelWithCount(getString(R.string.title_playlists), item.count)

                                HomeDestination.SONGS -> binding.btnSongsTitle.text =
                                    labelWithCount(getString(R.string.title_songs), item.count)
                            }
                        }
                    }
                }
                launch {
                    viewModel.isLoadingCounts.collect { loading ->
                        binding.progressCounts.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.logoutEvents.collect {
                        navigateToLogin()
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
            HomeDestination.ARTISTS -> startActivity(Intent(this, ArtistActivity::class.java))
            HomeDestination.ALBUMS -> startActivity(Intent(this, AlbumActivity::class.java))
            HomeDestination.PLAYLISTS -> startActivity(Intent(this, PlaylistActivity::class.java))
            HomeDestination.SONGS -> startActivity(Intent(this, SongsActivity::class.java))
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this@HomepageActivity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(LoginActivity.EXTRA_CLEAR_LOGIN_STATE, true)
        }
        startActivity(intent)
        finish()
    }
}
