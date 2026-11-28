package com.qx.orbit.bili.vision

import com.qx.orbit.bili.core.api.CoreUserProfile
import org.junit.Assert.*
import org.junit.Test

class VisionSessionPolicyTest {
    private val session = "DedeUserID=123; SESSDATA=fake; bili_jct=fake-csrf"
    private val profile = CoreUserProfile(123, "用户", "", 0.0, 1, true)

    @Test fun onlyRestoresMatchingUidWithSessionCredential() {
        assertTrue(validVisionSession(session, 123))
        assertFalse(validVisionSession(session, 456))
        assertFalse(validVisionSession("DedeUserID=123", 123))
        assertFalse(validVisionSession("SESSDATA=fake", 123))
        assertFalse(validVisionSession(session, 0))
    }

    @Test fun expiredResponseIsDistinctFromMismatchedOrLateAccountResponse() {
        assertEquals(VisionProfileDisposition.ACCEPT, visionProfileDisposition(session, 123, profile))
        assertEquals(VisionProfileDisposition.EXPIRED, visionProfileDisposition(session, 123, profile.copy(isLogin = false, mid = 0)))
        assertEquals(VisionProfileDisposition.IGNORE, visionProfileDisposition(session, 123, profile.copy(mid = 456)))
        assertEquals(VisionProfileDisposition.IGNORE, visionProfileDisposition("", 0, profile))
    }
}
