package com.qx.orbit.bili.vision

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import com.sun.jna.ptr.PointerByReference
import java.io.File

internal interface MpvNative : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_terminate_destroy(handle: Pointer)
    fun mpv_set_option_string(handle: Pointer, name: String, value: String): Int
    fun mpv_set_property_string(handle: Pointer, name: String, value: String): Int
    fun mpv_get_property(handle: Pointer, name: String, format: Int, value: Pointer): Int
    fun mpv_command(handle: Pointer, args: StringArray): Int
    fun mpv_wait_event(handle: Pointer, timeout: Double): Pointer
    fun mpv_render_context_create(result: PointerByReference, handle: Pointer, params: Pointer): Int
    fun mpv_render_context_update(context: Pointer): Long
    fun mpv_render_context_render(context: Pointer, params: Pointer): Int
    fun mpv_render_context_free(context: Pointer)

    companion object {
        private val bundledLibraryNames = when {
            Platform.isWindows() -> listOf("mpv-2.dll", "libmpv-2.dll", "mpv.dll")
            Platform.isMac() -> listOf("libmpv.2.dylib", "libmpv.dylib")
            else -> listOf("libmpv.so.2", "libmpv.so")
        }

        val instance: MpvNative by lazy {
            val options = mapOf(Library.OPTION_STRING_ENCODING to "UTF-8")
            val bundle = System.getProperty("compose.application.resources.dir")?.let { File(it, "mpv") }
            if (bundle?.isDirectory == true) {
                val library = bundledLibraryNames.map(bundle::resolve).firstOrNull(File::isFile)
                    ?: throw UnsatisfiedLinkError("Bundled libmpv is missing")
                return@lazy Native.load(library.absolutePath, MpvNative::class.java, options)
            }
            val names = when {
                Platform.isWindows() -> listOf("mpv-2", "libmpv-2", "mpv")
                Platform.isMac() -> listOf("mpv", "/opt/homebrew/lib/libmpv.2.dylib", "/usr/local/lib/libmpv.2.dylib")
                else -> listOf("mpv", "libmpv.so.2")
            }
            for (name in names) {
                try {
                    return@lazy Native.load(name, MpvNative::class.java, options)
                } catch (_: UnsatisfiedLinkError) {
                    // Try the platform's other common soname.
                }
            }
            throw UnsatisfiedLinkError("libmpv")
        }
    }
}

internal const val MPV_FORMAT_FLAG = 3
internal const val MPV_FORMAT_INT64 = 4
internal const val MPV_FORMAT_DOUBLE = 5
internal const val MPV_EVENT_SHUTDOWN = 1
internal const val MPV_EVENT_END_FILE = 7
internal const val MPV_EVENT_FILE_LOADED = 8
internal const val MPV_END_FILE_REASON_EOF = 0
internal const val MPV_RENDER_PARAM_API_TYPE = 1
internal const val MPV_RENDER_PARAM_SW_SIZE = 17
internal const val MPV_RENDER_PARAM_SW_FORMAT = 18
internal const val MPV_RENDER_PARAM_SW_STRIDE = 19
internal const val MPV_RENDER_PARAM_SW_POINTER = 20
internal const val MPV_RENDER_UPDATE_FRAME = 1L

// The native array ends with { MPV_RENDER_PARAM_INVALID, NULL }.
internal fun mpvRenderParams(vararg params: Pair<Int, Pointer>): Memory {
    val stride = (Native.POINTER_SIZE * 2).toLong()
    return Memory(stride * (params.size + 1)).apply {
        clear()
        params.forEachIndexed { i, (type, data) ->
            setInt(i * stride, type)
            setPointer(i * stride + Native.POINTER_SIZE, data)
        }
    }
}

internal fun MpvNative.command(handle: Pointer, vararg args: String): Int =
    mpv_command(handle, StringArray(args.toList().toTypedArray(), "UTF-8"))
