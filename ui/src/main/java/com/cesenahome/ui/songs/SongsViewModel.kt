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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SongsViewModel(
    private val getPagedSongsUseCase: GetPagedSongsUseCase,
    private val albumId: String? = null
) : ViewModel() {

    private val sortOptionState = MutableStateFlow(SongSortOption())
    val sortState: StateFlow<SongSortOption> = sortOptionState.asStateFlow()

    val pagedSongs: Flow<PagingData<Song>> = sortOptionState
        .flatMapLatest { sortOption ->
            getPagedSongsUseCase(
                SongPagingRequest(
                    albumId = albumId,
                    sortOption = sortOption
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
}