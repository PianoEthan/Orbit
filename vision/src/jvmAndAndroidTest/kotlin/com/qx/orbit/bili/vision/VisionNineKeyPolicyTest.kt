package com.qx.orbit.bili.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisionNineKeyPolicyTest {
    @Test
    fun directionSelectsEveryLetterWithoutCycling() {
        assertEquals(9, visionLetterGroups.size)
        assertEquals(('A'..'Z').toSet(), visionLetterGroups.drop(1).joinToString("").toSet())
        assertEquals("P", nineKeyCharacter("PQRS", NineKeyDirection.UP))
        assertEquals("Q", nineKeyCharacter("PQRS", NineKeyDirection.RIGHT))
        assertEquals("R", nineKeyCharacter("PQRS", NineKeyDirection.DOWN))
        assertEquals("S", nineKeyCharacter("PQRS", NineKeyDirection.LEFT))
        assertNull(nineKeyCharacter("ABC", NineKeyDirection.LEFT))
    }

    @Test
    fun deletePreservesUnicodeCharacters() {
        assertEquals("", deleteSearchCharacter(""))
        assertEquals("搜索", deleteSearchCharacter("搜索A"))
        assertEquals("搜索", deleteSearchCharacter("搜索\uD83D\uDE00"))
    }
}
