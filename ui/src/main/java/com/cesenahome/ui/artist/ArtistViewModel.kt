package com.cesenahome.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cesenahome.domain.models.artist.Artist
import com.cesenahome.domain.models.artist.ArtistPagingRequest
import com.cesenahome.domain.models.artist.ArtistSortField
import com.cesenahome.domain.models.artist.ArtistSortOption
import com.cesenahome.domain.models.SortDirection
import com.cesenahome.domain.usecases.libraries.GetPagedArtistsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

class ArtistViewModel(
    private val getPagedArtistsUseCase: GetPagedArtistsUseCase
): ViewModel() {

    private val sortOptionState = MutableStateFlow(ArtistSortOption())
    private val searchQueryState = MutableStateFlow("")
    val sortState: StateFlow<ArtistSortOption> = sortOptionState.asStateFlow()
    val searchQuery: StateFlow<String> = searchQueryState.asStateFlow()

    val pagedArtists: Flow<PagingData<Artist>> = combine(sortOptionState, searchQueryState) { sortOption, query ->
        sortOption to query
    }
        .flatMapLatest { (sortOption, query) ->
            getPagedArtistsUseCase(
                ArtistPagingRequest(
                    sortOption = sortOption,
                    searchQuery = query.takeIf { it.isNotBlank() }
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
    fun onSearchQueryChanged(query: String) {
        searchQueryState.update { current ->
            val newQuery = query
            if (current == newQuery) current else newQuery
        }
    }
}
