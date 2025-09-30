package com.cesenahome.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cesenahome.domain.models.Artist
import com.cesenahome.domain.usecases.GetPagedArtistsUseCase
import kotlinx.coroutines.flow.Flow

class ArtistViewModel(
    getPagedArtistsUseCase: GetPagedArtistsUseCase
): ViewModel() {
    val pagedArtists: Flow<PagingData<Artist>> = getPagedArtistsUseCase().cachedIn(viewModelScope)
}
