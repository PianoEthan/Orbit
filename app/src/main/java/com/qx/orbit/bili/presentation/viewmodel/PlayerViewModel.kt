package com.qx.orbit.bili.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.BuildConfig
import com.qx.orbit.bili.data.api.CookiesApi
import com.qx.orbit.bili.data.api.HeartbeatApi
import com.qx.orbit.bili.data.api.HistoryApi
import com.qx.orbit.bili.data.api.LiveApi
import com.qx.orbit.bili.data.api.PlayerApi
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.model.Subtitle
import com.qx.orbit.bili.data.model.SubtitleLink
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.util.player.OrbitPlayer
import com.qx.orbit.bili.util.player.createOrbitPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

class PlayerViewModel : ViewModel() {

    private lateinit var _player: OrbitPlayer
    val player: OrbitPlayer get() = _player

    fun initPlayer(context: Context) {
        if (::_player.isInitialized) return
        _player = createOrbitPlayer(context.applicationContext)
    }

    // --- State ---
    private val _playerData = MutableStateFlow(PlayerData())
    val playerData: StateFlow<PlayerData> = _playerData.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isPrepared = MutableStateFlow(false)
    val isPrepared: StateFlow<Boolean> = _isPrepared.asStateFlow()

    private val _currentProgress = MutableStateFlow(0L)
    val currentProgress: StateFlow<Long> = _currentProgress.asStateFlow()

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration: StateFlow<Long> = _totalDuration.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _videoWidth = MutableStateFlow(16f)
    val videoWidth: StateFlow<Float> = _videoWidth.asStateFlow()

    private val _videoHeight = MutableStateFlow(9f)
    val videoHeight: StateFlow<Float> = _videoHeight.asStateFlow()

    private val _bufferSpeed = MutableStateFlow("")
    val bufferSpeed: StateFlow<String> = _bufferSpeed.asStateFlow()

    private val _subtitles = MutableStateFlow(emptyArray<Subtitle>())
    val subtitles: StateFlow<Array<Subtitle>> = _subtitles.asStateFlow()

    private val _subtitleLinks = MutableStateFlow(emptyArray<SubtitleLink>())
    val subtitleLinks: StateFlow<Array<SubtitleLink>> = _subtitleLinks.asStateFlow()

    private val _currentSubtitleId = MutableStateFlow(0L)
    val currentSubtitleId: StateFlow<Long> = _currentSubtitleId.asStateFlow()

    private val _currentSubtitle = MutableStateFlow<String?>(null)
    val currentSubtitle: StateFlow<String?> = _currentSubtitle.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isLongPressSpeedUp = MutableStateFlow(false)
    val isLongPressSpeedUp: StateFlow<Boolean> = _isLongPressSpeedUp.asStateFlow()

    private val _liveElapsedSeconds = MutableStateFlow(0L)
    val liveElapsedSeconds: StateFlow<Long> = _liveElapsedSeconds.asStateFlow()

    // --- Internal ---
    private var switchPendingSeekMs = -1L
    private var startTs = 0L
    private var heartbeatStarted = false
    private var heartbeatJob: Job? = null
    private var bufferSpeedJob: Job? = null
    private var liveElapsedJob: Job? = null
    private var subtitleCursor = 0
    private var released = false

    /**
     * Read position directly from player, bypassing the currentProgress flow
     * which may be zeroed by heartbeat loop during player reset.
     */
    fun savePosition(): Long {
        return if (::_player.isInitialized && _isPrepared.value) {
            try { _player.currentPosition } catch (_: Exception) { 0L }
        } else _currentProgress.value
    }

    /**
     * Queue a seek to the given position on next onPrepared.
     */
    fun pendingSeekTo(ms: Long) {
        switchPendingSeekMs = ms
    }

    val isLive: Boolean get() = _playerData.value.type == PlayerData.TYPE_LIVE
    val isLocal: Boolean get() = _playerData.value.type == PlayerData.TYPE_LOCAL

    // --- Actions ---

