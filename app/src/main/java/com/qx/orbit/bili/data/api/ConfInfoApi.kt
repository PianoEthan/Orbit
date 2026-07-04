package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.remote.GsonConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

object ConfInfoApi {
    private var wbiKey: String? = null

    private val api by lazy { BiliApiService.create() }

    suspend fun signWBI(url: String): String = withContext(Dispatchers.IO) {
        WbiSigner.signUrl(url)
    }

    suspend fun getWbiKey(): String {
        wbiKey?.let { return it }
        return fetchWBIKey()
    }

    private suspend fun fetchWBIKey(): String = withContext(Dispatchers.IO) {
        when (val resp = api.getNav()) {
            is com.qx.orbit.bili.data.remote.Result.Success -> {
                val root = resp.data
                if (!root.isJsonObject) return@withContext ""
                val data = root.asJsonObject.get("data")?.asJsonObject ?: return@withContext ""
                val wbiImg = data.get("wbi_img")?.asJsonObject ?: return@withContext ""
                val imgUrl = wbiImg.get("img_url")?.asString ?: return@withContext ""
                val subUrl = wbiImg.get("sub_url")?.asString ?: return@withContext ""
                val rawKey = imgUrl.substringAfterLast("/").substringBefore(".") +
                        subUrl.substringAfterLast("/").substringBefore(".")
                val mixinKeyEncTab = intArrayOf(
                    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
                    27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
                    37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
                    22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
                )
                val key = mixinKeyEncTab.map { rawKey[it] }.joinToString("").take(32)
                wbiKey = key
                key
            }
            is com.qx.orbit.bili.data.remote.Result.Error -> ""
        }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getDateCurr(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 10000 +
                (cal.get(java.util.Calendar.MONTH) + 1) * 100 +
                cal.get(java.util.Calendar.DAY_OF_MONTH)
    }
}
