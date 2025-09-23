package com.cesenahome.ui.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cesenahome.domain.models.Song
import com.cesenahome.domain.usecases.GetAllSongsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SongsViewModel(
    private val getAllSongsUseCase: GetAllSongsUseCase
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    init {
        viewModelScope.launch {
            _loading.value = true
            _songs.value = getAllSongsUseCase()
            _loading.value = false
        }
    }
}