package com.cesenahome.ui.homepage

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.ui.databinding.ActivityHomepageBinding
import com.cesenahome.domain.models.HomeDestination
import kotlinx.coroutines.launch

class HomepageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomepageBinding

    private val viewModel: HomepageViewModel by lazy {
        HomepageViewModel(
            getHomepageMenuUseCase = UseCaseProvider.getHomepageMenuUseCase,
            getLibraryCountsUseCase = UseCaseProvider.getLibraryCountsUseCase
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomepageBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                        // update button labels (+ optional counts)
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
        // TODO: Replace Toasts with navigation to your list Activities.
        // e.g., startActivity(Intent(this, ArtistsListActivity::class.java))
        val msg = when (dest) {
            HomeDestination.ARTISTS   -> "Go to Artists"
            HomeDestination.ALBUMS    -> "Go to Albums"
            HomeDestination.PLAYLISTS -> "Go to Playlists"
            HomeDestination.SONGS     -> "Go to Songs"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
