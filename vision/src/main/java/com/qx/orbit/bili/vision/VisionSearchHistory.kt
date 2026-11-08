package com.qx.orbit.bili.vision

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object VisionSearchHistory {
    private const val PREFS_NAME = "vision_search_history"
    private const val KEY_HISTORY = "history_keywords"

    var history by mutableStateOf<List<String>>(emptyList())
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, "").orEmpty()
        history = if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun add(context: Context, keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val updated = (listOf(trimmed) + history.filterNot { it == trimmed }).take(20)
        history = updated
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, updated.joinToString("\n"))
            .apply()
    }

    fun remove(context: Context, keyword: String) {
        val updated = history.filterNot { it == keyword }
        history = updated
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, updated.joinToString("\n"))
            .apply()
    }

    fun clear(context: Context) {
        history = emptyList()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }
}
