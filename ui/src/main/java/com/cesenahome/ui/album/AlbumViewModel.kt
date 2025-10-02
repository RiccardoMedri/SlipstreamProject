package com.cesenahome.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cesenahome.domain.models.Album
import com.cesenahome.domain.models.AlbumPagingRequest
import com.cesenahome.domain.models.AlbumSortField
import com.cesenahome.domain.models.AlbumSortOption
import com.cesenahome.domain.models.SortDirection
import com.cesenahome.domain.usecases.GetPagedAlbumUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

class AlbumViewModel(
    private val getPagedAlbumUseCase: GetPagedAlbumUseCase,
    private val artistId: String? = null
): ViewModel() {
    private val defaultSort = if (artistId == null) {
        AlbumSortOption()
    } else {
        AlbumSortOption(field = AlbumSortField.YEAR, direction = SortDirection.DESCENDING)
    }

    private val sortOptionState = MutableStateFlow(defaultSort)
    private val searchQueryState = MutableStateFlow("")
    val sortState: StateFlow<AlbumSortOption> = sortOptionState.asStateFlow()
    val searchQuery: StateFlow<String> = searchQueryState.asStateFlow()

    val pagedAlbums: Flow<PagingData<Album>> = combine(sortOptionState, searchQueryState) { sortOption, query ->
        sortOption to query
    }
        .flatMapLatest { (sortOption, query) ->
            getPagedAlbumUseCase(
                AlbumPagingRequest(
                    artistId = artistId,
                    sortOption = sortOption,
                    searchQuery = query.takeIf { it.isNotBlank() }
                )
            )
        }
        .cachedIn(viewModelScope)

    fun onSortFieldSelected(field: AlbumSortField) {
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