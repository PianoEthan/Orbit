package com.qx.orbit.bili.data.sign

import java.net.URLEncoder
import java.security.MessageDigest

object AppSignUtil {
    const val APP_KEY_HD = "dfca71928277209b"
    const val APP_SEC_HD = "b5475a8825547a4fc26c7d518eaaa02e"

    const val HD_USER_AGENT = "Mozilla/5.0 BiliDroid/2.0.1 (bbcallen@gmail.com) os/android model/android_hd mobi_app/android_hd build/2001100 channel/master innerVer/2001100 osVer/15 network/2"

    fun sign(
        params: MutableMap<String, String>,
        appKey: String = APP_KEY_HD,
        appSecret: String = APP_SEC_HD,
    ): Map<String, String> {
        params["appkey"] = appKey
        params["ts"] = (System.currentTimeMillis() / 1000).toString()
        val sorted = params.keys.sorted()
        val raw = sorted.joinToString("&") { key ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(params[key]!!, "UTF-8")
        }
        params["sign"] = md5(raw + appSecret)
        return params
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
