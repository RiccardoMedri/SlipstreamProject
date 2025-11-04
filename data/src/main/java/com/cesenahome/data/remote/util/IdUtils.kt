package com.cesenahome.data.remote.util

import org.jellyfin.sdk.model.UUID

fun String?.parseUuidOrNull(): UUID? = this
    ?.takeIf { it.isNotBlank() }
    ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }

fun String?.takeIfNotBlank(): String? = this?.takeIf { it.isNotBlank() }