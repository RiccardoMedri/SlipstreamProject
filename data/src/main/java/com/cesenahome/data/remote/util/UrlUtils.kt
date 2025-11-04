package com.cesenahome.data.remote.util

fun normalizeServerUrl(input: String): String {
    require(input.isNotBlank()) { "Server URL is blank." }
    require(Regex("^https?://.+").matches(input)) { "Server URL must start with http:// or https://." }
    return if (input.endsWith('/')) input else "$input/"
}