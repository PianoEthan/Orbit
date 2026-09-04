package com.qx.orbit.bili.data.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchApiTest {
    @Test
    fun readsSuggestionsFromResultEnvelope() {
        val json = JsonParser.parseString("""
            {"code":0,"result":{"tag":[
                {"value":"洛天依","name":"<em>洛天依</em>"},
                {"value":"洛天依演唱会"},
                {"value":"洛天依"},
                {"value":""}
            ]}}
        """.trimIndent())
        assertEquals(listOf("洛天依", "洛天依演唱会"), SearchApi.parseSearchSuggestions(json))
    }

    @Test
    fun emptyAndFailedResponsesHaveNoSuggestions() {
        for (response in listOf("""{"code":0,"result":{"tag":[]}}""", """{"code":-400,"message":"请求错误"}""")) {
            assertEquals(emptyList<String>(), SearchApi.parseSearchSuggestions(JsonParser.parseString(response)))
        }
    }
}
