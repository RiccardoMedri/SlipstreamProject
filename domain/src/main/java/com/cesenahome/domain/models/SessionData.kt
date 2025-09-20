package com.cesenahome.domain.models

data class SessionData(
    val serverUrl: String,
    val accessToken: String,
    val userId: String,
    val userName: String?
)