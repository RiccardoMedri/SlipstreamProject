package com.cesenahome.domain.repository

import com.cesenahome.domain.models.Song

interface SongsRepository {
    /** Returns ALL songs alphabetically by name (A→Z). */
    suspend fun getAllSongsAlphabetical(): List<Song>
}