package com.cesenahome.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cesenahome.domain.models.album.Album
import com.cesenahome.domain.models.album.AlbumPagingRequest
import com.cesenahome.domain.models.album.AlbumSortField
import com.cesenahome.domain.models.album.AlbumSortOption
import com.cesenahome.domain.models.misc.SortDirection
import com.cesenahome.domain.usecases.libraries.GetPagedAlbumUseCase
import com.cesenahome.domain.usecases.download.ObserveDownloadedAlbumIdsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import androidx.paging.map

class AlbumViewModel(
    private val getPagedAlbumUseCase: GetPagedAlbumUseCase,
    observeDownloadedAlbumIdsUseCase: ObserveDownloadedAlbumIdsUseCase,
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

    private val downloadedAlbumIds = observeDownloadedAlbumIdsUseCase()

    private val basePagedAlbums: Flow<PagingData<Album>> = combine(sortOptionState, searchQueryState) { sortOption, query ->
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

    val pagedAlbums: Flow<PagingData<Album>> = basePagedAlbums
        .combine(downloadedAlbumIds) { pagingData, downloaded ->
            pagingData.map { album ->
                if (album.isDownloaded == downloaded.contains(album.id)) {
                    album
                } else {
                    album.copy(isDownloaded = downloaded.contains(album.id))
                }
            }
        }

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