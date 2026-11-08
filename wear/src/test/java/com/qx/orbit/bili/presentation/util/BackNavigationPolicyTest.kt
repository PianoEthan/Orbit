package com.qx.orbit.bili.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder

class BackNavigationPolicyTest {
    @Test
    fun videoPlayerNeverUsesWearSwipe() {
        for (systemOnly in listOf(false, true)) {
            for (miWatch in listOf(false, true)) {
                assertFalse(wearSwipeEnabled(systemOnly, miWatch, true, false))
            }
        }
    }

    @Test
    fun audioKeepsWearSwipeWithDefaultSettings() {
        assertTrue(wearSwipeEnabled(false, false, true, true))
        assertFalse(wearSwipeEnabled(false, true, true, true))
    }

    @Test
    fun systemBackOnlyDisablesWearSwipeAcrossAllDestinations() {
        for (player in listOf(false, true)) {
            for (audio in listOf(false, true)) {
                assertFalse(wearSwipeEnabled(true, false, player, audio))
            }
        }
    }

    @Test
    fun disablingThePreferenceRestoresOrdinaryPageSwipe() {
        assertFalse(wearSwipeEnabled(true, false, false, false))
        assertTrue(wearSwipeEnabled(false, false, false, false))
    }

    @Test
    fun rawAndUrlEncodedAudioRoutesDecodeConsistently() {
        val json = """{"audioUrl":"audio","title":"A+B 测试"}"""
        assertEquals("audio", decodePlayerRouteData(json)?.audioUrl)
        assertEquals("A+B 测试", decodePlayerRouteData(json)?.title)
        val encoded = URLEncoder.encode(json, "UTF-8")
        assertEquals("audio", decodePlayerRouteData(encoded)?.audioUrl)
        assertEquals("A+B 测试", decodePlayerRouteData(encoded)?.title)
    }

    @Test
    fun invalidPlayerRouteDoesNotEnableAudioSwipe() {
        assertNull(decodePlayerRouteData(""))
        assertNull(decodePlayerRouteData("{}broken"))
    }
}
