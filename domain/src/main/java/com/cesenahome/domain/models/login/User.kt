package com.cesenahome.domain.models.login

data class User(
    val userId: String,
    val name: String?,
    val serverUrl: String
)