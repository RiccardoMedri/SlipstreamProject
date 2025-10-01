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
    val sortState: StateFlow<AlbumSortOption> = sortOptionState.asStateFlow()

    val pagedAlbums: Flow<PagingData<Album>> = sortOptionState
        .flatMapLatest { sortOption ->
            getPagedAlbumUseCase(
                AlbumPagingRequest(
                    artistId = artistId,
                    sortOption = sortOption
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
}