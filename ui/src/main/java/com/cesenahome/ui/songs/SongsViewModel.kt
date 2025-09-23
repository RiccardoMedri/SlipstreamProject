package com.cesenahome.ui.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cesenahome.domain.models.Song
import com.cesenahome.domain.usecases.GetPagedSongsUseCase
import kotlinx.coroutines.flow.Flow

class SongsViewModel(
    getPagedSongsUseCase: GetPagedSongsUseCase
) : ViewModel() {

    // Flow that Paging manages. cachedIn keeps it alive across config changes.
    val pagedSongs: Flow<PagingData<Song>> = getPagedSongsUseCase().cachedIn(viewModelScope)
}