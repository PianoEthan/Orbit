package com.qx.orbit.bili.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 离线缓存本地播放历史记录存储
 * 支持以 cid 或 localUri 为键，持久化记录本地视频播放进度与时长。
 */
object LocalPlaybackHistoryStore {
    private const val PREFS_NAME = "orbit_local_playback_history"
    private const val KEY_PROGRESS_SUFFIX = "_progress"
    private const val KEY_DURATION_SUFFIX = "_duration"
    private const val KEY_TIMESTAMP_SUFFIX = "_timestamp"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun init(preferences: SharedPreferences) {
        prefs = preferences
    }

    private fun getSharedPrefs(): SharedPreferences? {
        return prefs
    }

    private fun buildKey(cid: Long, localUri: String?): String {
        return if (cid > 0L) {
            "cid_$cid"
        } else if (!localUri.isNullOrBlank()) {
            "uri_${localUri.hashCode()}"
        } else {
            ""
        }
    }

    /**
     * 保存本地播放进度
     * 当视频播放到末尾（超过98%或距结束不足3秒）时，自动重置进度为0，下次从头播放。
     */
    fun saveProgress(cid: Long, localUri: String?, progressMs: Long, durationMs: Long) {
        val key = buildKey(cid, localUri)
        if (key.isEmpty()) return
        val sp = getSharedPrefs() ?: return

        val isFinished = durationMs > 5000L && (progressMs >= durationMs - 3000L || progressMs >= (durationMs * 0.98f).toLong())
        val finalProgress = if (isFinished) 0L else progressMs.coerceAtLeast(0L)

        sp.edit()
            .putLong(key + KEY_PROGRESS_SUFFIX, finalProgress)
            .putLong(key + KEY_DURATION_SUFFIX, durationMs.coerceAtLeast(0L))
            .putLong(key + KEY_TIMESTAMP_SUFFIX, System.currentTimeMillis())
            .apply()
    }

    /**
     * 获取本地播放进度（秒），供起播恢复使用
     */
    fun getProgressSeconds(cid: Long, localUri: String?): Int {
        val ms = getProgressMs(cid, localUri)
        return (ms / 1000L).toInt()
    }

    /**
     * 获取本地播放进度（毫秒）
     */
    fun getProgressMs(cid: Long, localUri: String?): Long {
        val key = buildKey(cid, localUri)
        if (key.isEmpty()) return 0L
        return getSharedPrefs()?.getLong(key + KEY_PROGRESS_SUFFIX, 0L) ?: 0L
    }

    /**
     * 获取本地播放总时长（毫秒）
     */
    fun getDurationMs(cid: Long, localUri: String?): Long {
        val key = buildKey(cid, localUri)
        if (key.isEmpty()) return 0L
        return getSharedPrefs()?.getLong(key + KEY_DURATION_SUFFIX, 0L) ?: 0L
    }

    /**
     * 删除指定视频的本地播放记录
     */
    fun remove(cid: Long, localUri: String?) {
        val key = buildKey(cid, localUri)
        if (key.isEmpty()) return
        getSharedPrefs()?.edit()
            ?.remove(key + KEY_PROGRESS_SUFFIX)
            ?.remove(key + KEY_DURATION_SUFFIX)
            ?.remove(key + KEY_TIMESTAMP_SUFFIX)
            ?.apply()
    }

    /**
     * 清空全部本地播放记录
     */
    fun clear() {
        getSharedPrefs()?.edit()?.clear()?.apply()
    }
}
