package com.qx.orbit.bili.util

import com.qx.orbit.bili.data.remote.HttpClient
import okhttp3.Request
import java.net.URLDecoder

object LinkResolver {
    fun resolveB23Link(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = HttpClient.client.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            response.close()
            
            val bvMatch = Regex("BV[A-Za-z0-9]+", RegexOption.IGNORE_CASE).find(finalUrl)
            bvMatch?.value
        } catch (_: Exception) {
            null
        }
    }
}
