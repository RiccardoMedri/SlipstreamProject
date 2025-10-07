package com.cesenahome.domain.models.login

data class SessionData(
    val serverUrl: String,
    val accessToken: String,
    val userId: String,
    val userName: String?
)