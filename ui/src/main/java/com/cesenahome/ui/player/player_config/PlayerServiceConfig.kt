package com.cesenahome.ui.player

object PlayerServiceConfig {
    const val CHANNEL_ID = "playback"
    const val SESSION_ID = "slipstream_session"
    const val CACHE_SIZE = 250
    const val SEEK_BACK_MS = 10_000L
    const val SEEK_FORWARD_MS = 30_000L
    const val SHUFFLE_PRIME_BATCH = 10
    const val SHUFFLE_BUFFER_TARGET = 25
    const val SHUFFLE_HISTORY_LIMIT = 20
    const val SHUFFLE_RANDOM_ATTEMPTS = 40
    const val RANDOM_QUEUE_TARGET_SIZE = 25
    const val RANDOM_QUEUE_ATTEMPT_MULTIPLIER = 4
}