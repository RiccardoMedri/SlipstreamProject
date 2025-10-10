package com.cesenahome.ui.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
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
    private val albumId: String? = null,
    private val playlistId: String? = null,
) : ViewModel() {

    private val sortOptionState = MutableStateFlow(SongSortOption())
    private val searchQueryState = MutableStateFlow("")
    val sortState: StateFlow<SongSortOption> = sortOptionState.asStateFlow()
    val searchQuery: StateFlow<String> = searchQueryState.asStateFlow()

    private val favouriteOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private val basePagedSongs: Flow<PagingData<Song>> = combine(sortOptionState, searchQueryState) { sortOption, query ->
        sortOption to query
    }
        .flatMapLatest { (sortOption, query) ->
            getPagedSongsUseCase(
                SongPagingRequest(
                    albumId = albumId,
                    playlistId = playlistId,
                    sortOption = sortOption,
                    searchQuery = if (albumId == null && playlistId == null) {
                        query.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                )
            )
        }
        .cachedIn(viewModelScope)
    val pagedSongs: Flow<PagingData<Song>> = basePagedSongs
        .combine(favouriteOverrides) { pagingData, overrides ->
            pagingData.map { song ->
                val override = overrides[song.id]
                if (override == null) song else song.copy(isFavorite = override)
            }
        }

    sealed interface PlayCommand {
        data class PlaySong(val song: Song) : PlayCommand
    }

    private val _playCommands = MutableSharedFlow<PlayCommand>(extraBufferCapacity = 1)
    val playCommands: SharedFlow<PlayCommand> = _playCommands

    sealed interface FavouriteEvent {
        data class Success(val song: Song, val isFavourite: Boolean) : FavouriteEvent
        data class Failure(val song: Song, val isFavourite: Boolean, val reason: String?) : FavouriteEvent
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
        if (albumId != null || playlistId != null) return
        searchQueryState.update { current ->
            val newQuery = query
            if (current == newQuery) current else newQuery
        }
    }
    fun onFavouriteClick(song: Song) {
        val overridesSnapshot = favouriteOverrides.value
        val hadOverride = overridesSnapshot.containsKey(song.id)
        val previousState = overridesSnapshot[song.id] ?: song.isFavorite
        val targetState = !previousState

        favouriteOverrides.update { current ->
            val existing = current[song.id]
            if (existing == targetState) current else current + (song.id to targetState)
        }
        viewModelScope.launch {
            val result = addSongToFavouritesUseCase(song.id, targetState)
            result.fold(
                onSuccess = {
                    _favouriteEvents.emit(
                        FavouriteEvent.Success(
                            song.copy(isFavorite = targetState),
                            targetState,
                        )
                    )
                },
                onFailure = { error ->
                    favouriteOverrides.update { current ->
                        val mutable = current.toMutableMap()
                        if (hadOverride) {
                            mutable[song.id] = previousState
                        } else {
                            mutable.remove(song.id)
                        }
                        mutable.toMap()
                    }
                    _favouriteEvents.emit(
                        FavouriteEvent.Failure(
                            song.copy(isFavorite = previousState),
                            targetState,
                            error.message,
                        )
                    )
                }
            )
        }
    }
}