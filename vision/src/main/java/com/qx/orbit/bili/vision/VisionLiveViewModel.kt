package com.qx.orbit.bili.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.core.api.BilibiliLiveGateway
import com.qx.orbit.bili.core.api.CoreLiveRoom
import com.qx.orbit.bili.core.api.CoreLiveStream
import com.qx.orbit.bili.core.api.CorePlayback
import com.qx.orbit.bili.core.api.CoreVideo
import com.qx.orbit.bili.core.api.LiveGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VisionLiveStatus { LOADING, LIVE, ENDED, NOT_STARTED, ERROR }

data class VisionLiveState(
    val room: CoreLiveRoom? = null,
    val stream: CoreLiveStream? = null,
    val playback: CorePlayback? = null,
    val quality: Int = 10000,
    val status: VisionLiveStatus = VisionLiveStatus.LOADING,
    val error: String? = null,
)

class VisionLiveViewModel(
    private val gateway: LiveGateway = BilibiliLiveGateway(),
) : ViewModel() {
    private val _state = MutableStateFlow(VisionLiveState())
    val state: StateFlow<VisionLiveState> = _state.asStateFlow()

    private var roomId = 0L
    private var cookie = ""
    private var generation = 0
    private var job: Job? = null

    fun open(roomId: Long, cookie: String, preferredQuality: Int = DEFAULT_QUALITY) {
        if (roomId == this.roomId && cookie == this.cookie && _state.value.room != null) return
        this.roomId = roomId
        this.cookie = cookie
        load(preferredQuality)
    }

    fun selectQuality(qn: Int) {
        if (qn == _state.value.quality && _state.value.playback != null) return
        load(qn)
    }

    fun retry() = load(_state.value.quality)

    private fun load(qn: Int) {
        if (roomId <= 0) return
        val token = ++generation
        job?.cancel()
        _state.value = _state.value.copy(status = VisionLiveStatus.LOADING, error = null, quality = qn)
        job = viewModelScope.launch {
            try {
                val room = gateway.loadRoomInfo(roomId)
                if (token != generation) return@launch
                if (room.liveStatus != 1) {
                    _state.value = VisionLiveState(
                        room = room,
                        quality = qn,
                        status = if (room.liveStatus == 2) VisionLiveStatus.ENDED else VisionLiveStatus.NOT_STARTED,
                    )
                    return@launch
                }
                val stream = gateway.loadStreamInfo(roomId, qn, cookie)
                if (token != generation) return@launch
                val selected = stream.requestQuality(qn)
                val url = stream.urlsByQuality[selected]
                if (url.isNullOrBlank()) {
                    _state.value = VisionLiveState(
                        room = room, stream = stream, quality = selected,
                        status = VisionLiveStatus.ERROR,
                        error = if (stream.encrypted && !stream.pwdVerified) "该直播间需要密码，暂不支持" else "未获取到直播流，请稍后重试",
                    )
                    return@launch
                }
                val video = CoreVideo(
                    id = 0,
                    bvid = "live-$roomId",
                    cid = roomId,
                    title = room.title,
                    cover = room.cover,
                    author = room.uname,
                )
                _state.value = VisionLiveState(
                    room = room,
                    stream = stream,
                    playback = CorePlayback(video = video, urls = listOf(url), quality = selected, qualities = stream.qualities),
                    quality = selected,
                    status = VisionLiveStatus.LIVE,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (token == generation) {
                    _state.value = _state.value.copy(status = VisionLiveStatus.ERROR, error = e.message ?: "直播间加载失败")
                }
            }
        }
    }

    companion object {
        const val DEFAULT_QUALITY = 10000
    }
}
