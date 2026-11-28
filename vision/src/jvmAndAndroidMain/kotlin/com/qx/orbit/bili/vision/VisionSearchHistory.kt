package com.qx.orbit.bili.vision

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object VisionSearchHistory {
    private const val PREFS_NAME = "vision_search_history"
    private const val KEY_HISTORY = "history_keywords"

    var history by mutableStateOf<List<String>>(emptyList())
        private set

    private var store: VisionStore? = null

    internal fun init(openStore: (String) -> VisionStore) {
        val prefs = openStore(PREFS_NAME)
        store = prefs
        val raw = prefs.getString(KEY_HISTORY, "").orEmpty()
        history = if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun add(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val updated = (listOf(trimmed) + history.filterNot { it == trimmed }).take(20)
        history = updated
        store?.put(KEY_HISTORY to updated.joinToString("\n"))
    }

    fun remove(keyword: String) {
        val updated = history.filterNot { it == keyword }
        history = updated
        store?.put(KEY_HISTORY to updated.joinToString("\n"))
    }

    fun clear() {
        history = emptyList()
        store?.remove(KEY_HISTORY)
    }
}
