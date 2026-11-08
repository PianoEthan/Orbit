package com.qx.orbit.bili.vision

internal val visionLetterGroups = listOf(".-_", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")

internal enum class NineKeyDirection { UP, RIGHT, DOWN, LEFT }

internal fun nineKeyCharacter(group: String, direction: NineKeyDirection): String? =
    group.getOrNull(direction.ordinal)?.toString()

internal fun deleteSearchCharacter(text: String): String =
    if (text.isEmpty()) text else text.substring(0, text.offsetByCodePoints(text.length, -1))
