package com.qx.orbit.bili.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.data.api.EmoteApi
import com.qx.orbit.bili.data.api.LiveApi
import com.qx.orbit.bili.data.api.WbiSigner
import com.qx.orbit.bili.data.model.LivePlayInfo
import com.qx.orbit.bili.data.model.LiveRoom
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.presentation.player.PlayerDanmuClientListener
import com.qx.orbit.bili.presentation.player.PlayerCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

import java.util.UUID

data class EmoteInline(
    val url: String,
    val width: Int,
    val height: Int
)

data class DanmakuMessage(
    val id: String,
    val text: String,
    val color: Int = 0xFFFFFF,
    val isSystem: Boolean = false,
    val emotes: Map<String, EmoteInline>? = null,
    val singleEmote: EmoteInline? = null
)

class LiveDetailViewModel : ViewModel() {
    private val _room = MutableStateFlow<LiveRoom?>(null)
    val room: StateFlow<LiveRoom?> = _room.asStateFlow()

    private val _playInfo = MutableStateFlow<LivePlayInfo?>(null)
    val playInfo: StateFlow<LivePlayInfo?> = _playInfo.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _recommended = MutableStateFlow<List<LiveRoom>>(emptyList())
    val recommended: StateFlow<List<LiveRoom>> = _recommended.asStateFlow()

    private val _isRecommendedLoading = MutableStateFlow(false)
    private var recommendPage = 1

    private val _danmakuList = MutableStateFlow<List<DanmakuMessage>>(emptyList())
    val danmakuList: StateFlow<List<DanmakuMessage>> = _danmakuList.asStateFlow()

    private val _danmakuCount = MutableStateFlow(0)
    val danmakuCount: StateFlow<Int> = _danmakuCount.asStateFlow()

    private val _emotes = MutableStateFlow<List<EmoteApi.EmotePackage>?>(null)
    val emotes: StateFlow<List<EmoteApi.EmotePackage>?> = _emotes.asStateFlow()

    private var webSocket: okhttp3.WebSocket? = null
    private var danmuListener: PlayerDanmuClientListener? = null
    private var connectJob: kotlinx.coroutines.Job? = null

