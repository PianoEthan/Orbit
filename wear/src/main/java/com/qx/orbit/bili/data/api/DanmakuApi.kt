package com.qx.orbit.bili.data.api

import com.qx.orbit.bili.data.model.*
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.data.remote.GsonConfig
import com.qx.orbit.bili.data.remote.HttpClient
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.google.protobuf.CodedInputStream
import com.google.protobuf.WireFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

object DanmakuApi {

    internal data class SendDanmakuData(
        @SerializedName("dmid") val dmid: Long = 0
    )

    suspend fun sendDanmaku(
        cid: Long,
        msg: String,
        bvid: String,
        progress: Int,
        color: Int,
        mode: Int
    ): Int = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("oid", cid.toString())
            .add("type", "1")
            .add("msg", msg)
            .add("bvid", bvid)
            .add("progress", progress.toString())
            .add("color", color.toString())
            .add("mode", mode.toString())
            .add("csrf", CookieManager.getCsrf())
            .build()
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/v2/dm/post")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        val type = object : TypeToken<ApiResponse<SendDanmakuData>>() {}.type
        val resp: ApiResponse<SendDanmakuData>? = GsonConfig.gson.fromJson(json, type)
        resp?.code ?: -1
    }

    suspend fun likeDanmaku(dmid: Long, cid: Long, op: Int): Int = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("dmid", dmid.toString())
            .add("oid", cid.toString())
            .add("op", op.toString())
            .add("csrf", CookieManager.getCsrf())
            .build()
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/v2/dm/thumbup/add")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        val type = object : TypeToken<ApiResponse<*>>() {}.type
        val resp: ApiResponse<*>? = GsonConfig.gson.fromJson(json, type)
        resp?.code ?: -1
    }

    suspend fun recallDanmaku(dmid: Long, cid: Long): Int = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("dmid", dmid.toString())
            .add("oid", cid.toString())
            .add("csrf", CookieManager.getCsrf())
            .build()
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/dm/recall")
            .post(body)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val json = HttpClient.client.newCall(request).execute().body?.string() ?: ""
        val type = object : TypeToken<ApiResponse<*>>() {}.type
        val resp: ApiResponse<*>? = GsonConfig.gson.fromJson(json, type)
        resp?.code ?: -1
    }

    suspend fun getVideoDanmakuSegments(
        aid: Long,
        cid: Long,
        requireComplete: Boolean = false
    ): List<DmSegMobileReply> = withContext(Dispatchers.IO) {
        val segmentCount = runCatching {
            getVideoDanmakuSegmentCount(aid, cid)
        }.getOrNull() ?: if (requireComplete) {
            return@withContext emptyList()
        } else {
            1
        }
        val segments = (1..segmentCount)
            .chunked(MAX_CONCURRENT_SEGMENT_REQUESTS)
            .flatMap { segmentIndexes ->
                coroutineScope {
                    segmentIndexes.map { segmentIndex ->
                        async {
                            runCatching {
                                getVideoDanmakuSegment(aid, cid, segmentIndex)
                            }.getOrNull()
                        }
                    }.awaitAll()
                }
            }
        if (requireComplete && segments.any { it == null }) {
            emptyList()
        } else {
            segments.filterNotNull()
        }
    }

    suspend fun getVideoDanmakuSegment(aid: Long, cid: Long, segmentIndex: Int): DmSegMobileReply? = withContext(Dispatchers.IO) {
        val baseUrl = "https://api.bilibili.com/x/v2/dm/wbi/web/seg.so?type=1&oid=$cid&pid=$aid&segment_index=$segmentIndex"
        val url = ConfInfoApi.signWBI(baseUrl)
        val request = Request.Builder().url(url)
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val response = HttpClient.client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return@withContext null
        }
        val bytes = response.body.bytes()
        if (bytes.isEmpty()) return@withContext DmSegMobileReply()
        parseDmSegMobileReply(bytes)
    }

    private suspend fun getVideoDanmakuSegmentCount(aid: Long, cid: Long): Int? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/v2/dm/web/view?type=1&oid=$cid&pid=$aid")
            .addHeader("Cookie", CookieManager.getCookie())
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", "https://www.bilibili.com/")
            .build()
        val response = HttpClient.client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return@withContext null
        }
        val bytes = response.body.bytes()
        parseDanmakuSegmentCount(bytes).takeIf { it > 0 }
    }

    internal fun parseDanmakuSegmentCount(bytes: ByteArray): Int = runCatching {
        val input = CodedInputStream.newInstance(bytes)
        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (tag == 0) break
            if (WireFormat.getTagFieldNumber(tag) == DM_SEGMENT_CONFIG_FIELD_NUMBER) {
                val length = input.readRawVarint32()
                val oldLimit = input.pushLimit(length)
                while (!input.isAtEnd) {
                    val configTag = input.readTag()
                    if (configTag == 0) break
                    if (WireFormat.getTagFieldNumber(configTag) == DM_SEGMENT_TOTAL_FIELD_NUMBER) {
                        val total = input.readInt64().toInt()
                        input.popLimit(oldLimit)
                        return@runCatching total
                    }
                    input.skipField(configTag)
                }
                input.popLimit(oldLimit)
            } else {
                input.skipField(tag)
            }
        }
        0
    }.getOrDefault(0)

    private fun parseDmSegMobileReply(bytes: ByteArray): DmSegMobileReply {
        val elems = mutableListOf<DanmakuElem>()
        var pos = 0
        while (pos < bytes.size) {
            val tag = readVarint(bytes, pos)
            pos = tag.second
            val fieldNumber = (tag.first shr 3).toInt()
            if (fieldNumber != 1) {
                val wt = (tag.first and 0x7).toInt()
                pos = skipField(bytes, pos, wt)
                continue
            }
            val len = readVarint(bytes, pos)
            pos = len.second
            val end = pos + len.first.toInt()
            elems.add(parseDanmakuElem(bytes, pos, end))
            pos = end
        }
        return DmSegMobileReply(elems = elems)
    }

    private fun parseDanmakuElem(bytes: ByteArray, start: Int, end: Int): DanmakuElem {
        var id = 0L
        var progress = 0
        var mode = 1
        var fontsize = 25
        var color = 0
        var midHash = ""
        var content = ""
        var ctime = 0L
        var weight = 0
        var action = ""
        var pool = 0
        var idStr = ""
        var attr = 0
        var animation = ""
        var pos = start
        while (pos < end) {
            val tag = readVarint(bytes, pos)
            pos = tag.second
            val fieldNumber = (tag.first shr 3).toInt()
            val wireType = (tag.first and 0x7).toInt()
            when (fieldNumber) {
                1 -> { id = readVarint(bytes, pos).also { pos = it.second }.first }
                2 -> { progress = readVarint(bytes, pos).also { pos = it.second }.first.toInt() }
                3 -> { mode = readVarint(bytes, pos).also { pos = it.second }.first.toInt() }
                4 -> { fontsize = readVarint(bytes, pos).also { pos = it.second }.first.toInt() }
                5 -> { color = readVarint(bytes, pos).also { pos = it.second }.first.toInt() }
                6 -> {
                    val len = readVarint(bytes, pos).also { pos = it.second }.first.toInt()
                    midHash = String(bytes, pos, len, Charsets.UTF_8)
                    pos += len
                }
                7 -> {
                    val len = readVarint(bytes, pos).also { pos = it.second }.first.toInt()
                    content = String(bytes, pos, len, Charsets.UTF_8)
                    pos += len
                }
                8 -> { ctime = readVarint(bytes, pos).also { pos = it.second }.first }
                9 -> { weight = readVarint(bytes, pos).also { pos = it.second }.first.toInt() }
                10 -> {
                    val len = readVarint(bytes, pos).also { pos = it.second }.first.toInt()
                    action = String(bytes, pos, len, Charsets.UTF_8)
                    pos += len
                }
                11 -> { pool = readVarint(bytes, pos).also { pos = it.second }.first.toInt() }
                12 -> {
                    val len = readVarint(bytes, pos).also { pos = it.second }.first.toInt()
                    idStr = String(bytes, pos, len, Charsets.UTF_8)
                    pos += len
                }
                13 -> { attr = readVarint(bytes, pos).also { pos = it.second }.first.toInt() }
                22 -> {
                    val len = readVarint(bytes, pos).also { pos = it.second }.first.toInt()
                    animation = String(bytes, pos, len, Charsets.UTF_8)
                    pos += len
                }
                else -> { pos = skipField(bytes, pos, wireType) }
            }
        }
        return DanmakuElem(
            id = id, progress = progress, mode = mode, fontsize = fontsize,
            color = color, midHash = midHash, content = content, ctime = ctime,
            weight = weight, action = action, pool = pool, idStr = idStr,
            attr = attr, animation = animation
        )
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = start
        while (pos < bytes.size) {
            val b = bytes[pos].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            pos++
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Pair(result, pos)
    }

    private fun skipField(bytes: ByteArray, pos: Int, wireType: Int): Int {
        return when (wireType) {
            0 -> readVarint(bytes, pos).second
            1 -> pos + 8
            2 -> {
                val len = readVarint(bytes, pos)
                len.second + len.first.toInt()
            }
            5 -> pos + 4
            else -> pos
        }
    }

    private const val MAX_CONCURRENT_SEGMENT_REQUESTS = 4
    private const val DM_SEGMENT_CONFIG_FIELD_NUMBER = 4
    private const val DM_SEGMENT_TOTAL_FIELD_NUMBER = 2
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36"
}
