package com.qx.orbit.bili.vision

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliHistoryReporter
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.HistoryReporter
import com.qx.orbit.bili.core.api.parseCookieValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class VisionHistoryProgress(val aid: Long, val cid: Long, val seconds: Long, val episodeId: Long = 0L, val seasonId: Long = 0L)

internal class VisionPlaybackHistoryViewModel(
    private val reporter: HistoryReporter = BilibiliHistoryReporter(),
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {
    private var cookie = ""
    private var generation = 0
    private var job: Job? = null
    private val pending = LinkedHashMap<Pair<Long, Long>, VisionHistoryProgress>()
    private var lastSample: VisionHistoryProgress? = null
    private var lastScheduled: VisionHistoryProgress? = null
    private var lastAt = 0L
    private val _syncFailed = MutableStateFlow(false)
    val syncFailed = _syncFailed.asStateFlow()

    fun setAccount(session: String) {
        if (session == cookie) return
        generation++
        job?.cancel()
        job = null
        cookie = session
        pending.clear()
        lastSample = null
        lastScheduled = null
        lastAt = 0
        _syncFailed.value = false
    }

    fun sample(progress: VisionHistoryProgress?, playing: Boolean, force: Boolean = false) {
        if (cookie.isBlank() || parseCookieValue(cookie, "bili_jct").isBlank()) return
        if (progress == null || progress.aid <= 0 || progress.cid <= 0 || progress.seconds < 0) return
        val sameTarget = lastSample?.let { it.aid == progress.aid && it.cid == progress.cid } == true
        if (!playing && !sameTarget) return
        if (!sameTarget) flush()
        lastSample = progress
        if (force || !sameTarget || clock() - lastAt >= 15_000) enqueue(progress)
    }

    fun flush() { lastSample?.let(::enqueue) }

    fun endPlayback() {
        flush()
        lastSample = null
        lastScheduled = null
    }

    private fun enqueue(progress: VisionHistoryProgress) {
        if (progress == lastScheduled) return
        lastScheduled = progress
        lastAt = clock()
        pending[progress.aid to progress.cid] = progress
        if (pending.size > 16) pending.remove(pending.keys.first())
        if (job?.isActive == true) return
        val request = generation
        val session = cookie
        job = viewModelScope.launch {
            while (pending.isNotEmpty() && request == generation) {
                val key = pending.keys.first()
                val next = pending.remove(key) ?: continue
                try {
                    withTimeout(10_000) {
                        val csrf = parseCookieValue(session, "bili_jct")
                        if (next.episodeId > 0) reporter.reportEpisode(CoreVideo(next.aid, "", "", "", "", cid = next.cid, episodeId = next.episodeId, seasonId = next.seasonId), next.seconds, session, csrf)
                        else reporter.report(next.aid, next.cid, next.seconds, session, csrf)
                    }
                    if (request == generation) _syncFailed.value = false
                } catch (_: TimeoutCancellationException) {
                    if (request == generation) _syncFailed.value = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (request == generation) _syncFailed.value = true
                }
            }
        }
    }
}

internal fun historyPositionSeconds(segmentIndex: Int, positionMs: Long, precedingDurationsMs: List<Long>): Long? {
    if (segmentIndex < 0 || precedingDurationsMs.size != segmentIndex || precedingDurationsMs.any { it <= 0 }) return null
    return (precedingDurationsMs.sum() + positionMs.coerceAtLeast(0)) / 1000
}

internal fun resumeHistoryPosition(progressSeconds: Long, durationSeconds: Long): Long =
    if (progressSeconds <= 0 || (durationSeconds > 0 && progressSeconds >= durationSeconds)) 0 else progressSeconds * 1000