    fun loadRoom(roomId: Long) {
        viewModelScope.launch {
            _error.value = null
            try {
                // 避免重复连接导致弹幕重复
                connectJob?.cancel()
                try {
                    danmuListener?.close()
                    webSocket?.close(1000, "reload")
                } catch (_: Exception) {}
                danmuListener = null
                webSocket = null

                val roomInfo = LiveApi.getRoomInfo(roomId)
                _room.value = roomInfo
                if (roomInfo != null) {
                    val play = LiveApi.getRoomPlayInfo(roomId, 250)
                    _playInfo.value = play
                    loadRecommended()
                    connectDanmaku(roomId)
                    // Fetch host user info for avatar
                    launch {
                        try {
                            val biliApi = com.qx.orbit.bili.data.api.BiliApiService.create()
                            when (val cardResult = biliApi.getUserCard(roomInfo.uid)) {
                                is com.qx.orbit.bili.data.remote.Result.Success -> {
                                    val card = cardResult.data.asJsonObject
                                        .getAsJsonObject("data")
                                        ?.getAsJsonObject("card")
                                    if (card != null) {
                                        val name = card.get("name")?.asString
                                        val face = card.get("face")?.asString
                                        if (!name.isNullOrEmpty() || !face.isNullOrEmpty()) {
                                            _room.value = _room.value?.copy(
                                                uname = name?.takeIf { it.isNotEmpty() } ?: _room.value?.uname ?: "",
                                                face = face?.takeIf { it.isNotEmpty() } ?: _room.value?.face ?: ""
                                            )
                                        }
                                    }
                                }
                                else -> {}
                            }
                        } catch (_: Exception) {}
                    }
                } else {
                    _error.value = "直播间不存在"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "加载失败"
            }
        }
    }

    private fun connectDanmaku(roomId: Long) {
        if (webSocket != null || connectJob?.isActive == true) return
        connectJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val danmuInfo = LiveApi.getDanmuInfo(roomId) ?: return@launch
                val host = danmuInfo.host_list?.firstOrNull() ?: return@launch
                val token = danmuInfo.token ?: return@launch
                val wssPort = host.wss_port
                val hostName = host.host ?: return@launch

                val url = "wss://$hostName:$wssPort/sub"
                val buvid = CookieManager.getCookie().split("; ")
                    .find { it.startsWith("buvid3=") }?.substringAfter("=") ?: ""
                val mid = CookieManager.getMid()

                val callback = object : PlayerCallback {
                    override fun addDanmaku(text: String, color: Int, textSize: Int, type: Int, borderColor: Int, senderName: String, emotes: Map<String, EmoteInline>?, singleEmote: EmoteInline?, id: String) {
                        val msg = DanmakuMessage(
                            id = id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                            text = text,
                            color = color,
                            emotes = emotes,
                            singleEmote = singleEmote
                        )
                        _danmakuList.value = (_danmakuList.value + msg).distinctBy { it.id }.takeLast(200)
                        _danmakuCount.value++
                    }

                    override var onlineNumber: String = ""

                    override fun updateTitle(title: String) {
                        _room.value = _room.value?.copy(title = title)
                    }
                }

                val listener = PlayerDanmuClientListener(
                    roomId = roomId,
                    uid = mid,
                    buvid = buvid,
                    key = token,
                    callback = callback
                )
                danmuListener = listener

                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(url)
                    .header("Cookie", CookieManager.getCookie())
                    .header("Origin", "https://live.bilibili.com")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")
                    .build()
                webSocket = client.newWebSocket(request, listener)
                Log.d("BiliApi", "LiveDetail danmaku WS connecting to $url, uid=$mid, roomid=$roomId")
            } catch (e: Exception) {
                Log.e("LiveDetail", "Danmaku connect error: ${e.message}")
            }
        }
    }

    fun sendDanmaku(text: String, roomId: Long, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val csrf = CookieManager.getCsrf()
                val rnd = (System.currentTimeMillis() / 1000).toString()
                val body = okhttp3.FormBody.Builder()
                    .add("bubble", "0")
                    .add("msg", text)
                    .add("color", "16777215")
                    .add("mode", "1")
                    .add("room_type", "0")
                    .add("jumpfrom", "0")
                    .add("reply_mid", "0")
                    .add("reply_attr", "0")
                    .add("replay_dmid", "")
                    .add("statistics", "{\"appId\":100,\"platform\":5}")
                    .add("reply_type", "0")
                    .add("reply_uname", "")
                    .add("fontsize", "25")
                    .add("rnd", rnd)
                    .add("roomid", roomId.toString())
                    .add("csrf", csrf)
                    .add("csrf_token", csrf)
                    .build()
                val signedUrl = WbiSigner.signUrl("https://api.live.bilibili.com/msg/send?web_location=444.8")
                val request = okhttp3.Request.Builder()
                    .url(signedUrl)
                    .post(body)
                    .addHeader("Cookie", CookieManager.getCookie())
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")
                    .addHeader("Referer", "https://live.bilibili.com/$roomId")
                    .build()
                val response = com.qx.orbit.bili.data.remote.HttpClient.client.newCall(request).execute()
                val respBody = response.body?.string().orEmpty()
                response.close()
                val code = Regex("\"code\"\\s*:\\s*(\\d+)").find(respBody)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                val msg = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(respBody)?.groupValues?.get(1).orEmpty()
                val ok = code == 0
                withContext(Dispatchers.Main) { onResult(ok, msg) }
            } catch (e: Exception) {
                Log.e("LiveDetail", "Send danmaku error: ${e.message}")
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "unknown") }
            }
        }
    }

    fun loadEmotes(roomId: Long) {
        if (_emotes.value != null) return
        viewModelScope.launch {
            try {
                val pkgs = LiveApi.getLiveEmoticons(roomId = roomId)
                _emotes.value = pkgs.map { pkg ->
                    EmoteApi.EmotePackage(
                        id = pkg.pkgId,
                        text = pkg.pkgName,
                        url = pkg.currentCover,
                        type = pkg.pkgType,
                        attr = pkg.pkgPerm,
                        emotes = pkg.emoticons.map { e ->
                            EmoteApi.Emote(
                                id = e.emoticonId.toInt(),
                                packageId = pkg.pkgId,
                                name = "[${e.emoji}]",
                                url = e.url,
                                meta = EmoteApi.Emote.EmoteMeta(
                                    size = if (e.width > 0) e.height / e.width else 1
                                )
                            )
                        }
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun loadRecommended() {
        if (_isRecommendedLoading.value) return
        _isRecommendedLoading.value = true
        viewModelScope.launch {
            try {
                val list = LiveApi.getRecommend(recommendPage)
                if (!list.isNullOrEmpty()) {
                    _recommended.value = _recommended.value + list
                    recommendPage++
                }
            } catch (_: Exception) {} finally {
                _isRecommendedLoading.value = false
            }
        }
    }

    fun getStreamUrl(): String? {
        val play = _playInfo.value ?: return null
        val codec = play.playurl_info?.playurl?.stream
            ?.firstOrNull()?.format?.firstOrNull()?.codec?.firstOrNull() ?: return null
        val urlInfo = codec.urlInfo.firstOrNull() ?: return null
        return urlInfo.host + codec.base_url + urlInfo.extra
    }

    fun getLiveStartTime(): Long {
        return _playInfo.value?.live_time ?: 0L
    }

    override fun onCleared() {
        super.onCleared()
        try {
            danmuListener?.close()
            webSocket?.close(1000, "bye")
        } catch (_: Exception) {}
    }
}
