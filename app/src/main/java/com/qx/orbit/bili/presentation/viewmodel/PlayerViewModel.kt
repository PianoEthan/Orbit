package com.qx.orbit.bili.presentation.viewmodel

import android.content.Context
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qx.orbit.bili.BuildConfig
import com.qx.orbit.bili.data.api.CookiesApi
import com.qx.orbit.bili.data.api.HeartbeatApi
import com.qx.orbit.bili.data.api.HistoryApi
import com.qx.orbit.bili.data.api.LiveApi
import com.qx.orbit.bili.data.api.InteractionVideoApi
import com.qx.orbit.bili.data.model.InteractionVideo
import com.qx.orbit.bili.data.model.InteractionVideoResult
import com.qx.orbit.bili.data.model.PlayerMetadata
import com.qx.orbit.bili.presentation.player.InteractionPlaybackCoordinator
import com.qx.orbit.bili.data.api.PlayerApi
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.model.Subtitle
import com.qx.orbit.bili.data.interaction.InteractionProgressStore
import com.qx.orbit.bili.data.model.SubtitleLink
import com.qx.orbit.bili.data.remote.CookieManager
import com.qx.orbit.bili.presentation.player.effectivePlaybackSpeed
import com.qx.orbit.bili.util.player.OrbitPlayer
import com.qx.orbit.bili.util.player.DelegatingOrbitPlayer
import com.qx.orbit.bili.util.player.createOrbitPlayer
import com.qx.orbit.bili.util.player.media3.Media3PlayerWrapper
import kotlinx.coroutines.CoroutineScope
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

