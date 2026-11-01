package com.qx.orbit.bili.presentation.player

import com.qx.orbit.bili.data.model.InteractionVideo
import com.qx.orbit.bili.data.model.InteractionVideoResult
import com.qx.orbit.bili.data.model.PlayerData
import com.qx.orbit.bili.data.model.PlayerMetadata
import com.qx.orbit.bili.util.interaction.InteractionSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class InteractionPlaybackCoordinator(
    private val scope: CoroutineScope,
    private val metadata: suspend (Long, Long) -> InteractionVideoResult<PlayerMetadata>,
    private val edge: suspend (PlayerData, Long, Long?) -> InteractionVideoResult<InteractionVideo>,
    private val stopMedia: () -> Unit,
    private val requestMedia: (MediaRequest) -> Unit
) {
    data class MediaRequest(
        val epoch: Long,
        val revision: Long,
        val data: PlayerData,
        val metadata: PlayerMetadata,
        val token: InteractionSession.Token? = null,
        val seekToMs: Long? = null
    )

    data class State(
        val probing: Boolean = false,
        val interactive: Boolean = false,
        val session: InteractionSession.State = InteractionSession.State(),
        val node: InteractionVideo? = null,
        val error: String? = null,
        val requiresRestart: Boolean = false,
        val ended: Boolean = false
    ) {
        val blocksControls: Boolean get() = probing || error != null || interactive &&
            (session.question != null || session.transition != null || session.problem != null || ended)
        val blocksEpisodes: Boolean get() = probing || interactive || error != null
    }

    private val session = InteractionSession()
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val _request = MutableStateFlow<MediaRequest?>(null)
    val request = _request.asStateFlow()
    private var source = PlayerData()
    private var epoch = 0L
    private var revision = 0L
    private var job: Job? = null
    private var root: InteractionVideo? = null
    private var current: InteractionVideo? = null
    private var pending: InteractionVideo? = null
    private var pendingMetadata = PlayerMetadata()
    private val metadataCache = linkedMapOf<Long, PlayerMetadata>()

    fun open(data: PlayerData) {
        epoch++
        job?.cancel()
        source = data.copy()
        root = null
        current = null
        pending = null
        metadataCache.clear()
        session.reset()
        _request.value = null
        _state.value = State(probing = data.type == PlayerData.TYPE_VIDEO)
        stopMedia()
        if (data.type != PlayerData.TYPE_VIDEO) return
        probe()
    }

    private fun probe() {
        val expected = epoch
        job = scope.launch {
            try {
                val info = value(metadata(source.aid, source.cid))
                checkEpoch(expected)
                cache(source.cid, info)
                val interaction = info.interaction
                if (interaction == null) {
                    check(!source.interactionHint) { "互动视频缺少剧情信息，请重新开始" }
                    _state.value = State()
                    publish(source, info, null, null)
                    return@launch
                }
                _state.value = _state.value.copy(interactive = true)
                checkGraph(info)
                val node = freeze(value(edge(source, interaction.graphVersion, null)))
                checkEpoch(expected)
                val cid = node.storyList.firstOrNull { it.edgeId == node.edgeId && it.isCurrent == 1 }?.cid
                    ?: node.storyList.firstOrNull { it.edgeId == node.edgeId }?.cid
                    ?: error("剧情根节点缺少 CID，请重新开始")
                root = node
                pending = node
                pendingMetadata = nodeMetadata(cid, interaction.graphVersion, expected)
                handle(session.load(interaction.graphVersion, node, cid))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (expected == epoch) fail(failure)
            }
        }
    }

    private suspend fun nodeMetadata(cid: Long, graph: Long, expected: Long): PlayerMetadata {
        val info = metadataCache[cid] ?: value(metadata(source.aid, cid))
        checkEpoch(expected)
        checkGraph(info)
        check(info.interaction?.graphVersion == graph) { "剧情版本已变化，请重新开始" }
        cache(cid, info)
        return info
    }

    private fun cache(cid: Long, info: PlayerMetadata) {
        metadataCache.remove(cid)
        metadataCache[cid] = info
        if (metadataCache.size > 16) metadataCache.remove(metadataCache.keys.first())
    }

    private fun checkGraph(info: PlayerMetadata) {
        if (info.interaction?.needReload?.let { it != 0 } == true) throw RestartRequired("剧情已更新，请重新开始")
    }

    private fun handle(event: InteractionSession.Event) {
        when (event) {
            is InteractionSession.Target -> {
                stopMedia()
                _request.value = null
                val expected = epoch
                job?.cancel()
                job = scope.launch {
                    try {
                        val graph = session.state.graphVersion ?: return@launch
                        val node = freeze(value(edge(source, graph, event.edgeId)))
                        checkEpoch(expected)
                        val info = nodeMetadata(event.cid, graph, expected)
                        if (session.state.transition?.token != event.token) return@launch
                        pending = node
                        pendingMetadata = info
                        handle(session.targetReceived(event.token, node))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        if (expected == epoch) {
                            session.transitionFailed(event.token, failure.message ?: "剧情加载失败")
                            fail(failure)
                        }
                    }
                }
            }
            is InteractionSession.PrepareMedia -> {
                stopMedia()
                publish(source.copy(
                    cid = event.cid, progress = 0, cidHistory = 0,
                    videoUrl = "", audioUrl = "", dashData = null,
                    qnStrList = null, qnValueList = null
                ), pendingMetadata, event.token, event.seekToMs)
            }
            is InteractionSession.Committed -> {
                current = pending
                pending = null
            }
            else -> Unit
        }
        sync()
    }

    private fun publish(data: PlayerData, info: PlayerMetadata, token: InteractionSession.Token?, seek: Long?) {
        val request = MediaRequest(epoch, ++revision, data, info, token, seek)
        _request.value = request
        requestMedia(request)
    }

    fun accepts(request: MediaRequest): Boolean = request == _request.value && request.epoch == epoch &&
        (request.token == null || session.state.transition?.token == request.token || session.state.visitId != null && session.state.transition == null)

    fun mediaPrepared(request: MediaRequest, reloading: Boolean = false): Boolean {
        if (!accepts(request)) return false
        if (request.token == null) return true
        if (reloading && session.state.transition == null && session.state.visitId != null) return true
        if (session.state.transition?.token != request.token) return false
        val event = session.mediaPrepared(request.token)
        handle(event)
        return event is InteractionSession.Committed
    }

    fun mediaFailed(request: MediaRequest, message: String) {
        if (!accepts(request)) return
        stopMedia()
        request.token?.let { session.transitionFailed(it, message) }
        _state.value = _state.value.copy(error = message)
        sync()
    }

    fun choose(question: Int, choice: Int, visit: Long?, generation: Long) {
        handle(session.select(question, choice, visit, generation))
    }

    fun observe(position: Long, duration: Long, ended: Boolean, now: Long, advance: Boolean, discontinuity: Boolean = false) {
        if (!_state.value.interactive) return
        handle(session.observePlayback(position, duration, ended, now, advance, discontinuity))
        if (ended && session.state.transition == null && session.state.question == null && session.state.problem == null) {
            _state.value = _state.value.copy(ended = true)
        }
    }

    fun retry() {
        if (_state.value.requiresRestart) return
        _state.value = _state.value.copy(error = null, ended = false)
        if (!_state.value.interactive || root == null) {
            _state.value = _state.value.copy(probing = true)
            probe()
        } else if (session.state.transition != null) {
            handle(session.retry())
        } else {
            val previous = _request.value ?: return
            stopMedia()
            publish(previous.data.copy(videoUrl = "", audioUrl = "", dashData = null), previous.metadata, null, null)
        }
    }

    fun restart() = open(source.copy(progress = 0, cidHistory = 0, videoUrl = "", audioUrl = "", dashData = null))

    fun close() {
        epoch++
        job?.cancel()
        session.reset()
        _request.value = null
        metadataCache.clear()
    }

    private fun sync() {
        _state.value = _state.value.copy(
            probing = false, session = session.state, node = current,
            error = _state.value.error ?: session.state.problem?.message
        )
    }

    private fun fail(failure: Exception) {
        stopMedia()
        _state.value = _state.value.copy(
            probing = false, session = session.state,
            error = failure.message ?: "剧情加载失败",
            requiresRestart = failure is RestartRequired || failure.message?.contains("重新开始") == true
        )
    }

    private suspend fun checkEpoch(expected: Long) {
        coroutineContext.ensureActive()
        if (expected != epoch) throw CancellationException("Superseded playback")
    }

    private fun <T> value(result: InteractionVideoResult<T>): T = when (result) {
        is InteractionVideoResult.Success -> result.data
        is InteractionVideoResult.GraphInvalid -> throw RestartRequired("剧情版本失效 (99003)，请重新开始")
        is InteractionVideoResult.ApiError -> error("剧情请求失败 (${result.code}): ${result.message}")
        is InteractionVideoResult.VoucherRequired -> error("请求需要风控验证，请在哔哩哔哩完成验证后重试")
        is InteractionVideoResult.TransportError -> throw result.cause
        is InteractionVideoResult.InvalidResponse -> error("剧情数据无效: ${result.cause.message}")
    }

    private fun freeze(node: InteractionVideo) = node.copy(
        storyList = node.storyList.toList(), hiddenVars = node.hiddenVars.toList(),
        edges = node.edges?.copy(questions = node.edges.questions.map { question ->
            question.copy(choices = question.choices.map { it.copy(selected = null, submited = null) })
        })
    )

    private class RestartRequired(message: String) : Exception(message)
}
