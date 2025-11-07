package com.cesenahome.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.cesenahome.data.paging.PlaylistPagingSource
import com.cesenahome.data.remote.media.JellyfinMediaClient
import com.cesenahome.data.remote.playlist.JellyfinPlaylistClient
import com.cesenahome.data.remote.util.parseUuidOrNull
import com.cesenahome.domain.models.playlist.Playlist
import com.cesenahome.domain.models.playlist.PlaylistPagingRequest
import com.cesenahome.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlin.jvm.Volatile

private const val FAVOURITE_PLAYLIST_NAME = "Favourite Songs"

class PlaylistRepositoryImpl(
    private val mediaClient: JellyfinMediaClient,
    private val playlistClient: JellyfinPlaylistClient,
) : PlaylistRepository {

    //Avoids repeated lookups/creation of "Favourite Song" playlist
    @Volatile
    private var cachedFavouritePlaylistId: String? = null

    override fun pagingPlaylists(
        pageSize: Int,
        request: PlaylistPagingRequest,
    ): Flow<PagingData<Playlist>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize * 2,
                prefetchDistance = pageSize,
                enablePlaceholders = false,
                maxSize = pageSize * 5,
            ),
            pagingSourceFactory = {
                PlaylistPagingSource(
                    mediaClient = mediaClient,
                    pageSize = pageSize,
                    request = request,
                )
            }
        ).flow
    }

    //Checks and ensure existence of F.S. playlist
    //If it's not cached it tries searching it by name otherwise it creates it
    //It then computes and applies the delta between the items at server (new favourite and removed favourite)
    //Cache the result and return it
    override suspend fun ensureFavouritePlaylistId(): Result<String> {
        cachedFavouritePlaylistId?.let { return Result.success(it) }

        val resolveResult = resolveFavouritePlaylistId()
        resolveResult.exceptionOrNull()?.let { return Result.failure(it) }
        val playlistId = resolveResult.getOrNull()
            ?: return Result.failure(IllegalStateException("Unable to resolve favourites playlist identifier"))

        val syncResult = syncFavouritePlaylist(playlistId)
        syncResult.exceptionOrNull()?.let { return Result.failure(it) }

        cachedFavouritePlaylistId = playlistId

        return Result.success(playlistId)
    }

    private suspend fun resolveFavouritePlaylistId(): Result<String> {
        val existingResult = playlistClient.findPlaylistByName(FAVOURITE_PLAYLIST_NAME)
        existingResult.exceptionOrNull()?.let { return Result.failure(it) }

        existingResult.getOrNull()?.id?.toString()
            ?.let { normalizePlaylistId(it) }
            ?.let { return Result.success(it) }

        val createResult = playlistClient.createPlaylist(FAVOURITE_PLAYLIST_NAME)
        createResult.exceptionOrNull()?.let { return Result.failure(it) }

        val createdId = createResult.getOrNull()?.let { normalizePlaylistId(it) }
        if (createdId != null) {
            return Result.success(createdId)
        }

        val refreshedResult = playlistClient.findPlaylistByName(FAVOURITE_PLAYLIST_NAME)
        refreshedResult.exceptionOrNull()?.let { return Result.failure(it) }

        val refreshedId = refreshedResult.getOrNull()?.id?.toString()?.let { normalizePlaylistId(it) }

        return refreshedId?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Playlist creation did not return a valid identifier"))
    }

    //If a user set a song as favourite in Jellyfin it must appear inside the F.P. playlist
    //It fetches all song marked as favourite system-wide and all songs currently in the playlist
    //It then process the differences and keep the favourite items in synch
    private suspend fun syncFavouritePlaylist(playlistId: String): Result<Unit> {
        val favouritesResult = playlistClient.fetchFavouriteSongIds()
        favouritesResult.exceptionOrNull()?.let { return Result.failure(it) }
        val favouriteIds = favouritesResult.getOrNull()?.toSet() ?: emptySet()

        val playlistSongsResult = playlistClient.fetchPlaylistSongIds(playlistId)
        playlistSongsResult.exceptionOrNull()?.let { return Result.failure(it) }
        val playlistSongIds = playlistSongsResult.getOrNull()?.toSet() ?: emptySet()

        val songsToAdd = favouriteIds - playlistSongIds
        val songsToRemove = playlistSongIds - favouriteIds

        if (songsToAdd.isNotEmpty()) {
            val addResult = playlistClient.addSongsToPlaylist(playlistId, songsToAdd.toList())
            addResult.exceptionOrNull()?.let { return Result.failure(it) }
        }

        if (songsToRemove.isNotEmpty()) {
            val removeResult = playlistClient.removeSongsFromPlaylist(playlistId, songsToRemove.toList())
            removeResult.exceptionOrNull()?.let { return Result.failure(it) }
        }

        return Result.success(Unit)
    }

    private fun normalizePlaylistId(rawId: String): String? {
        return rawId.parseUuidOrNull()?.toString()
    }
}
