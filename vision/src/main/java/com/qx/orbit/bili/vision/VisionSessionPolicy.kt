package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CoreUserProfile
import com.qx.orbit.bili.core.api.parseCookieValue

internal fun validVisionSession(cookie: String, mid: Long): Boolean =
    mid > 0 && parseCookieValue(cookie, "DedeUserID").toLongOrNull() == mid && parseCookieValue(cookie, "SESSDATA").isNotBlank()

internal enum class VisionProfileDisposition { ACCEPT, IGNORE, EXPIRED }

internal fun visionProfileDisposition(cookie: String, mid: Long, profile: CoreUserProfile): VisionProfileDisposition = when {
    !validVisionSession(cookie, mid) -> VisionProfileDisposition.IGNORE
    !profile.isLogin -> VisionProfileDisposition.EXPIRED
    profile.mid != mid -> VisionProfileDisposition.IGNORE
    else -> VisionProfileDisposition.ACCEPT
}
