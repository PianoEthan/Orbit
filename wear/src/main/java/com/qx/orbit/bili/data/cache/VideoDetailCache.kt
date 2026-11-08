package com.qx.orbit.bili.data.cache

import com.qx.orbit.bili.data.api.VideoInfoApi
import com.qx.orbit.bili.data.model.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * 点击卡片时已知的展示信息，用于详情页首帧直接画出真实标题与封面，而不是转圈。
 */
data class VideoPreview(
    val title: String = "",
    val cover: String = "",
    val upName: String = ""
)

/**
 * 视频详情的内存缓存 + 请求去重。
 *
 * - [info]：LRU 保存最近打开过的详情，返回重进/相关推荐互跳直接命中，不再转圈
 * - [preview]：点击处的标题与封面，详情页首帧先画真内容
 * - 在途请求按 bvid 去重：点击预取与详情页加载共用同一次网络请求，不会重复发
 *   （预取若在详情页发起加载前就已完成，详情页会重新拉一次做静默刷新，属预期行为）
 */
object VideoDetailCache {

    private const val MAX_INFO = 8
    private const val MAX_PREVIEW = 16

    private val infoCache = object : LinkedHashMap<String, VideoInfo>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoInfo>?): Boolean {
            return size > MAX_INFO
        }
    }

    private val previewCache = object : LinkedHashMap<String, VideoPreview>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoPreview>?): Boolean {
            return size > MAX_PREVIEW
        }
    }

    private val inFlight = HashMap<String, Deferred<VideoInfo?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun info(bvid: String): VideoInfo? = synchronized(infoCache) {
        if (bvid.isEmpty()) null else infoCache[bvid]
    }

    fun putInfo(bvid: String, info: VideoInfo) {
        if (bvid.isEmpty()) return
        synchronized(infoCache) { infoCache[bvid] = info }
    }

    fun preview(bvid: String): VideoPreview? = synchronized(previewCache) {
        if (bvid.isEmpty()) null else previewCache[bvid]
    }

    fun putPreview(bvid: String, preview: VideoPreview) {
        if (bvid.isEmpty()) return
        if (preview.title.isEmpty() && preview.cover.isEmpty()) return
        synchronized(previewCache) { previewCache[bvid] = preview }
    }

    /**
     * 点击卡片时调用：请求与导航动画并行跑，命中后详情页直接拿缓存。
     * 已有缓存或已有在途请求时不重复发起。
     */
    fun prefetch(bvid: String, aid: Long) {
        if (bvid.isEmpty() || info(bvid) != null) return
        loadAsync(bvid, aid)
    }

    /**
     * 详情页加载：复用预取在途请求，显式刷新时重新请求。
     * 失败返回 null，由调用方决定是否展示错误页。
     */
    suspend fun load(bvid: String, aid: Long, refresh: Boolean = false): VideoInfo? {
        if (bvid.isEmpty() && aid <= 0L) return null
        return try {
            loadAsync(bvid, aid, refresh).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun loadAsync(bvid: String, aid: Long, refresh: Boolean = false): Deferred<VideoInfo?> = synchronized(this) {
        val key = if (bvid.isNotEmpty()) "bvid:$bvid" else "aid:$aid"
        if (!refresh) inFlight[key]?.let { return@synchronized it }
        val request = scope.async(start = CoroutineStart.LAZY) {
            try {
                val info = if (bvid.isNotEmpty()) {
                    VideoInfoApi.getVideoInfo(bvid)
                } else {
                    VideoInfoApi.getVideoInfo(aid)
                }
                val job = currentCoroutineContext()[Job]
                synchronized(this@VideoDetailCache) {
                    if (info != null && inFlight[key] === job) {
                        putInfo(info.bvid.ifBlank { bvid }, info)
                    }
                }
                info
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        inFlight[key] = request
        request.invokeOnCompletion {
            synchronized(this@VideoDetailCache) {
                if (inFlight[key] === request) inFlight.remove(key)
            }
        }
        request.start()
        request
    }
}
