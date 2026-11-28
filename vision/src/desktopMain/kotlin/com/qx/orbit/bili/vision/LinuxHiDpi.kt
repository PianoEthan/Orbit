package com.qx.orbit.bili.vision

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private const val BASE_DPI = 96f

/** Prefer compositor configuration on Wayland, then fall back to the X resource DPI. */
internal fun linuxDesktopScale(): Float? {
    if (!Platform.isLinux() || System.getProperty("sun.java2d.uiScale.enabled") == "false") return null

    val override = System.getProperty("sun.java2d.uiScale")
    if (override != null) return parseUiScale(override)

    val isWayland = System.getenv("XDG_SESSION_TYPE") == "wayland" || !System.getenv("WAYLAND_DISPLAY").isNullOrBlank()
    if (isWayland) {
        val output = runCatching { readWaylandOutput(System.getenv("XDG_CURRENT_DESKTOP").orEmpty()) }.getOrNull()
        if (output != null) {
            if (output.alreadyScaled(readXScreenSize())) return 1f
            return output.scale
        }
    }
    return readXftScale() ?: parseUiScale(System.getenv("GDK_SCALE").orEmpty())
}

private fun parseUiScale(value: String): Float? {
    val text = value.trim()
    val scale = when {
        text.endsWith("%") -> text.dropLast(1).toFloatOrNull()?.div(100f)
        text.endsWith("dpi") -> text.dropLast(3).toFloatOrNull()?.div(BASE_DPI)
        else -> text.removeSuffix("x").toFloatOrNull()
    }
    return scale?.takeIf { it.isFinite() && it >= 1f }
}

private data class ScreenSize(val width: Int, val height: Int)

private data class WaylandOutput(val scale: Float, val onlyOutputMode: ScreenSize?) {
    fun alreadyScaled(xScreen: ScreenSize?): Boolean {
        val mode = onlyOutputMode ?: return false
        val screen = xScreen ?: return false
        if (scale <= 1f) return false
        val logicalWidth = mode.width / scale
        val logicalHeight = mode.height / scale
        return (abs(screen.width - logicalWidth) <= 2 && abs(screen.height - logicalHeight) <= 2) ||
            (abs(screen.width - logicalHeight) <= 2 && abs(screen.height - logicalWidth) <= 2)
    }
}

private fun readWaylandOutput(desktop: String): WaylandOutput? {
    val names = desktop.split(':').map { it.uppercase() }
    return when {
        "KDE" in names || "PLASMA" in names -> commandJson("kscreen-doctor", "-j")
            ?.let(::parseKScreenOutput)
        "GNOME" in names -> commandJson(
            "busctl", "--user", "--json=short", "call",
            "org.gnome.Mutter.DisplayConfig", "/org/gnome/Mutter/DisplayConfig",
            "org.gnome.Mutter.DisplayConfig", "GetCurrentState",
        )?.let(::parseMutterOutput)
        "HYPRLAND" in names -> commandJson("hyprctl", "-j", "monitors")?.let(::parseHyprlandOutput)
        else -> commandJson("wlr-randr", "--json")?.let(::parseWlrootsOutput)
    }
}

private fun parseKScreenOutput(json: JsonElement): WaylandOutput? {
    val outputs = json.objectOrNull()?.get("outputs").asObjects()
        .filter { it.get("enabled").flag() && it.get("connected").flag() }
    val output = outputs.minByOrNull { it.get("priority")?.let { priority -> priority.asInt }?.takeIf { it > 0 } ?: Int.MAX_VALUE }
        ?: return null
    val currentId = output.get("currentModeId")?.asString
    val size = if (outputs.size == 1) {
        output.get("modes").asObjects().firstOrNull { it.get("id")?.asString == currentId }
            ?.get("size").asScreenSize()
    } else null
    return output.get("scale").asScale()?.let { WaylandOutput(it, size) }
}

private fun parseMutterOutput(json: JsonElement): WaylandOutput? {
    val data = json.objectOrNull()?.get("data").arrayOrNull() ?: return null
    val logical = data.getOrNull(2).arrayOrNull()?.mapNotNull { it.arrayOrNull() }.orEmpty()
    val output = logical.firstOrNull { it.getOrNull(4).flag() } ?: logical.firstOrNull() ?: return null
    val connector = output.getOrNull(5).arrayOrNull()?.getOrNull(0).arrayOrNull()?.getOrNull(0)?.asString
    val size = if (logical.size == 1 && connector != null) {
        val monitor = data.getOrNull(1).arrayOrNull()?.mapNotNull { it.arrayOrNull() }
            ?.firstOrNull { it.getOrNull(0).arrayOrNull()?.getOrNull(0)?.asString == connector }
        val mode = monitor?.getOrNull(1).arrayOrNull()?.mapNotNull { it.arrayOrNull() }
            ?.firstOrNull {
                val data = it.getOrNull(6).objectOrNull()?.get("is-current").objectOrNull()?.get("data")
                data.flag() || data.arrayOrNull()?.getOrNull(0).flag()
            }
        if (mode != null) ScreenSize(mode[1].asInt, mode[2].asInt) else null
    } else null
    return output.getOrNull(2).asScale()?.let { WaylandOutput(it, size) }
}

