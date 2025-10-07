package com.cesenahome.data.repository


import com.cesenahome.data.remote.JellyfinApiClient
import com.cesenahome.domain.models.homepage.HomeDestination
import com.cesenahome.domain.models.homepage.HomeMenuItem
import com.cesenahome.domain.models.homepage.LibraryCounts
import com.cesenahome.domain.repository.HomepageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class HomepageRepositoryImpl(
    private val jellyfinApiClient: JellyfinApiClient
) : HomepageRepository {

    override suspend fun getHomepageMenu(): List<HomeMenuItem> = withContext(Dispatchers.Default) {
        listOf(
            HomeMenuItem(id = UUID.randomUUID().toString(), title = "Artists",   destination = HomeDestination.ARTISTS),
            HomeMenuItem(id = UUID.randomUUID().toString(), title = "Albums",    destination = HomeDestination.ALBUMS),
            HomeMenuItem(id = UUID.randomUUID().toString(), title = "Playlists", destination = HomeDestination.PLAYLISTS),
            HomeMenuItem(id = UUID.randomUUID().toString(), title = "Songs",     destination = HomeDestination.SONGS),
        )
    }

    override suspend fun getLibraryCounts(): Result<LibraryCounts> = withContext(Dispatchers.IO) {
        runCatching {
            val artists   = jellyfinApiClient.getArtistsCount()
            val albums    = jellyfinApiClient.getAlbumsCount()
            val playlists = jellyfinApiClient.getPlaylistsCount()
            val songs     = jellyfinApiClient.getSongsCount()
            LibraryCounts(artists, albums, playlists, songs)
        }
    }
}