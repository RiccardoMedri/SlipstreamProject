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
import com.cesenahome.domain.models.misc.SortDirection
import com.cesenahome.domain.models.song.QueueSong
import com.cesenahome.domain.usecases.favourites.AddSongToFavouritesUseCase
import com.cesenahome.domain.usecases.libraries.GetPagedSongsUseCase
import com.cesenahome.domain.models.misc.DownloadCollectionTarget
import com.cesenahome.domain.usecases.download.ObserveDownloadedAlbumIdsUseCase
import com.cesenahome.domain.usecases.download.ObserveDownloadedPlaylistIdsUseCase
import com.cesenahome.domain.usecases.download.ObserveDownloadedSongIdsUseCase
import com.cesenahome.domain.usecases.ToggleCollectionDownloadUseCase
import com.cesenahome.domain.usecases.playback.GetRandomSongUseCase
import com.cesenahome.ui.player.PlayerServiceConfig.RANDOM_QUEUE_ATTEMPT_MULTIPLIER
import com.cesenahome.ui.player.PlayerServiceConfig.RANDOM_QUEUE_TARGET_SIZE
import com.cesenahome.ui.player.toQueueSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SongsViewModel(
    private val getPagedSongsUseCase: GetPagedSongsUseCase,
    private val addSongToFavouritesUseCase: AddSongToFavouritesUseCase,
    private val observeDownloadedSongIdsUseCase: ObserveDownloadedSongIdsUseCase,
    private val observeDownloadedAlbumIdsUseCase: ObserveDownloadedAlbumIdsUseCase,
    private val observeDownloadedPlaylistIdsUseCase: ObserveDownloadedPlaylistIdsUseCase,
    private val toggleCollectionDownloadUseCase: ToggleCollectionDownloadUseCase,
    private val getRandomSongUseCase: GetRandomSongUseCase,
    private val albumId: String? = null,
    private val playlistId: String? = null,
) : ViewModel() {

    private val sortOptionState = MutableStateFlow(SongSortOption())
    private val searchQueryState = MutableStateFlow("")
    val sortState: StateFlow<SongSortOption> = sortOptionState.asStateFlow()
    val searchQuery: StateFlow<String> = searchQueryState.asStateFlow()
    private val favouriteOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val downloadedSongIds = observeDownloadedSongIdsUseCase()
    private val downloadedAlbumIds = observeDownloadedAlbumIdsUseCase()
    private val downloadedPlaylistIds = observeDownloadedPlaylistIdsUseCase()
    private val downloadOperationInProgress = MutableStateFlow(false)
    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 1)
    val commands: SharedFlow<Command> = _commands
    private val _favouriteEvents = MutableSharedFlow<FavouriteEvent>(extraBufferCapacity = 1)
    val favouriteEvents: SharedFlow<FavouriteEvent> = _favouriteEvents
    private val _downloadEvents = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 1)
    val downloadEvents: SharedFlow<DownloadEvent> = _downloadEvents

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

    val pagedSongs: Flow<PagingData<Song>> = combine(
        basePagedSongs,
        favouriteOverrides,
        downloadedSongIds,
    ) { pagingData, overrides, downloadedIds ->
        pagingData.map { song ->
            val override = overrides[song.id]
            val adjustedFavourite = if (override == null) song else song.copy(isFavorite = override)
            val isDownloaded = downloadedIds.contains(adjustedFavourite.id)
            if (adjustedFavourite.isDownloaded == isDownloaded) {
                adjustedFavourite
            } else {
                adjustedFavourite.copy(isDownloaded = isDownloaded)
            }
        }
    }

    sealed interface Command {
        data class PlaySong(val song: Song, val queueSongs: List<QueueSong>, val selectedIndex: Int) : Command
        data class AddSongToQueue(val song: Song) : Command
        data class PlaySongNext(val song: Song) : Command
    }

    sealed interface FavouriteEvent {
        data class Success(val song: Song, val isFavourite: Boolean) : FavouriteEvent
        data class Failure(val song: Song, val isFavourite: Boolean, val reason: String?) : FavouriteEvent
    }

    data class CollectionDownloadState(val isDownloaded: Boolean, val inProgress: Boolean)

    sealed interface DownloadEvent {
        data class Success(val downloaded: Boolean) : DownloadEvent
        data class Failure(val reason: String?) : DownloadEvent
    }

    val collectionDownloadState: StateFlow<CollectionDownloadState?> = combine(
        downloadedAlbumIds,
        downloadedPlaylistIds,
        downloadOperationInProgress,
    ) { albumIds, playlistIds, inProgress ->
        when {
            albumId != null -> CollectionDownloadState(albumIds.contains(albumId), inProgress)
            playlistId != null -> CollectionDownloadState(playlistIds.contains(playlistId), inProgress)
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onSongClicked(song: Song, visibleSongs: List<Song>) {
        viewModelScope.launch {
            val queueSongs = buildQueueSongs(visibleSongs, song)
            val selectedIndex = queueSongs.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
            _commands.emit(Command.PlaySong(song, queueSongs, selectedIndex))
        }
    }

    fun onAddToQueueRequested(song: Song) {
        viewModelScope.launch {
            _commands.emit(Command.AddSongToQueue(song))
        }
    }

    fun onPlayNextRequested(song: Song) {
        viewModelScope.launch {
            _commands.emit(Command.PlaySongNext(song))
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

    fun onToggleDownloadRequested() {
        if (downloadOperationInProgress.value) return
        val state = collectionDownloadState.value ?: return
        val target = when {
            albumId != null -> DownloadCollectionTarget.Album(albumId)
            playlistId != null -> DownloadCollectionTarget.Playlist(playlistId)
            else -> null
        } ?: return
        val shouldDownload = !state.isDownloaded
        downloadOperationInProgress.value = true
        viewModelScope.launch {
            val result = toggleCollectionDownloadUseCase(target, shouldDownload)
            result.fold(
                onSuccess = {
                    _downloadEvents.emit(DownloadEvent.Success(shouldDownload))
                },
                onFailure = { error ->
                    _downloadEvents.emit(DownloadEvent.Failure(error.message))
                }
            )
            downloadOperationInProgress.value = false
        }
    }

    private suspend fun buildQueueSongs(visibleSongs: List<Song>, selectedSong: Song): List<QueueSong> {
        val queueSongs = ArrayList<QueueSong>(visibleSongs.size + 1)
        val existingIds = mutableSetOf<String>()

        visibleSongs.forEach { snapshotSong ->
            queueSongs += snapshotSong.toQueueSong().also { existingIds += it.id }
        }

        if (existingIds.add(selectedSong.id) && queueSongs.none { it.id == selectedSong.id }) {
            queueSongs.add(selectedSong.toQueueSong())
        }

        if (shouldAppendRandomSongs(queueSongs.size)) {
            queueSongs += fetchRandomQueueSongs(existingIds, RANDOM_QUEUE_TARGET_SIZE - queueSongs.size)
        }

        return queueSongs
    }

    private fun shouldAppendRandomSongs(currentQueueSize: Int): Boolean {
        val isInAllSongsView = albumId == null && playlistId == null
        val isSearching = searchQueryState.value.isNotBlank()
        return isInAllSongsView && isSearching && currentQueueSize < RANDOM_QUEUE_TARGET_SIZE
    }

    private suspend fun fetchRandomQueueSongs(
        existingIds: MutableSet<String>,
        required: Int,
    ): List<QueueSong> {
        if (required <= 0) return emptyList()
        val randomSongs = ArrayList<QueueSong>(required)
        var attempts = 0
        val maxAttempts = required * RANDOM_QUEUE_ATTEMPT_MULTIPLIER
        while (randomSongs.size < required && attempts < maxAttempts) {
            attempts++
            val song = getRandomSongUseCase().getOrNull() ?: continue
            if (!existingIds.add(song.id)) continue
            randomSongs += song.toQueueSong()
        }
        return randomSongs
    }
}