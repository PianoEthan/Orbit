package com.qx.orbit.bili.vision

import java.io.IOException

internal class VisionFailoverReader<S>(
    private val candidateCount: Int,
    private val create: (Int) -> S,
    private val openSource: (S, Long) -> Long,
    private val readSource: (S, ByteArray, Int, Int) -> Int,
    private val closeSource: (S) -> Unit,
) {
    var activeSource: S? = null
        private set
    private var index = 0
    private var bytesRead = 0L
    private var expectedLength = -1L
    private var opened = false

    init { require(candidateCount > 0) }

    fun open(): Long {
        close()
        bytesRead = 0
        expectedLength = -1
        val length = openCandidate()
        expectedLength = length
        opened = true
        return length
    }

    private fun openCandidate(): Long {
        while (true) {
            val source = create(index)
            activeSource = source
            try {
                return openSource(source, bytesRead)
            } catch (error: IOException) {
                closeActiveQuietly()
                if (index + 1 >= candidateCount) throw error
                index++
            }
        }
    }

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        check(opened) { "Source is not open" }
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
        if (length == 0) return 0
        if (expectedLength >= 0 && bytesRead >= expectedLength) return -1
        val limit = if (expectedLength >= 0) minOf(length.toLong(), expectedLength - bytesRead).toInt() else length
        while (true) {
            try {
                val count = readSource(checkNotNull(activeSource), buffer, offset, limit)
                if (count < 0 && expectedLength >= 0 && bytesRead < expectedLength) throw IOException("Unexpected end of media stream")
                if (count > 0) bytesRead += count
                return count
            } catch (error: IOException) {
                closeActiveQuietly()
                if (index + 1 >= candidateCount) {
                    opened = false
                    throw error
                }
                index++
                try {
                    openCandidate()
                } catch (failure: IOException) {
                    opened = false
                    throw failure
                }
            }
        }
    }

    private fun closeActiveQuietly() {
        try { activeSource?.let(closeSource) } catch (_: IOException) { } finally { activeSource = null }
    }

    fun close() {
        opened = false
        try { activeSource?.let(closeSource) } finally { activeSource = null }
    }
}
