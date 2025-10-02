package com.cesenahome.ui.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cesenahome.domain.models.Song
import com.cesenahome.domain.models.SongPagingRequest
import com.cesenahome.domain.models.SongSortField
import com.cesenahome.domain.models.SongSortOption
import com.cesenahome.domain.models.SortDirection
import com.cesenahome.domain.usecases.GetPagedSongsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SongsViewModel(
    private val getPagedSongsUseCase: GetPagedSongsUseCase,
    private val albumId: String? = null
) : ViewModel() {

    private val sortOptionState = MutableStateFlow(SongSortOption())
    private val searchQueryState = MutableStateFlow("")
    val sortState: StateFlow<SongSortOption> = sortOptionState.asStateFlow()
    val searchQuery: StateFlow<String> = searchQueryState.asStateFlow()

    val pagedSongs: Flow<PagingData<Song>> = combine(sortOptionState, searchQueryState) { sortOption, query ->
        sortOption to query
    }
        .flatMapLatest { (sortOption, query) ->
            getPagedSongsUseCase(
                SongPagingRequest(
                    albumId = albumId,
                    sortOption = sortOption,
                    searchQuery = if (albumId == null) query.takeIf { it.isNotBlank() } else null
                )
            )
        }
        .cachedIn(viewModelScope)

    sealed interface PlayCommand {
        data class PlaySong(val song: Song) : PlayCommand
    }

    private val _playCommands = MutableSharedFlow<PlayCommand>(extraBufferCapacity = 1)
    val playCommands: SharedFlow<PlayCommand> = _playCommands

    fun onSongClicked(song: Song) {
        viewModelScope.launch {
            _playCommands.emit(PlayCommand.PlaySong(song))
        }
    }

    fun onSortFieldSelected(field: SongSortField) {
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
        if (albumId != null) return
        searchQueryState.update { current ->
            val newQuery = query
            if (current == newQuery) current else newQuery
        }
    }
}