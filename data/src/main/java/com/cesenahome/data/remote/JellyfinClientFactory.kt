package com.cesenahome.data.remote

import android.content.Context
import com.cesenahome.data.remote.util.normalizeServerUrl
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import java.util.concurrent.atomic.AtomicReference

class JellyfinClientFactory(
    appContext: Context,
    clientName: String = "Slipstream",
    clientVersion: String = "0.1.0",
) {

    //Builds the top-level Jellyfin runtime
    //It wires the Android context and sets the identification metadata presented to the server
    private val jellyfin: Jellyfin = createJellyfin {
        context = appContext
        clientInfo = ClientInfo(name = clientName, version = clientVersion)
    }

    //A thread-safe slot that hold the currently configured ApiClient
    private val apiClientRef = AtomicReference<ApiClient?>()

    //Create or replace the current ApiClient when the user selects/enters a server
    //Calls createApi to create a new ApiClient and stores it in apiClientRef atomically
    fun initializeOrUpdateClient(serverUrl: String) {
        val base = normalizeServerUrl(serverUrl)
        apiClientRef.set(jellyfin.createApi(baseUrl = base))
    }

    fun currentApi(): ApiClient? = apiClientRef.get()

    fun requireApi(): ApiClient = apiClientRef.get() ?: error("ApiClient not initialized")

    //Blank out any bearer token on the current client leaving the base URL intact
    //Useful during logout, without discarding the server URL (an user can log in again without retyping the server)
    fun clearSession() {
        apiClientRef.get()?.update(accessToken = null)
    }
}