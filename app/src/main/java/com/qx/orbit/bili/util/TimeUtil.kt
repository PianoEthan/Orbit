package com.qx.orbit.bili.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.toJavaInstant

fun formatBiliTime(timestampSeconds: Long): String {
    val targetInstant = Instant.ofEpochSecond(timestampSeconds)
    val currentInstant = Clock.System.now().toJavaInstant()
    val zone = ZoneId.systemDefault()
    val target = LocalDateTime.ofInstant(targetInstant, zone)
    val current = LocalDateTime.ofInstant(currentInstant, zone)

    val diffMillis = currentInstant.toEpochMilli() - targetInstant.toEpochMilli()
    val diffMinutes = diffMillis / (60 * 1000)
    val diffHours = diffMillis / (60 * 60 * 1000)

    val dayDiff = current.dayOfYear - target.dayOfYear +
            (current.year - target.year) * 365

    return if (current.year == target.year) {
        if (current.dayOfYear == target.dayOfYear) {
            when {
                diffMinutes < 1L -> "刚刚"
                diffMinutes < 60L -> "${diffMinutes}分钟前"
                else -> "${diffHours}小时前"
            }
        } else if (dayDiff == 1) {
            "昨天 ${target.hour.pad()}:${target.minute.pad()}"
        } else if (dayDiff in 2..7) {
            "${dayDiff}天前"
        } else {
            "${target.monthValue}月${target.dayOfMonth}日"
        }
    } else {
        "${target.year}年${target.monthValue}月${target.dayOfMonth}日"
    }
}

private fun Int.pad() = toString().padStart(2, '0')
