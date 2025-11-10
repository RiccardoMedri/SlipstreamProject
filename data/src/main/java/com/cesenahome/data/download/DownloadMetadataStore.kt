package com.cesenahome.data.download

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.downloadMetadataDataStore by preferencesDataStore(name = "download_metadata")

internal class DownloadMetadataStore(
    private val context: Context,
) {

    private companion object {
        private const val ALBUM_PREFIX = "albums:"
        private const val PLAYLIST_PREFIX = "playlists:"
    }

    private fun albumKey(id: String) = stringSetPreferencesKey("$ALBUM_PREFIX$id")
    private fun playlistKey(id: String) = stringSetPreferencesKey("$PLAYLIST_PREFIX$id")

    private fun filterEntries(
        prefs: Preferences,
        prefix: String,
    ): Map<String, Set<String>> {
        return buildMap {
            prefs.asMap().forEach { (key, value) ->
                if (key.name.startsWith(prefix)) {
                    val id = key.name.removePrefix(prefix)
                    @Suppress("UNCHECKED_CAST")
                    val songs = value as? Set<String> ?: emptySet()
                    put(id, songs)
                }
            }
        }
    }

    // Flows of aggregated maps
    val albumSongsFlow: Flow<Map<String, Set<String>>> =
        context.downloadMetadataDataStore.data.map { prefs ->
            filterEntries(prefs, ALBUM_PREFIX)
        }

    val playlistSongsFlow: Flow<Map<String, Set<String>>> =
        context.downloadMetadataDataStore.data.map { prefs ->
            filterEntries(prefs, PLAYLIST_PREFIX)
        }

    // Point lookups
    suspend fun getAlbumSongs(albumId: String): Set<String> {
        val key = albumKey(albumId)
        return context.downloadMetadataDataStore.data.first()[key] ?: emptySet()
    }

    suspend fun getPlaylistSongs(playlistId: String): Set<String> {
        val key = playlistKey(playlistId)
        return context.downloadMetadataDataStore.data.first()[key] ?: emptySet()
    }

    // Writes
    suspend fun setAlbumSongs(albumId: String, songIds: Set<String>) {
        context.downloadMetadataDataStore.edit { prefs ->
            val key = albumKey(albumId)
            if (songIds.isEmpty()) prefs.remove(key) else prefs[key] = songIds
        }
    }

    suspend fun setPlaylistSongs(playlistId: String, songIds: Set<String>) {
        context.downloadMetadataDataStore.edit { prefs ->
            val key = playlistKey(playlistId)
            if (songIds.isEmpty()) prefs.remove(key) else prefs[key] = songIds
        }
    }

    // Removes
    suspend fun removeAlbum(albumId: String) {
        context.downloadMetadataDataStore.edit { it.remove(albumKey(albumId)) }
    }

    suspend fun removePlaylist(playlistId: String) {
        context.downloadMetadataDataStore.edit { it.remove(playlistKey(playlistId)) }
    }

    // Aggregated snapshots
    suspend fun getAllAlbumEntries(): Map<String, Set<String>> {
        val prefs = context.downloadMetadataDataStore.data.first()
        return filterEntries(prefs, ALBUM_PREFIX)
    }

    suspend fun getAllPlaylistEntries(): Map<String, Set<String>> {
        val prefs = context.downloadMetadataDataStore.data.first()
        return filterEntries(prefs, PLAYLIST_PREFIX)
    }
}
