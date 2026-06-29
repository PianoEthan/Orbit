package com.qx.orbit.bili.presentation.player

import android.util.Log
import org.brotli.dec.BrotliInputStream
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.util.SharedPreferencesUtil
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

import java.util.UUID

interface PlayerCallback {
    fun addDanmaku(
        text: String,
        color: Int,
        textSize: Int = 25,
        type: Int = 1,
        borderColor: Int = 0,
        senderName: String = "",
        emotes: Map<String, com.qx.orbit.bili.presentation.viewmodel.EmoteInline>? = null,
        singleEmote: com.qx.orbit.bili.presentation.viewmodel.EmoteInline? = null,
        id: String = ""
    )
    var onlineNumber: String
    fun updateTitle(title: String)
}

class PlayerDanmuClientListener(
    private val roomId: Long,
    private val uid: Long,
    private val buvid: String,
    private val key: String,
    private val callback: PlayerCallback
) : WebSocketListener() {

    companion object {
        private const val TAG = "BiliApi"
        private const val HEARTBEAT_INTERVAL = 32L
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var heartbeatFuture: ScheduledFuture<*>? = null
    private var webSocket: WebSocket? = null

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "Danmu WS onOpen: roomid=$roomId uid=$uid")
        this.webSocket = webSocket
        sendAuthPacket(webSocket)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        try {
            parsePackage(bytes.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Danmu WS parse error: ${e.message}")
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "Danmu WS onClosing: $code $reason")
        stopHeartbeat()
        webSocket.close(code, reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "Danmu WS onClosed: $code $reason")
        stopHeartbeat()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "Danmu WS onFailure: ${t.message}")
        stopHeartbeat()
    }

    private fun sendAuthPacket(ws: WebSocket) {
        val authJson = JSONObject().apply {
            put("uid", uid)
            put("roomid", roomId)
            put("protover", 3)
            put("platform", "web")
            put("buvid", buvid)
            put("type", 2)
            put("key", key)
        }
        val data = authJson.toString().toByteArray(StandardCharsets.UTF_8)
        ws.send(MessageData(data, actionCode = 7).toByteString())
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatFuture = scheduler.scheduleAtFixedRate({
            try {
                webSocket?.send(MessageData(ByteArray(0), actionCode = 2).toByteString())
            } catch (e: Exception) {
                Log.e(TAG, "Heartbeat failed", e)
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS)
    }

    private fun stopHeartbeat() {
        heartbeatFuture?.cancel(false)
        heartbeatFuture = null
    }

    private fun parsePackage(data: ByteArray) {
        if (data.size < 16) {
            Log.w(TAG, "Package too small: ${data.size} bytes")
            return
        }
        val buffer = ByteBuffer.wrap(data)
        val totalSize = buffer.int
        val headerSize = buffer.short.toInt() and 0xFFFF
        val protocolVersion = buffer.short.toInt() and 0xFFFF
        val actionCode = buffer.int
        val sequence = buffer.int

        //Log.d(TAG, "Package: action=$actionCode proto=$protocolVersion size=$totalSize headerSize=$headerSize")

        when (actionCode) {
            8 -> {
                Log.d(TAG, "Auth success")
                startHeartbeat()
            }
            5 -> {
                val payload = data.copyOfRange(headerSize, data.size)
                when (protocolVersion) {
                    0, 1 -> handlePlainPackage(payload)
                    3 -> {
                        val decompressed = decompressBrotli(payload)
                        if (decompressed != null) {
                            handlePlainPackage(decompressed)
                        }
                    }
                    2 -> {
                        val decompressed = inflate(payload)
                        if (decompressed != null) {
                            handlePlainPackage(decompressed)
                        }
                    }
                }
            }
            3 -> {
                val viewers = ByteBuffer.wrap(data, headerSize, 4).int
                callback.onlineNumber = viewers.toString()
            }
        }
    }

    private fun handlePlainPackage(data: ByteArray) {
        try {
            val text = String(data, StandardCharsets.UTF_8)
            val jsonStart = text.indexOf("{")
            if (jsonStart < 0) return
            val jsonStr = text.substring(jsonStart)
            val obj = JSONObject(jsonStr)
            val cmd = obj.optString("cmd", "")
            handleCmd(cmd, obj)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse plain package", e)
        }
    }

    private fun handleCmd(cmd: String, json: JSONObject) {
        when {
            cmd == "DANMU_MSG" -> {
                val info = json.optJSONArray("info") ?: return
                val message = info.optString(1, "")
                if (message.isEmpty()) return

                val first = info.optJSONArray(0)
                val extraJson = try {
                    first?.optJSONObject(15)?.optString("extra")
                } catch (_: Exception) { null }
                val extraObj = try {
                    extraJson?.let { JSONObject(it) }
                } catch (_: Exception) { null }

                val senderName = try {
                    first?.optJSONObject(15)
                        ?.optJSONObject("user")
                        ?.optJSONObject("base")
                        ?.optString("name", "") ?: ""
                } catch (_: Exception) { "" }

                val singleEmote = try {
                    first?.optJSONObject(13)?.let { emoteObj ->
                        val url = emoteObj.optString("url", "")
                        val width = emoteObj.optInt("width", 0)
                        val height = emoteObj.optInt("height", 0)
                        if (url.isNotBlank() && width > 0 && height > 0) {
                            com.qx.orbit.bili.presentation.viewmodel.EmoteInline(url, width, height)
                        } else null
                    }
                } catch (_: Exception) { null }

                val emotes = try {
                    extraObj?.optJSONObject("emots")?.let { emotsObj ->
                        val map = mutableMapOf<String, com.qx.orbit.bili.presentation.viewmodel.EmoteInline>()
                        emotsObj.keys().forEach { key ->
                            val emote = emotsObj.optJSONObject(key) ?: return@forEach
                            val url = emote.optString("url", "")
                            val width = emote.optInt("width", 0)
                            val height = emote.optInt("height", 0)
                            if (url.isNotBlank() && width > 0 && height > 0) {
                                map[key] = com.qx.orbit.bili.presentation.viewmodel.EmoteInline(url, width, height)
                            }
                        }
                        map.takeIf { it.isNotEmpty() }
                    }
                } catch (_: Exception) { null }

                val id = try { extraObj?.optString("id_str", "") ?: "" } catch (_: Exception) { "" }
                val displayText = if (senderName.isNotEmpty()) "$senderName：$message" else message
                callback.addDanmaku(displayText, 0xFFFFFF, senderName = senderName, emotes = emotes, singleEmote = singleEmote, id = id)
            }
            cmd == "WATCHED_CHANGE" -> {
                val data = json.optJSONObject("data")
                val num = data?.optString("num_small", "") ?: ""
                if (num.isNotEmpty()) {
                    callback.onlineNumber = num
                }
            }
            cmd == "INTERACT_WORD" -> {
                val data = json.optJSONObject("data")
                val uname = data?.optString("uname", "") ?: ""
                if (uname.isNotEmpty()) {
                    callback.addDanmaku("$uname 进入直播间", 0xAAAAAA, textSize = 18, type = 1, id = UUID.randomUUID().toString())
                }
            }
            cmd == "SEND_GIFT" -> {
                val data = json.optJSONObject("data")
                val uname = data?.optString("uname", "") ?: ""
                val action = data?.optString("action", "") ?: ""
                val giftName = data?.optString("giftName", "") ?: ""
                if (uname.isNotEmpty()) {
                    callback.addDanmaku("$uname $action $giftName", 0xFFAA00, textSize = 20, type = 1, id = UUID.randomUUID().toString())
                }
            }
            cmd == "ENTRY_EFFECT" -> {
                val data = json.optJSONObject("data")
                val copyWriting = data?.optString("copy_writing", "")?.replace(Regex("<[^>]+>"), "") ?: ""
                if (copyWriting.isNotEmpty()) {
                    callback.addDanmaku(copyWriting, 0xFF6699, textSize = 20, type = 1, id = UUID.randomUUID().toString())
                }
            }
            cmd.startsWith("NOTICE_MSG") -> {
                val data = json.optJSONObject("data")
                val msg = data?.optString("msg_common", "") ?: ""
                if (msg.isNotEmpty()) {
                    callback.addDanmaku(msg, 0xFF6699, textSize = 18, type = 1, id = UUID.randomUUID().toString())
                }
            }
            cmd == "ROOM_CHANGE" -> {
                val data = json.optJSONObject("data")
                val title = data?.optString("title", "") ?: ""
                if (title.isNotEmpty()) {
                    callback.updateTitle(title)
                }
            }
        }
    }

    private fun inflate(data: ByteArray): ByteArray? {
        return try {
            val inflater = Inflater()
            inflater.setInput(data)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) break
                output.write(buffer, 0, count)
            }
            inflater.end()
            output.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Inflate failed", e)
            null
        }
    }

    private fun decompressBrotli(data: ByteArray): ByteArray? {
        return try {
            val bais = ByteArrayInputStream(data)
            val brotliStream = BrotliInputStream(bais)
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var len: Int
            while (brotliStream.read(buffer).also { len = it } != -1) {
                baos.write(buffer, 0, len)
            }
            brotliStream.close()
            baos.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Brotli decompress failed", e)
            null
        }
    }

    fun close() {
        stopHeartbeat()
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        scheduler.shutdownNow()
    }

    private class MessageData(
        private val data: ByteArray,
        private val headerSize: Short = 16,
        private val protocolVersion: Short = 1,
        private val actionCode: Int,
        private val sequence: Int = 1
    ) {
        fun toByteString(): ByteString {
            val totalSize = headerSize + data.size
            val buffer = ByteBuffer.allocate(totalSize)
            buffer.putInt(totalSize)
            buffer.putShort(headerSize)
            buffer.putShort(protocolVersion)
            buffer.putInt(actionCode)
            buffer.putInt(sequence)
            buffer.put(data)
            return ByteString.of(*buffer.array())
        }
    }
}
