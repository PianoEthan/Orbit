package com.qx.orbit.bili.vision

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class VisionFailoverReaderTest {
    private data class Source(val index: Int, var offset: Int = 0)
    private val content = "0123456789".toByteArray()

    private class Fixture(
        private val content: ByteArray,
        private val openFailures: Set<Int> = emptySet(),
        private val readFailures: Map<Int, Int> = emptyMap(),
        private val truncated: Set<Int> = emptySet(),
    ) {
        val opened = mutableListOf<Pair<Int, Long>>()
        val closed = mutableListOf<Int>()
        val reader = VisionFailoverReader(
            candidateCount = 3,
            create = { Source(it) },
            openSource = { source, offset ->
                opened += source.index to offset
                if (source.index in openFailures) throw IOException("HTTP 403")
                source.offset = offset.toInt()
                (content.size - offset)
            },
            readSource = { source, buffer, offset, length ->
                val failureAt = readFailures[source.index]
                if (failureAt != null && source.offset >= failureAt) {
                    if (source.index in truncated) -1 else throw IOException("connection lost")
                } else {
                    val count = minOf(length, content.size - source.offset, (failureAt ?: content.size) - source.offset)
                    if (count == 0) -1 else {
                        content.copyInto(buffer, offset, source.offset, source.offset + count)
                        source.offset += count
                        count
                    }
                }
            },
            closeSource = { closed += it.index },
        )
    }

    private fun readAll(reader: VisionFailoverReader<Source>): ByteArray {
        val chunks = mutableListOf<Byte>()
        val buffer = ByteArray(3)
        while (true) {
            val count = reader.read(buffer, 0, buffer.size)
            if (count < 0) break
            chunks += buffer.take(count)
        }
        return chunks.toByteArray()
    }

    @Test fun failedPrimaryStartsWithFirstBackupAndClosesFailedConnection() {
        val fixture = Fixture(content, openFailures = setOf(0))
        assertEquals(10L, fixture.reader.open())
        assertArrayEquals(content, readAll(fixture.reader))
        assertEquals(listOf(0 to 0L, 1 to 0L), fixture.opened)
        assertEquals(listOf(0), fixture.closed)
        fixture.reader.close()
        assertEquals(listOf(0, 1), fixture.closed)
    }

    @Test fun interruptedReadResumesExactByteWithoutDuplicateOrMissingContent() {
        val fixture = Fixture(content, readFailures = mapOf(0 to 4))
        fixture.reader.open()
        assertArrayEquals(content, readAll(fixture.reader))
        assertEquals(listOf(0 to 0L, 1 to 4L), fixture.opened)
        fixture.reader.close()
    }

    @Test fun failedBackupDoesNotResetOffsetBeforeNextBackup() {
        val fixture = Fixture(content, openFailures = setOf(1), readFailures = mapOf(0 to 4))
        fixture.reader.open()
        assertArrayEquals(content, readAll(fixture.reader))
        assertEquals(listOf(0 to 0L, 1 to 4L, 2 to 4L), fixture.opened)
        fixture.reader.close()
    }

    @Test fun prematureEndOfKnownLengthMovesToNextSource() {
        val fixture = Fixture(content, readFailures = mapOf(0 to 4), truncated = setOf(0))
        fixture.reader.open()
        assertArrayEquals(content, readAll(fixture.reader))
        assertEquals(listOf(0 to 0L, 1 to 4L), fixture.opened)
        fixture.reader.close()
    }

    @Test fun exhaustedAddressesFailOnceWithoutLoopingAndCloseAllSources() {
        val fixture = Fixture(content, openFailures = setOf(0, 1, 2))
        try { fixture.reader.open(); fail("Expected source failure") } catch (_: IOException) { }
        assertEquals(listOf(0 to 0L, 1 to 0L, 2 to 0L), fixture.opened)
        assertEquals(listOf(0, 1, 2), fixture.closed)
        assertNull(fixture.reader.activeSource)
    }

    @Test fun allSourcesFailingDuringReadCloseBeforeThrowing() {
        val fixture = Fixture(content, readFailures = mapOf(0 to 4, 1 to 4, 2 to 4))
        fixture.reader.open()
        try { readAll(fixture.reader); fail("Expected read failure") } catch (_: IOException) { }
        assertEquals(listOf(0 to 0L, 1 to 4L, 2 to 4L), fixture.opened)
        assertEquals(listOf(0, 1, 2), fixture.closed)
        assertNull(fixture.reader.activeSource)
    }

    @Test fun seekReopenKeepsWorkingCdnButResetsReadPosition() {
        val fixture = Fixture(content, openFailures = setOf(0))
        fixture.reader.open()
        assertArrayEquals(content, readAll(fixture.reader))
        fixture.reader.open()
        assertArrayEquals(content, readAll(fixture.reader))
        assertEquals(listOf(0 to 0L, 1 to 0L, 1 to 0L), fixture.opened)
        fixture.reader.close()
    }

    @Test fun zeroLengthReadDoesNotCauseAnyMediaRead() {
        val fixture = Fixture(content)
        fixture.reader.open()
        assertEquals(0, fixture.reader.read(ByteArray(1), 0, 0))
        assertArrayEquals(content, readAll(fixture.reader))
        fixture.reader.close()
    }
}
