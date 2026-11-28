package com.qx.orbit.bili.vision

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.qx.orbit.bili.core.api.DanmakuGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.xml.XMLConstants
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

internal actual class VisionDanmakuHost actual constructor(private val gateway: DanmakuGateway) {
    private var loadJob: Job? = null
    private var cid = 0L
    private var anchor by mutableStateOf(DanmakuAnchor(0, visionElapsedTimeMs(), false, 1f))
    internal var cues by mutableStateOf(emptyList<DesktopDanmakuCue>())
        private set
    internal var showDanmaku by mutableStateOf(VisionPreferences.danmakuEnabled)
        private set
    internal var alpha by mutableStateOf(VisionPreferences.danmakuAlpha)
        private set
    internal var textScale by mutableStateOf(VisionPreferences.danmakuTextScale)
        private set
    internal val playing: Boolean get() = anchor.playing

    actual fun load(cid: Long, scope: CoroutineScope) {
        if (this.cid == cid) return
        this.cid = cid
        loadJob?.cancel()
        cues = emptyList()
        if (cid <= 0) return
        loadJob = scope.launch {
            try {
                val cueList = withContext(Dispatchers.IO) {
                    gateway.loadXmlDanmaku(cid)?.let(::parseDesktopDanmaku).orEmpty()
                }
                if (this@VisionDanmakuHost.cid == cid) cues = cueList
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Playback continues when the optional danmaku stream cannot be loaded.
            }
        }
    }

    internal fun position(now: Long): Long = with(anchor) {
        positionMs + if (playing) ((now - timeMs).coerceAtLeast(0) * speed).toLong() else 0
    }

    actual fun synchronize(positionMs: Long, playing: Boolean, speed: Float) {
        anchor = DanmakuAnchor(positionMs, visionElapsedTimeMs(), playing, speed)
    }

    actual fun setPlaying(playing: Boolean) {
        if (this.playing != playing) synchronize(position(visionElapsedTimeMs()), playing, anchor.speed)
    }

    actual fun setEnabled(enabled: Boolean) { showDanmaku = enabled }

    actual fun updateConfig(alpha: Float, textScale: Float) {
        this.alpha = alpha
        this.textScale = textScale
    }

    actual fun release() {
        loadJob?.cancel()
        cues = emptyList()
        cid = 0
    }
}

private data class DanmakuAnchor(val positionMs: Long, val timeMs: Long, val playing: Boolean, val speed: Float)

internal data class DesktopDanmakuCue(val positionMs: Long, val mode: Int, val fontSize: Int, val color: Int, val text: String)

private fun parseDesktopDanmaku(xml: ByteArray): List<DesktopDanmakuCue> {
    val factory = XMLInputFactory.newFactory().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    }
    val reader = factory.createXMLStreamReader(xml.inputStream())
    try {
        val cues = mutableListOf<DesktopDanmakuCue>()
        while (reader.hasNext() && cues.size < 20_000) {
            if (reader.next() != XMLStreamConstants.START_ELEMENT || reader.localName != "d") continue
            val fields = reader.getAttributeValue(null, "p")?.split(',', limit = 5).orEmpty()
            val position = fields.getOrNull(0)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: continue
            val mode = fields.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..6 } ?: continue
            val fontSize = fields.getOrNull(2)?.toIntOrNull()?.coerceIn(12, 36) ?: 25
            val color = fields.getOrNull(3)?.toIntOrNull() ?: 0xffffff
            val text = reader.elementText.take(120)
            if (text.isNotBlank()) cues += DesktopDanmakuCue((position * 1000).toLong(), mode, fontSize, color, text)
        }
        return cues.sortedBy { it.positionMs }
    } finally {
        reader.close()
    }
}

@Composable
internal actual fun VisionDanmakuLayer(host: VisionDanmakuHost, modifier: Modifier) {
    if (!host.showDanmaku || host.cues.isEmpty()) return
    val measurer = rememberTextMeasurer(cacheSize = 64)
    var frameTime by remember(host) { mutableLongStateOf(visionElapsedTimeMs()) }
    LaunchedEffect(host, host.playing) {
        while (host.playing) withFrameNanos { frameTime = visionElapsedTimeMs() }
    }
    Canvas(modifier) {
        val now = host.position(frameTime)
        val cues = host.cues
        val first = cues.binarySearchBy(now - 7000L) { it.positionMs }.let { if (it < 0) -it - 1 else it }
        val laneHeight = (32 * host.textScale).sp.toPx()
        val lanes = (size.height * 0.45f / laneHeight).toInt().coerceAtLeast(1)
        for (index in first until minOf(cues.size, first + 60)) {
            val cue = cues[index]
            if (cue.positionMs > now) break
            val age = now - cue.positionMs
            val fixed = cue.mode == 4 || cue.mode == 5
            if (fixed && age > 4000) continue
            val color = Color(cue.color or 0xff000000.toInt()).copy(alpha = host.alpha)
            val text = measurer.measure(
                cue.text,
                TextStyle(color = color, fontSize = (cue.fontSize * host.textScale).sp, shadow = Shadow(Color.Black, Offset(1f, 1f), 2f)),
                softWrap = false,
            )
            val progress = (age / 7000f).coerceIn(0f, 1f)
            val x = when {
                fixed -> (size.width - text.size.width) / 2
                cue.mode == 6 -> (size.width + text.size.width) * progress - text.size.width
                else -> size.width - (size.width + text.size.width) * progress
            }
            val top = (index % lanes) * laneHeight
            val y = if (cue.mode == 4) size.height - top - laneHeight else top
            drawText(text, topLeft = Offset(x, y))
        }
    }
}
