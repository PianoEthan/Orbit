package com.qx.orbit.bili.util.interaction

import com.qx.orbit.bili.data.model.InteractionQuestion

object InteractionTimeline {
    sealed interface Result

    data class Window(
        val startMs: Long,
        val endMs: Long?,
        val requiresEnded: Boolean,
        val countdownMs: Long?,
        val automatic: Boolean,
        val pauseRequested: Boolean
    ) : Result {
        fun contains(positionMs: Long, ended: Boolean): Boolean =
            (!requiresEnded || ended) && positionMs >= startMs &&
                (endMs == null || positionMs < endMs || endMs == startMs && positionMs == startMs)
    }

    data class Unsupported(val message: String) : Result

    fun window(question: InteractionQuestion, durationMs: Long): Result {
        if (durationMs < 0) return Unsupported("Negative media duration")
        return when (question.type) {
            0 -> if (question.duration <= 0) {
                Window(0, null, true, null, true, false)
            } else {
                Unsupported("Type 0 with positive duration")
            }
            1, 2 -> if (question.duration <= 0) {
                Window(0, null, true, null, false, false)
            } else {
                Window((durationMs - question.duration).coerceAtLeast(0), null, false, question.duration, false, false)
            }
            4 -> {
                if (question.duration < 0 || question.pauseVideo !in 0..1) {
                    return Unsupported("Type 4 duration or pause_video")
                }
                val start = question.startTime ?: question.startTimeR?.let {
                    if (it < 0 || it > durationMs) return Unsupported("Invalid start_time_r")
                    durationMs - it
                } ?: return Unsupported("Type 4 requires start_time or start_time_r")
                if (start < 0 || question.duration > Long.MAX_VALUE - start) {
                    return Unsupported("Invalid type 4 window")
                }
                Window(
                    start, start + question.duration, false,
                    question.duration.takeIf { it > 0 && question.pauseVideo == 0 },
                    false, question.pauseVideo == 1
                )
            }
            else -> Unsupported("Question type ${question.type}")
        }
    }
}