    fun setData(data: PlayerData) {
        _playerData.value = data
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun beginPlaybackPreparation() {
        _isPrepared.value = false
        _isLoading.value = true
        heartbeatStarted = false
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun setError(msg: String?) {
        _errorMessage.value = msg
    }

    fun setSubtitles(subs: Array<Subtitle>) {
        _subtitles.value = subs
        subtitleCursor = 0
    }

    fun setSubtitleLinks(links: Array<SubtitleLink>) {
        _subtitleLinks.value = links
    }

    suspend fun loadSubtitleFromPath(pathOrUrl: String): Array<Subtitle> {
        return try {
            val text = if (pathOrUrl.startsWith("/") || pathOrUrl.startsWith("file://")) {
                java.io.File(pathOrUrl.removePrefix("file://")).readText()
            } else {
                PlayerApi.getSubtitleRaw(pathOrUrl)
            }
            parseSubtitleJson(text)
        } catch (_: Exception) { emptyArray() }
    }

    fun discoverLocalSubtitles(videoFilePath: String): Array<SubtitleLink> {
        val videoFile = java.io.File(videoFilePath)
        val dir = videoFile.parentFile ?: return emptyArray()
        val baseName = videoFile.nameWithoutExtension
        val srtFiles = dir.listFiles { f ->
            f.isFile && (f.extension == "srt" || f.extension == "json") && f.nameWithoutExtension.startsWith(baseName)
        }?.sortedBy { it.extension } ?: return emptyArray()
        
        return srtFiles.mapIndexedNotNull { idx, f ->
            val name = f.nameWithoutExtension
            val isAi = name.contains(".ai") || name.contains("ai.")
            var lang = name.removePrefix(baseName)
            if (lang.startsWith(".mp4")) lang = lang.removePrefix(".mp4")
            if (lang.startsWith(".m4s")) lang = lang.removePrefix(".m4s")
            lang = lang.removePrefix(".").removePrefix("ai.").removePrefix(".ai").removePrefix(".")
                .ifEmpty { if (isAi) "AI" else "默认" }
            SubtitleLink(id = -(idx + 1L), isAI = isAi, lang = lang, url = f.absolutePath)
        }.distinctBy { it.lang + it.isAI }.sortedByDescending { !it.isAI }.toTypedArray()
    }

    companion object {
        fun parseSubtitleJson(json: String): Array<Subtitle> {
            val jsonObj = com.google.gson.Gson().fromJson(json, Map::class.java)
            return (jsonObj?.get("body") as? List<*>)?.mapNotNull { entry ->
                val m = entry as? Map<*, *> ?: return@mapNotNull null
                Subtitle(
                    content = m["content"]?.toString() ?: "",
                    from = (m["from"] as? Number)?.toDouble() ?: 0.0,
                    to = (m["to"] as? Number)?.toDouble() ?: 0.0
                )
            }?.toTypedArray() ?: emptyArray()
        }
    }

    fun setCurrentSubtitleId(id: Long) {
        _currentSubtitleId.value = id
    }

    fun preparePlayback(initialData: PlayerData, isAudioOnlyMode: Boolean) {
        // Stop heartbeat loop before player reset (prevents currentProgress from being zeroed)
        beginPlaybackPreparation()
        val data = _playerData.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!isLocal && !CookieManager.getCookie().contains("buvid3")) {
                    CookiesApi.checkCookies()
                }

                val result = if (isLive || isLocal) data
                else {
                    val isExoPlayer = com.qx.orbit.bili.util.SharedPreferencesUtil.getString("player_engine", "ijk") == "media3"
                    val requestDash = com.qx.orbit.bili.util.SharedPreferencesUtil.getBoolean("player_request_dash", false)
                    val shouldUseDash = isExoPlayer && requestDash
                    
                    if (data.type == PlayerData.TYPE_BANGUMI) {
                        PlayerApi.getBangumi(data, forceMp4 = !shouldUseDash)
                    } else {
                        if (shouldUseDash || isAudioOnlyMode) PlayerApi.getVideoDash(data) else PlayerApi.getVideo(data)
                    }
                }

                if (!isLive && !isLocal) _playerData.value = result

                if (result.videoUrl.isNotEmpty() || result.audioUrl.isNotEmpty()) {
                    configureAndStartPlayer(result, isAudioOnlyMode)
                } else {
                    _errorMessage.value = "无法获取视频地址"
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络请求失败"
                _isLoading.value = false
            }
        }
    }

