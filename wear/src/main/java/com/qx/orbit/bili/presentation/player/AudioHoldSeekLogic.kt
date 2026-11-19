package com.qx.orbit.bili.presentation.player

/**
 * 音频播放器「按住快进」的速率曲线。
 *
 * 按住时长 t（秒）时，前进偏移量为速率的积分：
 * rate(t) = min(BASE + ACCEL * t, MAX)，因此
 * - t <= RAMP 段：offset = BASE * t + ACCEL * t^2 / 2
 * - t >  RAMP 段：offset = 封顶前的积分 + MAX * (t - RAMP)
 *
 * 意义：误触按住 0.5 秒只前进约 3 秒；按住 2 秒约 +24 秒；
 * 按住 4.5 秒速率封顶（40 倍）并继续线性前进，既能微调也能大跳。
 */
internal const val AUDIO_HOLD_SEEK_BASE_RATE = 4_000.0
internal const val AUDIO_HOLD_SEEK_ACCEL = 8_000.0
internal const val AUDIO_HOLD_SEEK_MAX_RATE = 40_000.0

/** 速率从 [AUDIO_HOLD_SEEK_BASE_RATE] 涨到 [AUDIO_HOLD_SEEK_MAX_RATE] 所需的按住秒数。 */
private val rampSeconds: Double
    get() = (AUDIO_HOLD_SEEK_MAX_RATE - AUDIO_HOLD_SEEK_BASE_RATE) / AUDIO_HOLD_SEEK_ACCEL

/** 按住 [heldMs] 毫秒时应向前跳过的偏移量（毫秒），非负。 */
internal fun audioHoldSeekOffsetMs(heldMs: Long): Long {
    if (heldMs <= 0L) return 0L
    val seconds = heldMs / 1000.0
    val offsetMs = if (seconds <= rampSeconds) {
        AUDIO_HOLD_SEEK_BASE_RATE * seconds +
            AUDIO_HOLD_SEEK_ACCEL * seconds * seconds / 2.0
    } else {
        val rampOffsetMs = AUDIO_HOLD_SEEK_BASE_RATE * rampSeconds +
            AUDIO_HOLD_SEEK_ACCEL * rampSeconds * rampSeconds / 2.0
        rampOffsetMs + AUDIO_HOLD_SEEK_MAX_RATE * (seconds - rampSeconds)
    }
    return offsetMs.toLong().coerceAtLeast(0L)
}

/**
 * 按住快进/快退松手后的落点：以起手时的 [startMs] 为锚点平移 [deltaMs]（正数快进、负数快退），
 * 并钳制在 0..[durationMs]。时长未知（<= 0）时保持原位置，与 ViewModel 的 seek 守卫一致。
 */
internal fun audioHoldSeekTargetMs(startMs: Long, deltaMs: Long, durationMs: Long): Long {
    if (durationMs <= 0L) return startMs.coerceIn(0L, Long.MAX_VALUE)
    val start = startMs.coerceIn(0L, durationMs)
    return start + deltaMs.coerceIn(-start, durationMs - start)
}
