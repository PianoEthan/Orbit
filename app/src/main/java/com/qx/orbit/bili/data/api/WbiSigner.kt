package com.qx.orbit.bili.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

object WbiSigner {

    suspend fun signParams(params: Map<String, String>): Map<String, String> = withContext(Dispatchers.IO) {
        val key = ConfInfoApi.getWbiKey()
        val ts = (System.currentTimeMillis() / 1000).toString()
        val allEntries = params.entries.map { "${it.key}=${it.value}" } + "wts=$ts"
        val sortedParams = allEntries.sorted().joinToString("&")
        val wrid = md5(sortedParams + key)
        params + mapOf("w_rid" to wrid, "wts" to ts)
    }

    suspend fun signUrl(url: String): String = withContext(Dispatchers.IO) {
        val key = ConfInfoApi.getWbiKey()
        val separator = if (url.contains("?")) "&" else "?"
        val ts = System.currentTimeMillis() / 1000
        val params = Regex("[?&]([^&]+)").findAll(url).map { it.groupValues[1] }.toList()
        val sortedParams = (params + "wts=$ts").sorted().joinToString("&")
        val wrid = md5(sortedParams + key)
        "$url${separator}w_rid=$wrid&wts=$ts"
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
