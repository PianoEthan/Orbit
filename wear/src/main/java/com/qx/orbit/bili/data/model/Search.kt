package com.qx.orbit.bili.data.model

import com.google.gson.JsonElement

data class SearchData(
    val seid: String = "",
    val result: List<SearchResultGroup> = emptyList()
)

data class SearchResultGroup(
    val result_type: String = "",
    val data: List<JsonElement> = emptyList()
)
