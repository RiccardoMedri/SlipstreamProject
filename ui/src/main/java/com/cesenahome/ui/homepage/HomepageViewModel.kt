package com.cesenahome.ui.homepage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cesenahome.domain.models.homepage.HomeDestination
import com.cesenahome.domain.models.homepage.HomeMenuItem
import com.cesenahome.domain.usecases.homepage.EnsureFavouritePlaylistUseCase
import com.cesenahome.domain.usecases.homepage.GetHomepageMenuUseCase
import com.cesenahome.domain.usecases.homepage.GetLibraryCountsUseCase
import com.cesenahome.domain.usecases.auth.LogoutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomepageViewModel(
    private val getHomepageMenuUseCase: GetHomepageMenuUseCase,
    private val getLibraryCountsUseCase: GetLibraryCountsUseCase,
    private val ensureFavouritePlaylistUseCase: EnsureFavouritePlaylistUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _menu = MutableStateFlow<List<HomeMenuItem>>(emptyList())
    private val _isLoadingCounts = MutableStateFlow(false)
    private val _logoutEvents = MutableSharedFlow<Unit>()
    val menu: StateFlow<List<HomeMenuItem>> = _menu
    val isLoadingCounts: StateFlow<Boolean> = _isLoadingCounts
    val logoutEvents: SharedFlow<Unit> = _logoutEvents

    init {
        viewModelScope.launch { _menu.value = getHomepageMenuUseCase() }
        viewModelScope.launch {
            val result = ensureFavouritePlaylistUseCase()
            if (result.isFailure) {
                Log.w(
                    TAG,
                    "Unable to ensure Favourite Songs playlist on homepage start",
                    result.exceptionOrNull(),
                )
            }
        }
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

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _logoutEvents.emit(Unit)
        }
    }

    private companion object {
        private const val TAG = "HomepageViewModel"
    }
}