package com.qx.orbit.bili.core.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun JsonElement?.asJsonObjectOrNull(): JsonObject? =
    this?.takeIf { it.isJsonObject }?.asJsonObject

internal fun JsonObject.objectValue(name: String): JsonObject? = get(name).asJsonObjectOrNull()

internal fun JsonObject.arrayValue(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray

internal fun JsonObject.text(name: String): String =
    get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }.orEmpty()

internal fun JsonObject.longValue(name: String): Long =
    get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asLong }.getOrNull() } ?: 0L

internal fun JsonObject.intValue(name: String): Int =
    get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asInt }.getOrNull() } ?: 0

internal fun JsonObject.booleanValue(name: String, default: Boolean = false): Boolean =
    get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asBoolean }.getOrNull() } ?: default

internal fun JsonElement?.asIntOrNullSafe(): Int? =
    this?.takeUnless { it.isJsonNull }?.let { runCatching { it.asInt }.getOrNull() }
