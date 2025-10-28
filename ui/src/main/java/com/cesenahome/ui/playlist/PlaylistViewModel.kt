package com.cesenahome.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cesenahome.domain.models.playlist.Playlist
import com.cesenahome.domain.models.playlist.PlaylistPagingRequest
import com.cesenahome.domain.models.playlist.PlaylistSortField
import com.cesenahome.domain.models.playlist.PlaylistSortOption
import com.cesenahome.domain.models.SortDirection
import com.cesenahome.domain.usecases.libraries.GetPagedPlaylistsUseCase
import com.cesenahome.domain.usecases.download.ObserveDownloadedPlaylistIdsUseCase
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
import androidx.paging.map

class PlaylistViewModel(
    private val getPagedPlaylistsUseCase: GetPagedPlaylistsUseCase,
    observeDownloadedPlaylistIdsUseCase: ObserveDownloadedPlaylistIdsUseCase,
) : ViewModel() {

    private val sortState = MutableStateFlow(PlaylistSortOption())
    private val searchQueryState = MutableStateFlow("")

    val sortOption: StateFlow<PlaylistSortOption> = sortState.asStateFlow()
    val searchQuery: StateFlow<String> = searchQueryState.asStateFlow()

    private val downloadedPlaylistIds = observeDownloadedPlaylistIdsUseCase()

    private val basePagedPlaylists: Flow<PagingData<Playlist>> = combine(sortState, searchQueryState) { sort, query ->
        sort to query
    }
        .flatMapLatest { (sortOption, query) ->
            getPagedPlaylistsUseCase(
                PlaylistPagingRequest(
                    sortOption = sortOption,
                    searchQuery = query.takeIf { it.isNotBlank() },
                )
            )
        }
        .cachedIn(viewModelScope)

    val pagedPlaylists: Flow<PagingData<Playlist>> = basePagedPlaylists
        .combine(downloadedPlaylistIds) { pagingData, downloaded ->
            pagingData.map { playlist ->
                if (playlist.isDownloaded == downloaded.contains(playlist.id)) {
                    playlist
                } else {
                    playlist.copy(isDownloaded = downloaded.contains(playlist.id))
                }
            }
        }

    sealed interface Command {
        data class OpenPlaylist(val playlist: Playlist) : Command
    }

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 1)
    val commands: SharedFlow<Command> = _commands

    fun onPlaylistClicked(playlist: Playlist) {
        viewModelScope.launch {
            _commands.emit(Command.OpenPlaylist(playlist))
        }
    }

    fun onSortFieldSelected(field: PlaylistSortField) {
        sortState.update { current ->
            if (current.field == field) current else current.copy(field = field)
        }
    }

    fun onSortDirectionSelected(direction: SortDirection) {
        sortState.update { current ->
            if (current.direction == direction) current else current.copy(direction = direction)
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQueryState.update { current ->
            if (current == query) current else query
        }
    }
}