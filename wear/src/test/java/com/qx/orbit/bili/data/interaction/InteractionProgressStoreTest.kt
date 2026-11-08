package com.qx.orbit.bili.data.interaction

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qx.orbit.bili.data.interaction.InteractionProgressStore.Key
import com.qx.orbit.bili.data.interaction.InteractionProgressStore.SaveResult
import com.qx.orbit.bili.data.interaction.InteractionProgressStore.Snapshot
import com.qx.orbit.bili.data.model.InteractionChoice
import com.qx.orbit.bili.data.model.InteractionEdge
import com.qx.orbit.bili.data.model.InteractionQuestion
import com.qx.orbit.bili.data.model.InteractionVideo
import com.qx.orbit.bili.util.interaction.InteractionSession
import com.qx.orbit.bili.util.interaction.InteractionSession.Checkpoint
import com.qx.orbit.bili.util.interaction.InteractionSession.RootIdentity
import com.qx.orbit.bili.util.interaction.InteractionSession.Visit
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InteractionProgressStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val key = Key(accountKey = 0, graphVersion = 7, aid = 100)

    @Test
    fun roundTripPreservesPositionRootHistoryDoublesAndPolicyAcrossStoreRecreation() = runBlocking {
        val directory = temporary.newFolder()
        val store = InteractionProgressStore(directory)
        val expected = snapshot().copy(noBacktracking = true)
        assertEquals(SaveResult.SAVED, store.save(store.begin(key), 19, expected, true))
        val restored = InteractionProgressStore(directory).read(key)!!
        assertEquals(InteractionProgressStore.SCHEMA_VERSION, restored.schemaVersion)
        assertEquals(key, restored.key)
        assertEquals(expected.checkpoint, restored.checkpoint)
        assertEquals(expected.positionMs, restored.positionMs)
        assertTrue(restored.noBacktracking)
        assertEquals(19L, restored.revision)
        assertTrue(restored.updatedAt >= 0)
        assertEquals(36, restored.sessionId.length)

        val session = InteractionSession()
        val prepared = session.load(
            7, InteractionVideo(edgeId = 2), 20, restored.checkpoint, InteractionVideo(edgeId = 1)
        ) as InteractionSession.PrepareMedia
        assertTrue(session.mediaPrepared(prepared.token) is InteractionSession.Committed)
        assertEquals(restored.checkpoint.root, session.checkpoint()!!.root)
        assertEquals(restored.checkpoint.variables, session.state.variables)
    }

    @Test
    fun committedCheckpointDoesNotPersistPendingTargetOrCandidateVariables() = runBlocking {
        val directory = temporary.newFolder()
        val store = InteractionProgressStore(directory)
        val session = InteractionSession()
        val node = InteractionVideo(edgeId = 1, edges = InteractionEdge(questions = listOf(
            InteractionQuestion(type = 1, choices = listOf(InteractionChoice(id = 2, cid = 20, nativeAction = "x=99")))
        )))
        val initial = session.load(7, node, 10) as InteractionSession.PrepareMedia
        assertNull(session.checkpoint())
        session.mediaPrepared(initial.token)
        session.observePlayback(10_000, 10_000, true, 0, true)
        assertTrue(session.select(0, 0) is InteractionSession.Target)
        assertEquals(99.0, session.state.transition!!.candidateVariables["x"])
        val checkpoint = session.checkpoint()!!
        assertEquals(1L, checkpoint.edgeId)
        assertTrue(checkpoint.variables.isEmpty())
        assertEquals(SaveResult.SAVED, store.save(store.begin(key), 1, Snapshot(checkpoint, 10_000), true))
        val restored = store.read(key)!!
        assertEquals(checkpoint, restored.checkpoint)
        val json = dataFiles(directory).single().readText()
        assertFalse(json.contains("transition"))
        assertFalse(json.contains("candidateVariables"))
    }

    @Test
    fun guestAccountsArticlesBvidAndGraphsAreIndependentAndNeverFallBack() = runBlocking {
        val store = InteractionProgressStore(temporary.newFolder())
        val keys = listOf(
            key, key.copy(accountKey = 1), key.copy(accountKey = 2), key.copy(aid = 101),
            key.copy(graphVersion = 8), key.copy(aid = null, bvid = "BV1xx411c7mD")
        )
        for ((index, item) in keys.withIndex()) {
            val snapshot = snapshot(graphVersion = item.graphVersion).copy(positionMs = index.toLong())
            assertEquals(SaveResult.SAVED, store.save(store.begin(item), 1, snapshot, true))
        }
        for ((index, item) in keys.withIndex()) assertEquals(index.toLong(), store.read(item)!!.positionMs)
        assertNull(store.read(key.copy(graphVersion = 9)))
        assertNull(store.read(key.copy(accountKey = 3)))
        assertNull(store.read(key.copy(aid = 102)))
    }

    @Test
    fun recordingDisabledErasesOnlyThisKeyEvenWhenSnapshotIsInvalid() = runBlocking {
        val directory = temporary.newFolder()
        val store = InteractionProgressStore(directory)
        val writer = store.begin(key)
        val other = key.copy(accountKey = 1)
        assertEquals(SaveResult.SAVED, store.save(writer, 1, snapshot(), true))
        assertEquals(SaveResult.SAVED, store.save(store.begin(other), 1, snapshot(), true))
        assertEquals(SaveResult.RECORDING_DISABLED, store.save(writer, 2, snapshot().copy(positionMs = -1), false))
        assertNull(InteractionProgressStore(directory).read(key))
        assertEquals(snapshot().checkpoint, store.read(other)!!.checkpoint)
        assertEquals(1, dataFiles(directory).size)
        assertEquals(SaveResult.STALE_REVISION, store.save(writer, 1, snapshot(), true))
    }

    @Test
    fun disabledRecordingCreatesNoDirectoryAndRemovesUnreadableOldPayload() = runBlocking {
        val parent = temporary.newFolder()
        val directory = File(parent, "progress")
        val store = InteractionProgressStore(directory)
        val writer = store.begin(key)
        assertEquals(SaveResult.RECORDING_DISABLED, store.save(writer, 1, snapshot(), false))
        assertFalse(directory.exists())
        assertEquals(SaveResult.SAVED, store.save(writer, 2, snapshot(), true))
        dataFiles(directory).single().writeText("{broken")
        assertEquals(SaveResult.RECORDING_DISABLED, store.save(writer, 3, snapshot(), false))
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun staleWritersCannotOverwriteEraseOrInvalidateANewerSession() = runBlocking {
        val store = InteractionProgressStore(temporary.newFolder())
        val oldWriter = store.begin(key)
        assertEquals(SaveResult.SAVED, store.save(oldWriter, 50, snapshot(), true))
        val oldSessionId = store.read(key)!!.sessionId
        val gate = CompletableDeferred<Unit>()
        val lateSave = async(Dispatchers.Default) {
            gate.await()
            store.save(oldWriter, 100, snapshot().copy(positionMs = 999), true)
        }
        val currentWriter = store.begin(key)
        assertEquals(SaveResult.SAVED, store.save(currentWriter, 1, snapshot().copy(positionMs = 100), true))
        gate.complete(Unit)
        assertEquals(SaveResult.STALE_WRITER, lateSave.await())
        assertEquals(SaveResult.STALE_WRITER, store.save(oldWriter, 101, snapshot(), false))
        assertFalse(store.delete(oldWriter))
        assertFalse(store.invalidate(oldWriter))
        val current = store.read(key)!!
        assertEquals(100L, current.positionMs)
        assertEquals(1L, current.revision)
        assertNotEquals(oldSessionId, current.sessionId)
        assertTrue(store.invalidate(currentWriter))
        assertEquals(SaveResult.STALE_WRITER, store.save(currentWriter, 2, snapshot(), true))
        assertEquals(100L, store.read(key)!!.positionMs)
    }

    @Test
    fun outOfOrderAndConcurrentRevisionsAlwaysRetainHighestCapturedRevision() = runBlocking {
        val store = InteractionProgressStore(temporary.newFolder())
        val writer = store.begin(key)
        val gate = CompletableDeferred<Unit>()
        val delayed = async(Dispatchers.Default) {
            gate.await()
            store.save(writer, 1, snapshot().copy(positionMs = 1), true)
        }
        assertEquals(SaveResult.SAVED, store.save(writer, 2, snapshot().copy(positionMs = 2), true))
        gate.complete(Unit)
        assertEquals(SaveResult.STALE_REVISION, delayed.await())
        assertEquals(SaveResult.STALE_REVISION, store.save(writer, 2, snapshot(), true))
        assertEquals(SaveResult.STALE_REVISION, store.save(writer, 0, snapshot(), true))
        assertEquals(SaveResult.STALE_REVISION, store.save(writer, -1, snapshot(), true))
        coroutineScope {
            (3L..40L).map { revision ->
                async(Dispatchers.Default) { store.save(writer, revision, snapshot().copy(positionMs = revision), true) }
            }.awaitAll().forEach { assertTrue(it == SaveResult.SAVED || it == SaveResult.STALE_REVISION) }
        }
        assertEquals(40L, store.read(key)!!.revision)
        assertEquals(40L, store.read(key)!!.positionMs)
    }

    @Test
    fun deleteRevokesQueuedSavesAndLeavesOtherAccountsUntouched() = runBlocking {
        val store = InteractionProgressStore(temporary.newFolder())
        val writer = store.begin(key)
        val otherKey = key.copy(accountKey = 1)
        val otherWriter = store.begin(otherKey)
        store.save(writer, 1, snapshot(), true)
        store.save(otherWriter, 1, snapshot(), true)
        assertTrue(store.delete(writer))
        assertEquals(SaveResult.STALE_WRITER, store.save(writer, 2, snapshot(), true))
        assertFalse(store.delete(writer))
        assertNull(store.read(key))
        assertEquals(SaveResult.SAVED, store.save(otherWriter, 2, snapshot(), true))
        assertTrue(store.read(otherKey) != null)
    }

    @Test
    fun invalidKeysIncludingPathTraversalAreRejectedWithoutCreatingFiles() = runBlocking {
        val directory = temporary.newFolder()
        val store = InteractionProgressStore(directory)
        for (invalid in listOf(
            key.copy(accountKey = -1), key.copy(graphVersion = 0), key.copy(aid = 0),
            key.copy(aid = null), key.copy(bvid = "BV1xx411c7mD"),
            key.copy(aid = null, bvid = "../../escape"), key.copy(aid = null, bvid = "")
        )) {
            assertThrows(IllegalArgumentException::class.java) { store.begin(invalid) }
            assertNull(store.read(invalid))
        }
        assertTrue(directory.listFiles()!!.isEmpty())
        assertEquals(SaveResult.SAVED, store.save(store.begin(key), 1, snapshot(), true))
        assertTrue(dataFiles(directory).all { it.name.matches(Regex("progress-[0-9a-f]{2}\\.json")) })
    }

    @Test
    fun invalidSnapshotBoundsDoNotReplacePreviousGoodRecord() = runBlocking {
        val store = InteractionProgressStore(temporary.newFolder())
        val writer = store.begin(key)
        val good = snapshot()
        assertEquals(SaveResult.SAVED, store.save(writer, 1, good, true))
        val cp = good.checkpoint
        val visit = cp.visits.last()
        val badCheckpoints = listOf(
            cp.copy(graphVersion = 8), cp.copy(edgeId = 0), cp.copy(cid = -1),
            cp.copy(root = RootIdentity(0, 10)), cp.copy(root = RootIdentity(1, 0)),
            cp.copy(variables = mapOf("" to 1.0)), cp.copy(variables = mapOf(" " to 1.0)),
            cp.copy(variables = mapOf("x".repeat(InteractionSession.MAX_VARIABLE_NAME + 1) to 1.0)),
            cp.copy(variables = (0..InteractionSession.MAX_VARIABLES).associate { "v$it" to 1.0 }),
            cp.copy(variables = mapOf("x" to Double.NaN)), cp.copy(variables = mapOf("x" to Double.POSITIVE_INFINITY)),
            cp.copy(variables = mapOf("x" to Double.NEGATIVE_INFINITY)),
            cp.copy(visits = List(InteractionSession.MAX_VISITS + 1) { visit.copy(visitId = it + 1L) }),
            cp.copy(visits = listOf(visit.copy(visitId = 0))),
            cp.copy(visits = listOf(visit.copy(visitId = Long.MAX_VALUE))),
            cp.copy(visits = listOf(visit.copy(edgeId = 0))), cp.copy(visits = listOf(visit.copy(cid = -1))),
            cp.copy(visits = listOf(visit.copy(variables = mapOf("x" to Double.NaN)))),
            cp.copy(visits = listOf(visit, visit)),
            cp.copy(visits = listOf(visit.copy(visitId = 4), visit.copy(visitId = 3))),
            cp.copy(visits = listOf(visit.copy(edgeId = 3)))
        )
        val invalid = badCheckpoints.map { good.copy(checkpoint = it) } + listOf(
            good.copy(positionMs = -1), good.copy(positionMs = InteractionProgressStore.MAX_POSITION_MS + 1)
        )
        for ((index, snapshot) in invalid.withIndex()) {
            assertEquals(SaveResult.INVALID_SNAPSHOT, store.save(writer, index + 2L, snapshot, true))
            assertEquals(good.checkpoint, store.read(key)!!.checkpoint)
            assertEquals(1L, store.read(key)!!.revision)
        }
    }

    @Test
    fun exactBoundsAndFiniteDoubleExtremesRoundTrip() = runBlocking {
        val store = InteractionProgressStore(temporary.newFolder())
        val writer = store.begin(key)
        val variables = (0 until InteractionSession.MAX_VARIABLES).associate { "v$it" to it.toDouble() }.toMutableMap()
        variables.remove("v0")
        variables["x".repeat(InteractionSession.MAX_VARIABLE_NAME)] = Double.MAX_VALUE
        variables["v1"] = Double.MIN_VALUE
        val cp = snapshot().checkpoint.copy(
            variables = variables,
            visits = List(InteractionSession.MAX_VISITS) { Visit(it + 1L, 2, 20, emptyMap()) }
        )
        assertEquals(SaveResult.SAVED, store.save(writer, 1, Snapshot(cp, InteractionProgressStore.MAX_POSITION_MS), true))
        assertEquals(cp, store.read(key)!!.checkpoint)
        val highVisit = cp.copy(visits = listOf(Visit(InteractionSession.MAX_VISIT_ID, 2, 20, emptyMap())))
        assertEquals(SaveResult.SAVED, store.save(writer, Long.MAX_VALUE, Snapshot(highVisit, 0), true))
        assertEquals(highVisit, store.read(key)!!.checkpoint)
        assertEquals(SaveResult.STALE_REVISION, store.save(writer, Long.MAX_VALUE, Snapshot(highVisit, 0), true))
    }

    @Test
    fun corruptIncompatibleOversizeAndDeeplyNestedFilesAreIgnored() = runBlocking {
        val directory = temporary.newFolder()
        val store = InteractionProgressStore(directory)
        store.save(store.begin(key), 1, snapshot(), true)
        val file = dataFiles(directory).single()
        val valid = file.readText()
        for (raw in listOf(
            "{", "null", "[]", valid + "garbage", valid + "/*comment*/",
            "[".repeat(1000) + "]".repeat(1000), " ".repeat(InteractionProgressStore.MAX_FILE_BYTES + 1)
        )) {
            file.writeText(raw)
            assertNull(InteractionProgressStore(directory).read(key))
        }
        val mutations: List<(JsonObject) -> Unit> = listOf(
            { it.addProperty("schemaVersion", 2) }, { it.remove("schemaVersion") },
            { it.addProperty("schemaVersion", "1") }, { it.addProperty("positionMs", -1) },
            { it.addProperty("positionMs", InteractionProgressStore.MAX_POSITION_MS + 1) },
            { it.addProperty("positionMs", 1.5) }, { it.addProperty("positionMs", "1") },
            { it.remove("positionMs") }, { it.addProperty("revision", 0) }, { it.addProperty("updatedAt", -1) },
            { it.addProperty("sessionId", "not-a-session") }, { it.remove("noBacktracking") },
            { it.add("noBacktracking", JsonNull.INSTANCE) },
            { it.getAsJsonObject("key").addProperty("accountKey", -1) },
            { it.getAsJsonObject("key").remove("accountKey") },
            { it.getAsJsonObject("checkpoint").addProperty("graphVersion", 8) },
            { it.getAsJsonObject("checkpoint").addProperty("cid", 0) },
            { it.getAsJsonObject("checkpoint").add("root", JsonNull.INSTANCE) },
            { it.getAsJsonObject("checkpoint").getAsJsonObject("root").addProperty("edgeId", 0) },
            { it.getAsJsonObject("checkpoint").add("variables", JsonNull.INSTANCE) },
            { it.getAsJsonObject("checkpoint").getAsJsonObject("variables").add("x", JsonNull.INSTANCE) },
            { it.getAsJsonObject("checkpoint").getAsJsonObject("variables").addProperty("x", "NaN") },
            { it.getAsJsonObject("checkpoint").add("visits", JsonNull.INSTANCE) },
            { it.getAsJsonObject("checkpoint").getAsJsonArray("visits")[0].asJsonObject.remove("visitId") },
            { it.getAsJsonObject("checkpoint").getAsJsonArray("visits")[0].asJsonObject.addProperty("visitId", -1) },
            { it.getAsJsonObject("checkpoint").getAsJsonArray("visits")[0].asJsonObject.addProperty("visitId", 3) },
            { it.getAsJsonObject("checkpoint").getAsJsonArray("visits")[1].asJsonObject.addProperty("edgeId", 3) },
            { it.getAsJsonObject("checkpoint").getAsJsonArray("visits")[0].asJsonObject.addProperty("cid", 0) },
            { it.getAsJsonObject("checkpoint").getAsJsonObject("variables").addProperty("x".repeat(129), 1) },
            { obj ->
                val variables = obj.getAsJsonObject("checkpoint").getAsJsonObject("variables")
                repeat(InteractionSession.MAX_VARIABLES + 1) { variables.addProperty("v$it", it) }
            },
            { obj ->
                val visits = obj.getAsJsonObject("checkpoint").getAsJsonArray("visits")
                repeat(InteractionSession.MAX_VISITS) { visits.add(visits[0].deepCopy()) }
            }
        )
        for (mutate in mutations) {
            val obj = JsonParser.parseString(valid).asJsonObject
            mutate(obj)
            file.writeText(obj.toString())
            assertNull(InteractionProgressStore(directory).read(key))
        }
        file.writeText(valid)
        assertEquals(snapshot().checkpoint, store.read(key)!!.checkpoint)
    }

    @Test
    fun oversizedValidSnapshotDoesNotDestroyPreviousRecord() = runBlocking {
        val directory = temporary.newFolder()
        val store = InteractionProgressStore(directory)
        val writer = store.begin(key)
        store.save(writer, 1, snapshot(), true)
        val variables = (0 until 128).associate { "v$it".padEnd(100, 'x') to it.toDouble() }
        val large = snapshot().checkpoint.copy(
            visits = List(32) { Visit(it + 1L, 2, 20, variables) }
        )
        assertEquals(SaveResult.INVALID_SNAPSHOT, store.save(writer, 2, Snapshot(large, 0), true))
        assertEquals(snapshot().checkpoint, store.read(key)!!.checkpoint)
        assertTrue(dataFiles(directory).single().length() <= InteractionProgressStore.MAX_FILE_BYTES)
    }

    @Test
    fun commitFailureRollsBackAndConsumesRevisionWithoutLeavingTemporaryFiles() = runBlocking {
        val directory = temporary.newFolder()
        var fail = false
        val store = InteractionProgressStore(directory) { if (fail) throw IOException("injected commit failure") }
        val writer = store.begin(key)
        assertEquals(SaveResult.SAVED, store.save(writer, 1, snapshot(), true))
        fail = true
        assertEquals(SaveResult.IO_ERROR, store.save(writer, 2, snapshot().copy(positionMs = 999), true))
        assertEquals(snapshot().positionMs, InteractionProgressStore(directory).read(key)!!.positionMs)
        assertEquals(1, directory.listFiles()!!.size)
        fail = false
        assertEquals(SaveResult.STALE_REVISION, store.save(writer, 2, snapshot(), true))
        assertEquals(SaveResult.SAVED, store.save(writer, 3, snapshot().copy(positionMs = 123), true))
        assertEquals(123L, store.read(key)!!.positionMs)
    }

    @Test
    fun interruptedWriteRecoversBackupAndDiscardsUncommittedTemporaryPayload() = runBlocking {
        val directory = temporary.newFolder()
        val store = InteractionProgressStore(directory)
        store.save(store.begin(key), 1, snapshot(), true)
        val file = dataFiles(directory).single()
        assertTrue(file.renameTo(File(file.path + ".bak")))
        file.writeText("{partly-written-new-data")
        File(file.path + ".tmp").writeText("uncommitted")
        assertEquals(snapshot().checkpoint, InteractionProgressStore(directory).read(key)!!.checkpoint)
        assertEquals(listOf(file.name), directory.listFiles()!!.map { it.name })
        assertTrue(file.delete())
        File(file.path + ".tmp").writeText("uncommitted-first-save")
        assertNull(store.read(key))
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun recoveryIoFailureCannotCreateDuplicateRecordsOrReportDisabledRecordingAsSuccessful() = runBlocking {
        val directory = temporary.newFolder()
        val store = InteractionProgressStore(directory)
        val writer = store.begin(key)
        assertEquals(SaveResult.SAVED, store.save(writer, 1, snapshot(), true))
        val file = dataFiles(directory).single()
        val blockedTemporary = File(file.path + ".tmp")
        assertTrue(blockedTemporary.mkdir())
        val child = File(blockedTemporary, "block-deletion").apply { writeText("blocked") }
        assertNull(store.read(key))
        assertEquals(SaveResult.IO_ERROR, store.save(writer, 2, snapshot().copy(positionMs = 999), true))
        assertEquals(SaveResult.IO_ERROR, store.save(writer, 3, snapshot(), false))
        assertEquals(1, dataFiles(directory).size)
        assertTrue(child.delete())
        assertTrue(blockedTemporary.delete())
        assertEquals(1L, store.read(key)!!.revision)
        assertEquals(SaveResult.RECORDING_DISABLED, store.save(writer, 4, snapshot(), false))
        assertNull(store.read(key))
    }

    @Test
    fun invalidStorageLocationReturnsFailureWithoutThrowing() = runBlocking {
        val file = temporary.newFile()
        val store = InteractionProgressStore(file)
        assertNull(store.read(key))
        assertEquals(SaveResult.IO_ERROR, store.save(store.begin(key), 1, snapshot(), true))
        assertTrue(file.isFile)
    }

    @Test
    fun boundedRetentionEvictsWithoutDirectoryEnumerationOrLeftoverJournalFiles() = runBlocking {
        val directory = temporary.newFolder()
        val store = InteractionProgressStore(directory)
        for (index in 1..InteractionProgressStore.MAX_RECORDS + 3) {
            val item = key.copy(aid = index.toLong())
            assertEquals(SaveResult.SAVED, store.save(store.begin(item), 1, snapshot(), true))
        }
        assertEquals(InteractionProgressStore.MAX_RECORDS, dataFiles(directory).size)
        assertEquals(InteractionProgressStore.MAX_RECORDS, directory.listFiles()!!.size)
        val latestKey = key.copy(aid = InteractionProgressStore.MAX_RECORDS + 3L)
        assertTrue(store.read(latestKey) != null)
        val foreignFile = File(directory, "not-progress.json").apply { writeText("foreign") }
        assertTrue(store.read(latestKey) != null)
        assertEquals("foreign", foreignFile.readText())
        assertTrue(store.delete(store.begin(latestKey)))
        assertNull(store.read(latestKey))
        assertEquals(InteractionProgressStore.MAX_RECORDS - 1, dataFiles(directory).size)
    }

    private fun snapshot(graphVersion: Long = 7): Snapshot {
        val variables = mapOf("fraction" to -1.23456789012345, "score" to 12.5, "escaped\\\"{[" to -0.0)
        return Snapshot(
            Checkpoint(graphVersion, 2, 20, variables, RootIdentity(1, 10), listOf(
                Visit(1, 1, 10, mapOf("fraction" to 0.25)), Visit(3, 2, 20, variables)
            )),
            positionMs = 54_321
        )
    }

    private fun dataFiles(directory: File): List<File> =
        directory.listFiles()?.filter { it.name.matches(Regex("progress-[0-9a-f]{2}\\.json")) }.orEmpty()
}
