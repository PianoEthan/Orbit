package com.qx.orbit.bili.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

object WbiSigner {

    suspend fun signParams(params: Map<String, String>): Map<String, String> = withContext(Dispatchers.IO) {
        val key = ConfInfoApi.getWbiKey()
        val ts = (System.currentTimeMillis() / 1000).toString()
        val encodedParams = params.map { (k, v) -> 
            val encK = encodeUriComponent(k)
            val encV = encodeUriComponent(v)
            "$encK=$encV"
        }
        val allEntries = encodedParams + "wts=$ts"
        val sortedParams = allEntries.sorted().joinToString("&")
        val wrid = md5(sortedParams + key)
        params + mapOf("w_rid" to wrid, "wts" to ts)
    }

    suspend fun signUrl(url: String): String = withContext(Dispatchers.IO) {
        val key = ConfInfoApi.getWbiKey()
        val separator = if (url.contains("?")) "&" else "?"
        val ts = System.currentTimeMillis() / 1000
        val queryPart = url.substringAfter("?", "")
        val params = if (queryPart.isEmpty()) emptyList() else queryPart.split("&").map {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) {
                // If it is already encoded in the URL, we should decode then encode? 
                // Or just assume it's properly encoded or plain. Let's just use it as is if it's from URL, 
                // but Wbi expects specific encoding. Actually signUrl is rarely used for dynamic queries.
                val k = encodeUriComponent(java.net.URLDecoder.decode(parts[0], "UTF-8"))
                val v = encodeUriComponent(java.net.URLDecoder.decode(parts[1], "UTF-8"))
                "$k=$v"
            } else {
                it
            }
        }
        val sortedParams = (params + "wts=$ts").sorted().joinToString("&")
        val wrid = md5(sortedParams + key)
        "$url${separator}w_rid=$wrid&wts=$ts"
    }

    private fun encodeUriComponent(str: String): String {
        return java.net.URLEncoder.encode(str, "UTF-8")
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")
            .replace("%2A", "*")
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
