package com.cesenahome.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cesenahome.domain.models.Artist
import com.cesenahome.domain.models.ArtistPagingRequest
import com.cesenahome.domain.models.ArtistSortField
import com.cesenahome.domain.models.ArtistSortOption
import com.cesenahome.domain.models.SortDirection
import com.cesenahome.domain.usecases.GetPagedArtistsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

class ArtistViewModel(
    private val getPagedArtistsUseCase: GetPagedArtistsUseCase
): ViewModel() {

    private val sortOptionState = MutableStateFlow(ArtistSortOption())
    val sortState: StateFlow<ArtistSortOption> = sortOptionState.asStateFlow()

    val pagedArtists: Flow<PagingData<Artist>> = sortOptionState
        .flatMapLatest { sortOption ->
            getPagedArtistsUseCase(
                ArtistPagingRequest(
                    sortOption = sortOption
                )
            )
        }
        .cachedIn(viewModelScope)

    fun onSortFieldSelected(field: ArtistSortField) {
        sortOptionState.update { current ->
            if (current.field == field) current else current.copy(field = field)
        }
    }

    fun onSortDirectionSelected(direction: SortDirection) {
        sortOptionState.update { current ->
            if (current.direction == direction) current else current.copy(direction = direction)
        }
    }
}
