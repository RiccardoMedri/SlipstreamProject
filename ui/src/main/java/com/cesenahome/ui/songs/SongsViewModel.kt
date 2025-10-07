package com.cesenahome.ui.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cesenahome.domain.models.song.Song
import com.cesenahome.domain.models.song.SongPagingRequest
import com.cesenahome.domain.models.song.SongSortField
import com.cesenahome.domain.models.song.SongSortOption
import com.cesenahome.domain.models.song.SortDirection
import com.cesenahome.domain.usecases.AddSongToFavouritesUseCase
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
    private val addSongToFavouritesUseCase: AddSongToFavouritesUseCase,
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

    sealed interface FavouriteEvent {
        data class Success(val song: Song) : FavouriteEvent
        data class Failure(val song: Song, val reason: String?) : FavouriteEvent
    }

    private val _favouriteEvents = MutableSharedFlow<FavouriteEvent>(extraBufferCapacity = 1)
    val favouriteEvents: SharedFlow<FavouriteEvent> = _favouriteEvents

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
    fun onAddSongToFavourites(song: Song) {
        viewModelScope.launch {
            val result = addSongToFavouritesUseCase(song.id)
            result.fold(
                onSuccess = {
                    _favouriteEvents.emit(FavouriteEvent.Success(song))
                },
                onFailure = { error ->
                    _favouriteEvents.emit(FavouriteEvent.Failure(song, error.message))
                }
            )
        }
    }
}