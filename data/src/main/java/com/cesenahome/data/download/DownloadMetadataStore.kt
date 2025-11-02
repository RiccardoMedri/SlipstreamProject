package com.cesenahome.data.download

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.downloadMetadataDataStore by preferencesDataStore(name = "download_metadata")

internal class DownloadMetadataStore(
    private val context: Context,
) {

    private object Keys {
        val albums = stringPreferencesKey("albums")
        val playlists = stringPreferencesKey("playlists")
    }

    //Both map the DataStore data to the decoded maps
    val albumSongsFlow: Flow<Map<String, Set<String>>> =
        context.downloadMetadataDataStore.data.map { prefs ->
            prefs[Keys.albums]?.let { deserialize(it) } ?: emptyMap()
        }
    val playlistSongsFlow: Flow<Map<String, Set<String>>> =
        context.downloadMetadataDataStore.data.map { prefs ->
            prefs[Keys.playlists]?.let { deserialize(it) } ?: emptyMap()
        }

    //Read the current snapshot and return the set of song IDs for that collection
    suspend fun getAlbumSongs(albumId: String): Set<String> {
        val map = context.downloadMetadataDataStore.data.first()[Keys.albums]?.let { deserialize(it) } ?: emptyMap()
        return map[albumId] ?: emptySet()
    }
    suspend fun getPlaylistSongs(playlistId: String): Set<String> {
        val map = context.downloadMetadataDataStore.data.first()[Keys.playlists]?.let { deserialize(it) } ?: emptyMap()
        return map[playlistId] ?: emptySet()
    }

    //Read current map, update that one entry and write back the serialized string
    suspend fun setAlbumSongs(albumId: String, songIds: Set<String>) {
        context.downloadMetadataDataStore.edit { prefs ->
            val current = prefs[Keys.albums]?.let { deserialize(it).toMutableMap() } ?: mutableMapOf()
            current[albumId] = songIds
            prefs[Keys.albums] = serialize(current)
        }
    }
    suspend fun setPlaylistSongs(playlistId: String, songIds: Set<String>) {
        context.downloadMetadataDataStore.edit { prefs ->
            val current = prefs[Keys.playlists]?.let { deserialize(it).toMutableMap() } ?: mutableMapOf()
            current[playlistId] = songIds
            prefs[Keys.playlists] = serialize(current)
        }
    }

    //Remove the entry; if the resulting map is empty, the preference key itself is removed
    suspend fun removeAlbum(albumId: String) {
        context.downloadMetadataDataStore.edit { prefs ->
            val current = prefs[Keys.albums]?.let { deserialize(it).toMutableMap() } ?: mutableMapOf()
            current.remove(albumId)
            if (current.isEmpty()) {
                prefs.remove(Keys.albums)
            } else {
                prefs[Keys.albums] = serialize(current)
            }
        }
    }
    suspend fun removePlaylist(playlistId: String) {
        context.downloadMetadataDataStore.edit { prefs ->
            val current = prefs[Keys.playlists]?.let { deserialize(it).toMutableMap() } ?: mutableMapOf()
            current.remove(playlistId)
            if (current.isEmpty()) {
                prefs.remove(Keys.playlists)
            } else {
                prefs[Keys.playlists] = serialize(current)
            }
        }
    }

    //Decode full maps
    suspend fun getAllAlbumEntries(): Map<String, Set<String>> {
        val prefs = context.downloadMetadataDataStore.data.first()
        return prefs[Keys.albums]?.let { deserialize(it) } ?: emptyMap()
    }
    suspend fun getAllPlaylistEntries(): Map<String, Set<String>> {
        val prefs = context.downloadMetadataDataStore.data.first()
        return prefs[Keys.playlists]?.let { deserialize(it) } ?: emptyMap()
    }

    private fun serialize(map: Map<String, Set<String>>): String =
        map.entries.joinToString(separator = "|") { (key, values) ->
            buildString {
                append(key.replace("|", ""))
                append(':')
                append(values.joinToString(separator = ",") { it.replace(",", "") })
            }
        }

    private fun deserialize(raw: String): Map<String, Set<String>> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('|')
            .filter { it.contains(':') }
            .associate { entry ->
                val parts = entry.split(':', limit = 2)
                val key = parts[0]
                val values = parts.getOrNull(1)
                    ?.split(',')
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?: emptySet()
                key to values
            }
    }
}