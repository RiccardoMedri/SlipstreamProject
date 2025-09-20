package com.cesenahome.domain.session

import com.cesenahome.domain.models.SessionData

interface SessionStore {
    suspend fun save(session: SessionData)
    suspend fun load(): SessionData?
    suspend fun clear()
}