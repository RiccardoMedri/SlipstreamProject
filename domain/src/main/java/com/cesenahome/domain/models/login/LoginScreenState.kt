package com.cesenahome.domain.models.login

data class LoginScreenState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val error: String? = null,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = ""
)
