package com.qx.orbit.bili.vision

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal class MpvPlaybackSession(
    private val address: String,
    private val audioAddress: String?,
    positionMs: Long,
    playbackSpeed: Float,
    private val onUpdate: (Status) -> Unit,
    private val onFrame: (Image) -> Unit,
    private val onEnd: (String?, Boolean) -> Unit,
) {
    data class Status(val positionMs: Long, val durationMs: Long, val playing: Boolean, val buffering: Boolean)

    private data class FrameSize(val width: Int, val height: Int)

    private val closed = AtomicBoolean()
    private val commands = ConcurrentLinkedQueue<MpvNative.(Pointer) -> Unit>()
    @Volatile private var size: FrameSize? = null
    private var loaded = false
    private var paused = false
    private var position = positionMs
    private var speed = playbackSpeed

    fun start() {
        thread(name = "Vision mpv playback", isDaemon = true) {
            try {
                val mpv = MpvNative.instance
                val handle = checkNotNull(mpv.mpv_create())
                try {
                    check(mpv.mpv_set_option_string(handle, "vo", "libmpv") >= 0)
                    check(mpv.mpv_set_option_string(handle, "hwdec", "auto-copy") >= 0)
                    check(mpv.mpv_set_option_string(handle, "pause", "yes") >= 0)
                    check(mpv.mpv_set_option_string(handle, "referrer", "https://www.bilibili.com/") >= 0)
                    check(mpv.mpv_set_option_string(handle, "user-agent", "Mozilla/5.0 Chrome/122 Safari/537.36") >= 0)
                    mpv.mpv_set_option_string(handle, "config", "no")
                    mpv.mpv_set_option_string(handle, "ytdl", "no")
                    mpv.mpv_set_option_string(handle, "load-scripts", "no")
                    mpv.mpv_set_option_string(handle, "audio-display", "no")
                    check(mpv.mpv_initialize(handle) >= 0)

                    val ready = CompletableFuture<Unit>()
                    val renderer = thread(name = "Vision mpv video", isDaemon = true) { render(mpv, handle, ready) }
                    try {
                        ready.get()
                        if (closed.get()) return@thread
                        if (mpv.command(handle, "loadfile", address) < 0) onEnd("mpv 无法打开播放地址", true)
                        else readEvents(mpv, handle)
                    } catch (_: ExecutionException) {
                        if (!closed.get()) onEnd("mpv 无法初始化视频输出", false)
                    } catch (_: RuntimeException) {
                        if (!closed.get()) onEnd("mpv 播放失败", false)
                    } finally {
                        closed.set(true)
                        renderer.join()
                    }
                } finally {
                    mpv.mpv_terminate_destroy(handle)
                }
            } catch (_: LinkageError) {
                if (!closed.get()) onEnd("无法加载 libmpv，请检查应用安装完整性。", false)
            } catch (_: Exception) {
                if (!closed.get()) onEnd("mpv 无法初始化播放器", false)
            } finally {
                closed.set(true)
            }
        }
    }

    fun setPaused(value: Boolean) = enqueue { handle ->
        paused = value
        if (loaded) mpv_set_property_string(handle, "pause", if (value) "yes" else "no")
    }

    fun seek(value: Long) = enqueue { handle ->
        position = value
        if (loaded) mpv_set_property_string(handle, "time-pos", (value / 1000.0).toString())
    }

    fun setSpeed(value: Float) = enqueue { handle ->
        speed = value
        if (loaded) mpv_set_property_string(handle, "speed", value.toString())
    }

    fun close() { closed.set(true) }

    private fun enqueue(command: MpvNative.(Pointer) -> Unit) {
        if (!closed.get()) commands.add(command)
    }

    private fun readEvents(mpv: MpvNative, handle: Pointer) {
        var lastUpdate = visionElapsedTimeMs() - 60
        Memory(8).use { property ->
            while (!closed.get()) {
                while (true) {
                    val command = commands.poll() ?: break
                    command(mpv, handle)
                }
                val event = mpv.mpv_wait_event(handle, 0.02)
                when (event.getInt(0)) {
                    MPV_EVENT_FILE_LOADED -> {
                        if (audioAddress != null && mpv.command(handle, "audio-add", audioAddress, "select") < 0) {
                            onEnd("mpv 无法加载独立音轨", true)
                            return
                        }
                        if (position > 0) mpv.mpv_set_property_string(handle, "time-pos", (position / 1000.0).toString())
                        mpv.mpv_set_property_string(handle, "speed", speed.toString())
                        mpv.mpv_set_property_string(handle, "pause", if (paused) "yes" else "no")
                        loaded = true
                    }
                    MPV_EVENT_END_FILE -> {
                        // mpv_event's data pointer follows two 32-bit fields and reply_userdata.
                        val reason = event.getPointer(16)?.getInt(0)
                        onEnd(if (reason == MPV_END_FILE_REASON_EOF) null else "mpv 无法读取当前播放地址", true)
                        return
                    }
                    MPV_EVENT_SHUTDOWN -> {
                        onEnd("mpv 播放器已退出", false)
                        return
                    }
                }
                val now = visionElapsedTimeMs()
                if (loaded && now - lastUpdate >= 60) {
                    lastUpdate = now
                    val width = mpv.dimension(handle, "dwidth", property)
                    val height = mpv.dimension(handle, "dheight", property)
                    if (width > 0 && height > 0) size = FrameSize(width, height)
                    val buffering = mpv.mpv_get_property(handle, "paused-for-cache", MPV_FORMAT_FLAG, property) >= 0 &&
                        property.getInt(0) != 0
                    val current = mpv.milliseconds(handle, "time-pos", property) ?: position
                    val duration = mpv.milliseconds(handle, "duration", property) ?: 0
                    onUpdate(Status(current, duration, !paused && !buffering, buffering))
                }
            }
        }
    }

    private fun MpvNative.dimension(handle: Pointer, name: String, value: Memory): Int =
        if (mpv_get_property(handle, name, MPV_FORMAT_INT64, value) >= 0) value.getLong(0).toInt() else 0

    private fun MpvNative.milliseconds(handle: Pointer, name: String, value: Memory): Long? =
        if (mpv_get_property(handle, name, MPV_FORMAT_DOUBLE, value) >= 0) {
            value.getDouble(0).takeIf { it.isFinite() }?.let { (it * 1000).roundToLong().coerceAtLeast(0) }
        } else null

    private fun render(mpv: MpvNative, handle: Pointer, ready: CompletableFuture<Unit>) {
        try {
            val output = PointerByReference()
            Memory(3).use { api ->
                api.setString(0, "sw")
                mpvRenderParams(MPV_RENDER_PARAM_API_TYPE to api).use { params ->
                    check(mpv.mpv_render_context_create(output, handle, params) >= 0)
                }
            }
            val context = checkNotNull(output.value)
            try {
                ready.complete(Unit)
                var previous: FrameSize? = null
                var buffer: MpvSoftwareFrame? = null
                try {
                    while (!closed.get()) {
                        val current = size
                        if (current != null && (previous != current ||
                                mpv.mpv_render_context_update(context) and MPV_RENDER_UPDATE_FRAME != 0L)
                        ) {
                            if (previous != current) {
                                buffer?.close()
                                buffer = null
                                // The software renderer only needs a preview-sized compositing surface.
                                val scale = minOf(1.0, 1920.0 / current.width, 1920.0 / current.height)
                                buffer = MpvSoftwareFrame(
                                    (current.width * scale).roundToInt().coerceAtLeast(1),
                                    (current.height * scale).roundToInt().coerceAtLeast(1),
                                )
                                previous = current
                            }
                            checkNotNull(buffer).render(mpv, context)?.let(onFrame)
                        }
                        Thread.sleep(8)
                    }
                } finally {
                    buffer?.close()
                }
            } finally {
                mpv.mpv_render_context_free(context)
            }
        } catch (failure: Exception) {
            if (!ready.completeExceptionally(failure) && !closed.getAndSet(true)) onEnd("mpv 视频输出失败", false)
        } catch (failure: LinkageError) {
            if (!ready.completeExceptionally(failure) && !closed.getAndSet(true)) onEnd("libmpv 视频输出接口不可用", false)
        }
    }
}