class PlayerViewModel(
    metadata: suspend (Long, Long) -> InteractionVideoResult<PlayerMetadata> = { aid, cid -> PlayerApi.getMetadata(aid, cid) },
    edge: suspend (PlayerData, Long, Long?) -> InteractionVideoResult<InteractionVideo> = { data, graph, id ->
        InteractionVideoApi.getEdgeInfo(aid = data.aid, bvid = data.bvid, graphVersion = graph, edgeId = id)
    },
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val reportPlayback: suspend (PlayerData, Long, Long, Long) -> Unit = ::sendProgress,
    private val progressStoreProvider: ((Context) -> InteractionProgressStore)? = null
) : ViewModel() {
    private var progressStore: InteractionProgressStore? = null
    private var foreground = true
    private var userPaused = false
    private var decisionPaused = false
    private var resumeAfterBackground = false
    private var playbackEnded = false
    private var interactionObserver: Job? = null
    val interaction: InteractionPlaybackCoordinator = InteractionPlaybackCoordinator(
        viewModelScope, metadata, edge,
        stopMedia = { beginPlaybackPreparation() },
        requestMedia = { request ->
            if (request.token != null) {
                ensureMedia3ForInteractive()
            }
            _playerData.value = request.data
            if (request.token != null) {
                switchPendingSeekMs = request.seekToMs ?: 0L
                _currentProgress.value = 0L
            }
            setSubtitles(emptyArray())
            setSubtitleLinks(emptyArray())
            setCurrentSubtitleId(0)
        },
        storeProvider = { progressStore },
        accountKeyProvider = { runCatching { CookieManager.getMid() }.getOrDefault(0L) }
    )

    fun open(data: PlayerData) {
        _playerData.value = data
        userPaused = false
        if (data.interactionHint) {
            ensureMedia3ForInteractive()
        }
        interaction.open(data)
    }

    fun setForeground(value: Boolean) {
        if (foreground && !value && interaction.state.value.interactive) {
            resumeAfterBackground = !userPaused && (_isPlaying.value || decisionPaused || playbackEnded)
        }
        foreground = value
        observeInteraction()
        if (!value) {
            interactionObserver?.cancel()
            if (interaction.state.value.interactive) pauseForDecision()
        } else if (_isPrepared.value) {
            resumeBackgroundPlayback()
            startInteractionObserver()
        }
    }

    private fun resumeBackgroundPlayback() {
        if (!resumeAfterBackground || !foreground || !playbackAllowed || !_isPrepared.value || userPaused || released) return
        val state = interaction.state.value
        if (state.error != null || state.session.problem != null || state.session.transition != null || state.ended) {
            resumeAfterBackground = false
            return
        }
        resumeAfterBackground = false
        if (state.session.question?.pauseRequested == true) {
            pauseForDecision()
        } else if (!playbackEnded) {
            _player.start()
            _isPlaying.value = true
        }
        observeInteraction()
    }

    fun prepareInteractionMedia(request: InteractionPlaybackCoordinator.MediaRequest, reloading: Boolean = false): Boolean {
        if (!interaction.mediaPrepared(request, reloading)) return false
        playbackEnded = false
        decisionPaused = false
        if (!reloading) request.seekToMs?.let { switchPendingSeekMs = it }
        return true
    }

    fun chooseInteraction(question: Int, choice: Int, visit: Long?, generation: Long) {
        userPaused = false
        interaction.choose(question, choice, visit, generation)
    }

    private fun pauseForDecision() {
        setLongPressSpeedUp(false)
        if (::_player.isInitialized && _isPrepared.value && _isPlaying.value) _player.pause()
        _isPlaying.value = false
        decisionPaused = foreground && !userPaused && interaction.state.value.session.question?.pauseRequested == true
    }

    fun observeInteraction(discontinuity: Boolean = false) {
        if (!interaction.state.value.interactive || !_isPrepared.value) return
        val advance = foreground && !userPaused && !_isLoading.value &&
            (_isPlaying.value || decisionPaused || playbackEnded)
        val position = if (playbackEnded) _totalDuration.value else savePosition().coerceAtLeast(0)
        interaction.observe(position, _totalDuration.value, playbackEnded,
            monotonicMs(), advance, discontinuity)
        val state = interaction.state.value
        if (state.session.question?.pauseRequested == true || state.error != null || !foreground) pauseForDecision()
        if (state.blocksControls) setLongPressSpeedUp(false)
    }

    private fun startInteractionObserver() {
        interactionObserver?.cancel()
        if (!interaction.state.value.interactive || !foreground) return
        interactionObserver = viewModelScope.launch {
            while (isActive && _isPrepared.value && foreground) {
                observeInteraction()
                delay(150.milliseconds)
            }
        }
    }

    private var appContext: Context? = null
    private lateinit var _player: OrbitPlayer
    val player: OrbitPlayer get() = _player

    fun initPlayer(context: Context, isInteractive: Boolean = false) {
        appContext = context.applicationContext
        if (progressStore == null) {
            val appCtx = context.applicationContext
            progressStore = progressStoreProvider?.invoke(appCtx)
                ?: InteractionProgressStore(appCtx.filesDir.resolve("interaction_progress"))
        }
        if (::_player.isInitialized) return
        val interactive = isInteractive || _playerData.value.interactionHint || interaction.state.value.interactive
        val basePlayer = createOrbitPlayer(context.applicationContext, interactive)
        _player = DelegatingOrbitPlayer(basePlayer)
    }

    fun ensureMedia3ForInteractive() {
        val ctx = appContext ?: return
        val current = if (::_player.isInitialized) _player else null
        if (current is DelegatingOrbitPlayer && current.delegate !is Media3PlayerWrapper) {
            current.switchTo(Media3PlayerWrapper(ctx))
        }
    }

    // --- State ---
    private val _playerData = MutableStateFlow(PlayerData())
    val playerData: StateFlow<PlayerData> = _playerData.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _bufferingRevision = MutableStateFlow(0L)
    val bufferingRevision: StateFlow<Long> = _bufferingRevision.asStateFlow()

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
    private val _effectiveSpeed = MutableStateFlow(1f)
    val effectiveSpeed: StateFlow<Float> = _effectiveSpeed.asStateFlow()
    private var playbackAllowed = true

    private val _liveElapsedSeconds = MutableStateFlow(0L)
    val liveElapsedSeconds: StateFlow<Long> = _liveElapsedSeconds.asStateFlow()

    // --- Internal ---
    private var switchPendingSeekMs = -1L
    private var relativeSeekTargetMs: Long? = null
    private var lastRelativeSeekNanos = 0L
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
        interactionObserver?.cancel()
        playbackEnded = false
        decisionPaused = false
        resumeAfterBackground = false
        relativeSeekTargetMs = null
        setLongPressSpeedUp(false)
        if (::_player.isInitialized && !released) {
            if (_isPrepared.value) _currentProgress.value = savePosition()
            try { _player.pause() } catch (_: Exception) {}
        }
        _isPlaying.value = false
        _isPrepared.value = false
        _isLoading.value = true
        _errorMessage.value = null
        _totalDuration.value = 0L
        heartbeatStarted = false
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun setPlaybackAllowed(allowed: Boolean) {
        playbackAllowed = allowed
        if (!allowed) setLongPressSpeedUp(false) else resumeBackgroundPlayback()
    }

    fun setError(msg: String?) {
        _errorMessage.value = msg
        if (msg != null) {
            setLongPressSpeedUp(false)
            _isPlaying.value = false
            _isLoading.value = false
        }
    }

    fun setSubtitles(subs: Array<Subtitle>) {
        _subtitles.value = subs
        _currentSubtitle.value = null
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
        private suspend fun sendProgress(data: PlayerData, position: Long, started: Long, duration: Long) {
            val bangumi = data.type == PlayerData.TYPE_BANGUMI && data.epid > 0
            if (!bangumi) HistoryApi.reportHistory(data.aid, data.cid, position)
            HeartbeatApi.reportHeartbeat(
                aid = data.aid, bvid = data.bvid, cid = data.cid,
                playedTime = position, startTs = started,
                type = if (bangumi) "4" else "3",
                subType = if (bangumi) "1" else null,
                epid = if (bangumi) data.epid else null,
                sid = if (bangumi) data.sid else null,
                videoDuration = duration
            )
        }

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
            _player.setOption(OrbitPlayer.OPT_CATEGORY_PLAYER, "vn", if (isAudioOnlyMode) 1 else 0)
        }
        if (isAudioOnlyMode) {
            // Power-saving: disable video decode (IJK only), minimize buffer
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "reconnect_delay_max", 2)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 1024)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "timeout", 5000000)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 0)
            _player.setOption(OrbitPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 0)
        }
        // Local cached videos: keep video URL (surface already detached → video decode stops, saves battery)
        // Network videos: use audio-only stream to save bandwidth
        val rawPlayUrl = if (isLocal) {
            data.videoUrl
        } else if (isAudioOnlyMode && data.audioUrl.isNotEmpty()) {
            data.audioUrl
        } else {
            data.videoUrl
        }
        val playUrl = if (isLocal) {
            val file = File(rawPlayUrl)
            if (!file.exists()) {
                val alt1 = File(rawPlayUrl.replace(" ", "+"))
                val alt2 = File(rawPlayUrl.replace("+", " "))
                if (alt1.exists()) alt1.absolutePath
                else if (alt2.exists()) alt2.absolutePath
                else rawPlayUrl
            } else rawPlayUrl
        } else rawPlayUrl
        _player.dataSource = playUrl
        _player.prepareAsync()
    }

    fun onPrepared() {
        if (released) return
        setLongPressSpeedUp(false)
        _isPrepared.value = true
        _isLoading.value = false
        _errorMessage.value = null
        _totalDuration.value = _player.duration.coerceAtLeast(0L)
        _currentProgress.value = 0L

        if (!isLive && switchPendingSeekMs >= 0) {
            val targetMs = switchPendingSeekMs.coerceIn(0L, _totalDuration.value)
            _player.seekTo(targetMs)
            _currentProgress.value = targetMs
            switchPendingSeekMs = -1L
        } else if (!isLive && _playerData.value.progress > 0) {
            val targetMs = (_playerData.value.progress * 1000L).coerceAtMost(_totalDuration.value)
            _player.seekTo(targetMs)
            _currentProgress.value = targetMs
            _playerData.value = _playerData.value.copy(progress = 0)
        }

        applyEffectiveSpeed()
        playbackEnded = false
        if (playbackAllowed) play()
        startInteractionObserver()

        if (!heartbeatStarted && !isLive) {
            heartbeatStarted = true
            startHeartbeatLoop()
        }
    }

    fun onCompletion() {
        setLongPressSpeedUp(false)
        _isPlaying.value = false
        _isLoading.value = false
        if (!isLive) _currentProgress.value = _totalDuration.value
        if (interaction.state.value.interactive) {
            playbackEnded = true
            reportProgress()
            observeInteraction()
            return
        }
        val data = _playerData.value
        val isBangumi = data.type == PlayerData.TYPE_BANGUMI && data.epid > 0
        val isLocalMulti = data.type == PlayerData.TYPE_LOCAL && data.cids.size > 1
        val isVideoMulti = data.type == PlayerData.TYPE_VIDEO && data.cids.size > 1
        if ((isBangumi || isLocalMulti || isVideoMulti) && data.currentPageIndex + 1 < data.cids.size) {
            switchToNextEpisode()
            return
        }
        if (!isLive && !isLocal) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
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
        setError("播放器错误: $what")
        _isPrepared.value = false
    }

    fun onInfo(what: Int, extra: Int) {
        when (what) {
            OrbitPlayer.MEDIA_INFO_BUFFERING_START -> {
                setLongPressSpeedUp(false)
                _isLoading.value = true
                _bufferingRevision.value++
            }
            OrbitPlayer.MEDIA_INFO_BUFFERING_END -> {
                _isLoading.value = false
                _bufferingRevision.value++
            }
        }
        observeInteraction()
    }

    fun onVideoSizeChanged(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            _videoWidth.value = width.toFloat()
            _videoHeight.value = height.toFloat()
        }
    }

    fun play() {
        if (released || !_isPrepared.value || !playbackAllowed || interaction.state.value.blocksControls ||
            interaction.state.value.interactive && !foreground) return
        userPaused = false
        decisionPaused = false
        _player.start()
        _isPlaying.value = true
    }

    fun pause() {
        userPaused = true
        decisionPaused = false
        resumeAfterBackground = false
        setLongPressSpeedUp(false)
        if (released || !::_player.isInitialized) return
        if (_isPrepared.value) {
            _currentProgress.value = savePosition()
            _player.pause()
        }
        _isPlaying.value = false
        observeInteraction()
        reportProgress()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekTo(ms: Long) {
        if (released || !_isPrepared.value || isLive || _totalDuration.value <= 0L || interaction.state.value.blocksControls) return
        relativeSeekTargetMs = null
        setLongPressSpeedUp(false)
        val target = ms.coerceIn(0L, _totalDuration.value)
        _player.seekTo(target)
        _currentProgress.value = target
        playbackEnded = false
        if (interaction.state.value.interactive) {
            interaction.observe(target, _totalDuration.value, false, monotonicMs(), false, discontinuity = true)
        }
    }

    fun seekBy(deltaMs: Float): Long? {
        if (released || !_isPrepared.value || isLive || _totalDuration.value <= 0L || !deltaMs.isFinite()) return null
        val now = System.nanoTime()
        val position = relativeSeekTargetMs?.takeIf { now - lastRelativeSeekNanos < 500_000_000L }
            ?: savePosition()
        val target = (position.toDouble() + deltaMs).coerceIn(0.0, _totalDuration.value.toDouble()).toLong()
        seekTo(target)
        relativeSeekTargetMs = target
        lastRelativeSeekNanos = now
        return target
    }

    fun setSpeed(speed: Float) {
        if (!speed.isFinite() || speed <= 0f) return
        _playbackSpeed.value = speed
        applyEffectiveSpeed()
    }

    fun setLongPressSpeedUp(enabled: Boolean) {
        val active = enabled && !released && playbackAllowed && _isPrepared.value &&
            _isPlaying.value && !_isLoading.value && !isLive && !interaction.state.value.blocksControls
        if (_isLongPressSpeedUp.value == active) return
        _isLongPressSpeedUp.value = active
        applyEffectiveSpeed()
    }

    private fun applyEffectiveSpeed() {
        val speed = effectivePlaybackSpeed(_playbackSpeed.value, _isLongPressSpeedUp.value)
        _effectiveSpeed.value = speed
        if (::_player.isInitialized && !released) {
            try { _player.setSpeed(speed) } catch (_: Exception) {}
        }
    }

    fun switchToNextEpisode() = switchToEpisode(_playerData.value.currentPageIndex + 1)

    fun switchToPreviousEpisode() = switchToEpisode(_playerData.value.currentPageIndex - 1)

    fun switchToEpisode(index: Int) {
        if (interaction.state.value.blocksEpisodes) return
        val data = _playerData.value
        if (index !in data.cids.indices || index == data.currentPageIndex) return
        val newData = data.copy(
            currentPageIndex = index,
            aid = data.aids.getOrNull(index) ?: data.aid,
            cid = data.cids[index],
            epid = data.epids.getOrNull(index) ?: data.epid,
            title = data.pagenames.getOrNull(index) ?: data.title,
            videoUrl = if (isLocal) data.localUrls.getOrNull(index) ?: data.videoUrl else "",
            audioUrl = if (isLocal) data.audioUrl else "",
            dashData = null,
            progress = 0
        )
        switchPendingSeekMs = 0L
        open(newData)
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
        interactionObserver?.cancel()
        interaction.close()
        setLongPressSpeedUp(false)
        if (released || !::_player.isInitialized) return
        released = true
        if (_isPrepared.value) {
            try { _currentProgress.value = _player.currentPosition } catch (_: Exception) {}
        }
        _isPlaying.value = false
        _isPrepared.value = false
        _isLoading.value = false
        reportProgress()
        heartbeatJob?.cancel()
        heartbeatJob = null
        bufferSpeedJob?.cancel()
        bufferSpeedJob = null
        liveElapsedJob?.cancel()
        liveElapsedJob = null

        val p = _player
        try {
            p.setSurface(null)
            p.setDisplay(null)
            p.pause()
        } catch (_: Exception) {}

        CoroutineScope(Dispatchers.IO).launch {
            try {
                p.release()
            } catch (_: Exception) {}
        }
    }

    // --- Private ---

    private fun reportProgress() {
        val data = _playerData.value
        if (isLive || isLocal) return
        val interactionState = interaction.state.value
        if (interactionState.interactive && (interactionState.session.visitId == null || interactionState.session.transition != null)) return
        val pos = _currentProgress.value / 1000
        val duration = (_totalDuration.value / 1000).coerceAtLeast(0)
        val started = startTs
        viewModelScope.launch(Dispatchers.IO) {
            try { reportPlayback(data, pos, started, duration) } catch (_: Exception) {}
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
