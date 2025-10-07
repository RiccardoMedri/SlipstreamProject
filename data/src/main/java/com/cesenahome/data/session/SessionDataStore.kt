package com.cesenahome.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cesenahome.domain.models.login.SessionData
import com.cesenahome.domain.session.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session_preferences")

class SessionDataStore(
    private val context: Context
): SessionStore {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
    }

    override suspend fun save(session: SessionData) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = session.serverUrl
            prefs[Keys.ACCESS_TOKEN] = session.accessToken
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.USER_NAME] = session.userName.orEmpty()
        }
    }

    override suspend fun load(): SessionData? {
        val prefs = context.dataStore.data.map { it }.first()
        val server = prefs[Keys.SERVER_URL]
        val token = prefs[Keys.ACCESS_TOKEN]
        val userId = prefs[Keys.USER_ID]
        if (server.isNullOrBlank() || token.isNullOrBlank() || userId.isNullOrBlank()) return null
        val name = prefs[Keys.USER_NAME]
        return SessionData(serverUrl = server, accessToken = token, userId = userId, userName = name)
    }

    override suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}