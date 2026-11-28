package com.qx.orbit.bili.vision

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties

internal class DesktopVisionStore(private val directory: Path, name: String) : VisionStore {
    private val file = directory.resolve("$name.properties")
    private val values = Properties().apply {
        if (Files.isRegularFile(file)) Files.newInputStream(file).use { load(it) }
    }

    override fun getString(key: String, default: String): String = values.getProperty(key, default)
    override fun getLong(key: String, default: Long): Long = values.getProperty(key)?.toLongOrNull() ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean = values.getProperty(key)?.toBooleanStrictOrNull() ?: default
    override fun getFloat(key: String, default: Float): Float = values.getProperty(key)?.toFloatOrNull() ?: default

    override fun put(vararg values: Pair<String, Any>) {
        edit { values.forEach { (key, value) -> setProperty(key, value.toString()) } }
    }

    override fun remove(key: String) { edit { remove(key) } }
    override fun clear() { edit { clear() } }

    @Synchronized
    private fun edit(update: Properties.() -> Unit) {
        val updated = Properties().apply {
            putAll(this@DesktopVisionStore.values)
            update()
        }
        val posix = directory.fileSystem.supportedFileAttributeViews().contains("posix")
        if (posix) Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
        else Files.createDirectories(directory)
        val temp = if (posix) Files.createTempFile(
            directory, file.fileName.toString(), ".tmp",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
        ) else Files.createTempFile(directory, file.fileName.toString(), ".tmp")
        try {
            Files.newOutputStream(temp).use { updated.store(it, null) }
            try {
                Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file, REPLACE_EXISTING)
            }
            values.clear()
            values.putAll(updated)
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
