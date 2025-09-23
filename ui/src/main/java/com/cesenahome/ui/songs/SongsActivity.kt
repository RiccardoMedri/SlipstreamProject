package com.cesenahome.ui.songs

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.isVisible
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.ui.databinding.ActivitySongsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.paging.LoadState

class SongsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongsBinding
    private val adapter = SongsAdapter()
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
        binding.recyclerSongs.layoutManager = LinearLayoutManager(this)
        binding.recyclerSongs.adapter = adapter
        binding.recyclerSongs.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter.addLoadStateListener { loadStates ->
            val refresh = loadStates.refresh
            binding.progress.isVisible = refresh is LoadState.Loading
            binding.emptyView.root.isVisible =
                refresh is LoadState.NotLoading && adapter.itemCount == 0

            val error = (refresh as? LoadState.Error)
                ?: (loadStates.append as? LoadState.Error)
                ?: (loadStates.prepend as? LoadState.Error)

            error?.error?.localizedMessage?.let { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeVm() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pagedSongs.collectLatest { pagingData ->
                        adapter.submitData(pagingData)
                    }
                }
            }
        }
    }
}