private fun parseWlrootsOutput(json: JsonElement): WaylandOutput? {
    val outputs = json.asObjects().filter { it.get("enabled").flag() }
    val output = outputs.firstOrNull { it.get("position").objectOrNull()?.get("x")?.asInt == 0 } ?: outputs.firstOrNull()
        ?: return null
    val size = if (outputs.size == 1) {
        output.get("modes").asObjects().firstOrNull { it.get("current").flag() }.asScreenSize()
    } else null
    return output.get("scale").asScale()?.let { WaylandOutput(it, size) }
}

private fun parseHyprlandOutput(json: JsonElement): WaylandOutput? {
    val outputs = json.asObjects().filterNot { it.get("disabled").flag() }
    val output = outputs.firstOrNull { it.get("focused").flag() } ?: outputs.firstOrNull() ?: return null
    return output.get("scale").asScale()?.let { WaylandOutput(it, if (outputs.size == 1) output.asScreenSize() else null) }
}

private fun commandJson(vararg command: String): JsonElement? {
    val process = try {
        ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    } catch (_: IOException) {
        return null
    }
    val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().use { it.readText() } }
    return try {
        if (!process.waitFor(900, TimeUnit.MILLISECONDS) || process.exitValue() != 0) null
        else JsonParser.parseString(output.get(100, TimeUnit.MILLISECONDS))
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    } catch (_: Exception) {
        null
    } finally {
        if (process.isAlive) process.destroyForcibly()
    }
}

private fun JsonElement?.objectOrNull(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject
private fun JsonElement?.arrayOrNull(): JsonArray? = this?.takeIf { it.isJsonArray }?.asJsonArray
private fun JsonElement?.asObjects(): List<JsonObject> = arrayOrNull()?.mapNotNull { it.objectOrNull() }.orEmpty()
private fun JsonElement?.flag(): Boolean = this?.isJsonPrimitive == true && this.asBoolean
private fun JsonArray.getOrNull(index: Int): JsonElement? = if (index < size()) get(index) else null
private fun JsonElement?.asScale(): Float? = this?.takeIf { it.isJsonPrimitive }?.asString?.let(::parseUiScale)
private fun JsonElement?.asScreenSize(): ScreenSize? {
    val obj = objectOrNull() ?: return null
    val width = obj.get("width")?.asInt ?: return null
    val height = obj.get("height")?.asInt ?: return null
    return ScreenSize(width, height).takeIf { width > 0 && height > 0 }
}

private fun readXScreenSize(): ScreenSize? = withXDisplay { x11, display ->
    val screen = x11.XDefaultScreen(display)
    ScreenSize(x11.XDisplayWidth(display, screen), x11.XDisplayHeight(display, screen))
}

private fun readXftScale(): Float? = withXDisplay { x11, display ->
    x11.XResourceManagerString(display)?.getString(0)
        ?.lineSequence()
        ?.map { it.substringBefore(':').trim() to it.substringAfter(':', "").trim() }
        ?.firstOrNull { (name, _) -> name.equals("Xft.dpi", ignoreCase = true) }
        ?.second
        ?.let { parseUiScale("${it}dpi") }
}

private fun <T> withXDisplay(read: (X11ResourceDatabase, Pointer) -> T?): T? {
    if (System.getenv("DISPLAY").isNullOrBlank()) return null
    return try {
        val x11 = Native.load("X11", X11ResourceDatabase::class.java)
        val display = x11.XOpenDisplay(null) ?: return null
        try {
            read(x11, display)
        } finally {
            x11.XCloseDisplay(display)
        }
    } catch (_: LinkageError) {
        null
    }
}

internal interface X11ResourceDatabase : Library {
    fun XOpenDisplay(name: String?): Pointer?
    fun XResourceManagerString(display: Pointer): Pointer?
    fun XDefaultScreen(display: Pointer): Int
    fun XDisplayWidth(display: Pointer, screen: Int): Int
    fun XDisplayHeight(display: Pointer, screen: Int): Int
    fun XCloseDisplay(display: Pointer): Int
}
