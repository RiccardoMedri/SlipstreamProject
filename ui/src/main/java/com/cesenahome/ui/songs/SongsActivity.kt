package com.cesenahome.ui.songs

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.cesenahome.domain.di.UseCaseProvider
import com.cesenahome.ui.databinding.ActivitySongsBinding
import kotlinx.coroutines.launch

class SongsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongsBinding
    private val adapter = SongsAdapter()

    private val viewModel: SongsViewModel by lazy {
        SongsViewModel(UseCaseProvider.getAllSongsUseCase)
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
    }

    private fun observeVm() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.songs.collect { list ->
                        adapter.updateList(list)
                        binding.emptyView.root.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                    }
                }
                launch {
                    viewModel.loading.collect { loading ->
                        binding.progress.isVisible = loading
                        binding.emptyView.root.isVisible = !loading && adapter.itemCount == 0
                    }
                }
            }
        }
    }
}