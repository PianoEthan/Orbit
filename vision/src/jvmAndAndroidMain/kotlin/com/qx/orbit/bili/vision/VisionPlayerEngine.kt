package com.qx.orbit.bili.vision

internal fun supportsVisionIjk(abis: List<String>): Boolean = abis.any { it == "arm64-v8a" || it == "armeabi-v7a" }

internal fun effectiveVisionEngine(requested: VisionPlayerEngine, abis: List<String>): VisionPlayerEngine =
    if (requested == VisionPlayerEngine.IJK && !supportsVisionIjk(abis)) VisionPlayerEngine.MEDIA3 else requested