private class MpvSoftwareFrame(private val width: Int, private val height: Int) : AutoCloseable {
    private val pitch = (width * 4 + 63) and 63.inv()
    private val storage = Memory((pitch * height + 63).toLong())
    private val data = storage.align(64)
    private val pixels = ByteArray(pitch * height)
    private val dimensions = Memory(8).apply { setInt(0, width); setInt(4, height) }
    private val format = Memory(5).apply { setString(0, "bgr0") }
    private val stride = Memory(Native.SIZE_T_SIZE.toLong()).apply {
        if (Native.SIZE_T_SIZE == 8) setLong(0, pitch.toLong()) else setInt(0, pitch)
    }
    private val parameters = mpvRenderParams(
        MPV_RENDER_PARAM_SW_SIZE to dimensions,
        MPV_RENDER_PARAM_SW_FORMAT to format,
        MPV_RENDER_PARAM_SW_STRIDE to stride,
        MPV_RENDER_PARAM_SW_POINTER to data,
    )

    fun render(mpv: MpvNative, context: Pointer): Image? {
        if (mpv.mpv_render_context_render(context, parameters) < 0) return null
        data.read(0, pixels, 0, pixels.size)
        return Image.makeRaster(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), pixels, pitch)
    }

    override fun close() {
        parameters.close()
        stride.close()
        dimensions.close()
        format.close()
        storage.close()
    }
}
