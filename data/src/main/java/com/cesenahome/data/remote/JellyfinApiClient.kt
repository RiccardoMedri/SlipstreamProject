package com.cesenahome.data.remote

import android.content.Context
import com.cesenahome.data.remote.media.JellyfinMediaClient
import com.cesenahome.data.remote.playlist.JellyfinPlaylistClient
import com.cesenahome.data.remote.session.JellyfinSessionManager

class JellyfinApiClient(
    appContext: Context,
    clientName: String = "CesenaHome",
    clientVersion: String = "0.1.0",
) {
    private val clientFactory = JellyfinClientFactory(
        appContext = appContext,
        clientName = clientName,
        clientVersion = clientVersion,
    )

    val session: JellyfinSessionManager = JellyfinSessionManager(clientFactory)

    val media: JellyfinMediaClient = JellyfinMediaClient(
        clientFactory = clientFactory,
        sessionManager = session,
    )

    val playlist: JellyfinPlaylistClient = JellyfinPlaylistClient(
        clientFactory = clientFactory,
        sessionManager = session
    )
}
