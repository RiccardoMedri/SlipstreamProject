package com.cesenahome.ui.homepage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cesenahome.domain.models.HomeMenuItem
import com.cesenahome.domain.usecases.GetHomepageMenuUseCase
import com.cesenahome.domain.usecases.GetLibraryCountsUseCase
import com.cesenahome.domain.models.HomeDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class HomepageViewModel(
    private val getHomepageMenuUseCase: GetHomepageMenuUseCase,
    private val getLibraryCountsUseCase: GetLibraryCountsUseCase
) : ViewModel() {

    private val _menu = MutableStateFlow<List<HomeMenuItem>>(emptyList())
    val menu: StateFlow<List<HomeMenuItem>> = _menu

    private val _isLoadingCounts = MutableStateFlow(false)
    val isLoadingCounts: StateFlow<Boolean> = _isLoadingCounts

    init {
        viewModelScope.launch { _menu.value = getHomepageMenuUseCase() }
        // optional counts fetch
        viewModelScope.launch {
            _isLoadingCounts.value = true
            getLibraryCountsUseCase().onSuccess { counts ->
                _menu.update { items ->
                    items.map {
                        when (it.destination) {
                            HomeDestination.ARTISTS   -> it.copy(count = counts.artists)
                            HomeDestination.ALBUMS    -> it.copy(count = counts.albums)
                            HomeDestination.PLAYLISTS -> it.copy(count = counts.playlists)
                            HomeDestination.SONGS     -> it.copy(count = counts.songs)
                        }
                    }
                }
            }
            _isLoadingCounts.value = false
        }
    }
}