    private fun configureAndStartPlayer(data: PlayerData, isAudioOnlyMode: Boolean) {
        _player.reset()
        val useAudioOnlySource = !isLocal && isAudioOnlyMode && data.audioUrl.isNotEmpty()
        if (useAudioOnlySource) {
            _player.setDashData(null, data.audioUrl, "")
        } else {
            _player.setDashData(data.dashData, data.videoUrl, data.audioUrl)
        }
        _player.setOption(OrbitPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1)
        _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "allowed_extensions", "ALL")
        _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "file,http,https,tcp,tls,crypto")
        if (!isLocal) {
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36")
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "headers", "Referer: https://www.bilibili.com")
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "reconnect_at_eof", 1)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "reconnect_streamed", 1)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "reconnect_delay_max", 2)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
        }
        if (isLive) {
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "live直播延时", 1)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1)
        }
        if (BuildConfig.HAS_IJK) {
            _player.setOption(OrbitPlayer.OPT_CATEGORY_PLAYER, "vn", if (useAudioOnlySource) 1 else 0)
        }
        if (isAudioOnlyMode && !isLocal) {
            // Power-saving: disable video decode (IJK only), minimize buffer
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "reconnect_delay_max", 2)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 1024)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "timeout", 5000000)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 0)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 0)
        }
        // Local cached videos: keep video URL (surface already detached → video decode stops, saves battery)
        // Network videos: use audio-only stream to save bandwidth
        val playUrl = if (isLocal) {
            data.videoUrl
        } else if (isAudioOnlyMode && data.audioUrl.isNotEmpty()) {
            data.audioUrl
        } else {
            data.videoUrl
        }
        _player.dataSource = playUrl
        _player.prepareAsync()
    }

    fun onPrepared() {
        _isPrepared.value = true
        _isLoading.value = false
        _totalDuration.value = _player.duration

        if (switchPendingSeekMs >= 0) {
            _player.seekTo(switchPendingSeekMs)
            _currentProgress.value = switchPendingSeekMs
            switchPendingSeekMs = -1L
        } else if (_playerData.value.progress > 0) {
            val targetMs = (_playerData.value.progress * 1000L).coerceAtMost(_player.duration)
            _player.seekTo(targetMs)
            _currentProgress.value = targetMs
            _playerData.value = _playerData.value.copy(progress = 0)
        }

        _player.start()
        _isPlaying.value = true

        if (!heartbeatStarted && !isLive) {
            heartbeatStarted = true
            startHeartbeatLoop()
        }
    }

    fun onCompletion() {
        if (!isLive && !isLocal) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val data = _playerData.value
                    val isBangumi = data.type == PlayerData.TYPE_BANGUMI && data.epid > 0
                    if (isBangumi) {
                        if (data.currentPageIndex + 1 < data.cids.size) {
                            switchToNextEpisode()
                            return@launch
                        }
                    }
                    HeartbeatApi.reportHeartbeat(
                        aid = data.aid, bvid = data.bvid, cid = data.cid,
                        playedTime = _totalDuration.value / 1000,
                        startTs = startTs,
                        type = if (isBangumi) "4" else "3",
                        subType = if (isBangumi) "1" else null,
                        epid = if (isBangumi) data.epid else null,
                        sid = if (isBangumi) data.sid else null,
                        videoDuration = (_totalDuration.value / 1000).coerceAtLeast(0)
                    )
                } catch (_: Exception) {}
            }
            _isPlaying.value = false
        }
    }

    fun onError(what: Int, extra: Int) {
        _errorMessage.value = "播放器错误: $what"
    }

    fun onInfo(what: Int, extra: Int) {
        when (what) {
            OrbitPlayer.MEDIA_INFO_BUFFERING_START -> {
                _isLoading.value = true
            }
            OrbitPlayer.MEDIA_INFO_BUFFERING_END -> {
                _isLoading.value = false
            }
        }
    }

    fun onVideoSizeChanged(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            _videoWidth.value = width.toFloat()
            _videoHeight.value = height.toFloat()
        }
    }

    fun play() {
        _player.start()
        _isPlaying.value = true
    }

    fun pause() {
        _player.pause()
        _isPlaying.value = false
        reportProgress()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekTo(ms: Long) {
        _player.seekTo(ms)
        _currentProgress.value = ms
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        try { _player.setSpeed(speed) } catch (_: Exception) {}
    }

    fun setLongPressSpeedUp(enabled: Boolean) {
        _isLongPressSpeedUp.value = enabled
        try {
            _player.setSpeed(if (enabled) 2.0f else _playbackSpeed.value)
        } catch (_: Exception) {}
    }

    fun switchToNextEpisode() {
        val data = _playerData.value
        if (data.currentPageIndex + 1 >= data.cids.size) return
        val nextIndex = data.currentPageIndex + 1
        val newData = data.copy(
            currentPageIndex = nextIndex,
            aid = if (data.aids.size > nextIndex) data.aids[nextIndex] else data.aid,
            cid = data.cids[nextIndex],
            epid = if (data.epids.size > nextIndex) data.epids[nextIndex] else data.epid,
            title = if (data.pagenames.size > nextIndex) data.pagenames[nextIndex] else data.title,
            progress = 0
        )
        _playerData.value = newData
        switchPendingSeekMs = 0L
        _isPrepared.value = false
        _isLoading.value = true
    }

    fun switchToPreviousEpisode() {
        val data = _playerData.value
        if (data.currentPageIndex - 1 < 0) return
        val prevIndex = data.currentPageIndex - 1
        val newData = data.copy(
            currentPageIndex = prevIndex,
            aid = if (data.aids.size > prevIndex) data.aids[prevIndex] else data.aid,
            cid = data.cids[prevIndex],
            epid = if (data.epids.size > prevIndex) data.epids[prevIndex] else data.epid,
            title = if (data.pagenames.size > prevIndex) data.pagenames[prevIndex] else data.title,
            progress = 0
        )
        _playerData.value = newData
        switchPendingSeekMs = 0L
        _isPrepared.value = false
        _isLoading.value = true
    }

    fun updateProgress() {
        if (_isPrepared.value) {
            _currentProgress.value = _player.currentPosition
        }
    }

    fun updateBufferSpeed() {
        val speedBytes = _player.tcpSpeed
        _bufferSpeed.value = if (speedBytes > 0) {
            when {
                speedBytes >= 1024 * 1024 -> String.format("%.1f MB/s", speedBytes / (1024f * 1024f))
                speedBytes >= 1024 -> String.format("%.1f KB/s", speedBytes / 1024f)
                else -> "$speedBytes B/s"
            }
        } else {
            if (!_isPrepared.value) "加载中..." else "缓冲中..."
        }
    }

    fun clearBufferSpeed() {
        _bufferSpeed.value = ""
    }

    fun release() {
        if (released || !::_player.isInitialized) return
        released = true
        if (_isPrepared.value) {
            try { _currentProgress.value = _player.currentPosition } catch (_: Exception) {}
        }
        reportProgress()
        heartbeatJob?.cancel()
        bufferSpeedJob?.cancel()
        liveElapsedJob?.cancel()
        _player.release()
    }

    // --- Private ---

    private fun reportProgress() {
        val data = _playerData.value
        if (isLive || isLocal) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pos = _currentProgress.value / 1000
                val isBangumi = data.type == PlayerData.TYPE_BANGUMI && data.epid > 0
                if (!isBangumi) {
                    HistoryApi.reportHistory(data.aid, data.cid, pos)
                }
                HeartbeatApi.reportHeartbeat(
                    aid = data.aid, bvid = data.bvid, cid = data.cid,
                    playedTime = pos, startTs = startTs,
                    type = if (isBangumi) "4" else "3",
                    subType = if (isBangumi) "1" else null,
                    epid = if (isBangumi) data.epid else null,
                    sid = if (isBangumi) data.sid else null,
                    videoDuration = (_totalDuration.value / 1000).coerceAtLeast(0)
                )
            } catch (_: Exception) {}
        }
    }

    private fun startHeartbeatLoop() {
        startTs = _currentProgress.value / 1000
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive && _isPrepared.value) {
                delay(15.seconds)
                updateProgress()
                if (!isLocal) {
                    reportProgress()
                }
            }
        }
    }

    fun updateCurrentSubtitle(progress: Long) {
        val subs = _subtitles.value
        if (subs.isEmpty()) {
            _currentSubtitle.value = null
            return
        }
        val timeSec = progress / 1000.0
        if (subtitleCursor !in subs.indices || timeSec < subs[subtitleCursor].from) {
            var low = 0
            var high = subs.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (subs[mid].to <= timeSec) low = mid + 1 else high = mid
            }
            subtitleCursor = low.coerceAtMost(subs.lastIndex)
        } else {
            while (subtitleCursor < subs.lastIndex && timeSec >= subs[subtitleCursor].to) {
                subtitleCursor++
            }
        }
        val candidate = subs[subtitleCursor]
        val matched = candidate.takeIf { timeSec >= it.from && timeSec < it.to }
        _currentSubtitle.value = matched?.content
    }

    fun startBufferSpeedLoop() {
        bufferSpeedJob?.cancel()
        bufferSpeedJob = viewModelScope.launch {
            while (isActive && _isLoading.value) {
                updateBufferSpeed()
                delay(1.seconds)
            }
            _bufferSpeed.value = ""
        }
    }

    fun startLiveElapsedTimer(timeStamp: Long) {
        liveElapsedJob?.cancel()
        if (timeStamp <= 0) {
            _liveElapsedSeconds.value = 0L
            return
        }
        liveElapsedJob = viewModelScope.launch {
            while (isActive) {
                _liveElapsedSeconds.value = (System.currentTimeMillis() / 1000) - timeStamp
                delay(1.seconds)
            }
        }
    }

    override fun onCleared() {
        release()
        super.onCleared()
    }
}
