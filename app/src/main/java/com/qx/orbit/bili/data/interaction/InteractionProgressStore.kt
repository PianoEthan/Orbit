package com.qx.orbit.bili.data.interaction

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Strictness
import com.qx.orbit.bili.util.interaction.InteractionSession
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Share one store per directory. Writer ownership is local to this store, not a cross-process lock. */
class InteractionProgressStore(private val directory: File) {
    data class Key(
        val accountKey: Long,
        val graphVersion: Long,
        val aid: Long? = null,
        val bvid: String? = null
    )

    data class Snapshot(
        val checkpoint: InteractionSession.Checkpoint,
        val positionMs: Long,
        val noBacktracking: Boolean = false
    )

    data class Record(
        val schemaVersion: Int,
        val key: Key,
        val checkpoint: InteractionSession.Checkpoint,
        val positionMs: Long,
        val noBacktracking: Boolean,
        val updatedAt: Long,
        val sessionId: String,
        val revision: Long
    )

    class Writer internal constructor(internal val key: Key, internal val sessionId: String) {
        internal var revision = 0L
    }

    enum class SaveResult {
        SAVED, RECORDING_DISABLED, STALE_WRITER, STALE_REVISION, INVALID_SNAPSHOT, IO_ERROR
    }

    private val lock = Any()
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    private val writers = LinkedHashMap<Key, Writer>()
    private var beforeCommit: (() -> Unit)? = null

    internal constructor(directory: File, beforeCommit: () -> Unit) : this(directory) {
        this.beforeCommit = beforeCommit
    }

    /** Beginning a new player session revokes the previous writer for exactly this key. */
    fun begin(key: Key): Writer = synchronized(lock) {
        require(validKey(key)) { "Invalid interaction progress key" }
        val writer = Writer(key, UUID.randomUUID().toString())
        writers.remove(key)
        writers[key] = writer
        if (writers.size > MAX_RECORDS) writers.remove(writers.keys.first())
        writer
    }

    /** Invalidate synchronously on account/session changes, before launching any further IO. */
    fun invalidate(writer: Writer): Boolean = synchronized(lock) {
        if (writers[writer.key] !== writer) return@synchronized false
        writers.remove(writer.key)
        true
    }

    /**
     * Pass only Session.checkpoint(), never a pending Transition. Assign revisions at capture time,
     * not when IO starts. An attempted write consumes its revision even when IO fails.
     */
    suspend fun save(
        writer: Writer,
        revision: Long,
        snapshot: Snapshot,
        recordingAllowed: Boolean
    ): SaveResult = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (writers[writer.key] !== writer) return@synchronized SaveResult.STALE_WRITER
            if (revision <= writer.revision) return@synchronized SaveResult.STALE_REVISION
            writer.revision = revision
            if (!recordingAllowed) {
                return@synchronized if (erase(writer.key)) SaveResult.RECORDING_DISABLED else SaveResult.IO_ERROR
            }
            val frozen = freeze(snapshot, writer.key) ?: return@synchronized SaveResult.INVALID_SNAPSHOT
            val record = Record(
                SCHEMA_VERSION, writer.key, frozen.checkpoint, frozen.positionMs,
                frozen.noBacktracking, System.currentTimeMillis().coerceAtLeast(0), writer.sessionId, revision
            )
            try {
                val bytes = gson.toJson(encode(record)).toByteArray(Charsets.UTF_8)
                if (bytes.size > MAX_FILE_BYTES) return@synchronized SaveResult.INVALID_SNAPSHOT
                writeSlot(writableSlot(writer.key), bytes)
                SaveResult.SAVED
            } catch (_: Exception) {
                SaveResult.IO_ERROR
            }
        }
    }

    suspend fun read(key: Key): Record? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!validKey(key)) return@synchronized null
            slots(key).firstNotNullOfOrNull { readSlot(it)?.takeIf { record -> record.key == key } }
        }
    }

    /** Deletes the key and revokes this writer, including already queued saves. */
    suspend fun delete(writer: Writer): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (writers[writer.key] !== writer) return@synchronized false
            writers.remove(writer.key)
            erase(writer.key)
        }
    }

    private fun erase(key: Key): Boolean = try {
        for (slot in slots(key)) {
            val record = readSlot(slot, failOnIoError = true)
            if (record == null || record.key == key) {
                val file = file(slot)
                remove(File(file.path + ".tmp"))
                remove(File(file.path + ".bak"))
                remove(file)
            }
        }
        true
    } catch (_: Exception) {
        false
    }

    // A fixed hash table bounds both files and lookups without enumerating an unbounded directory.
    private fun slots(key: Key): List<Int> {
        val identity = "${key.accountKey}|${key.aid?.let { "aid:$it" } ?: "bvid:${key.bvid}"}|${key.graphVersion}"
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        val first = (hash[0].toInt() and 0xff) % MAX_RECORDS
        return List(MAX_RECORDS) { (first + it) % MAX_RECORDS }
    }

    private fun writableSlot(key: Key): Int {
        var empty: Int? = null
        var oldest: Int? = null
        var oldestTime = Long.MAX_VALUE
        for (slot in slots(key)) {
            val record = readSlot(slot, failOnIoError = true)
            if (record?.key == key) return slot
            if (record == null) {
                if (empty == null) empty = slot
            } else if (oldest == null || record.updatedAt < oldestTime) {
                oldest = slot
                oldestTime = record.updatedAt
            }
        }
        return empty ?: checkNotNull(oldest)
    }

    private fun file(slot: Int) = File(directory, "progress-${slot.toString(16).padStart(2, '0')}.json")

    private fun readSlot(slot: Int, failOnIoError: Boolean = false): Record? = try {
        val file = file(slot)
        recover(file)
        if (file.exists() && !file.isFile) throw IOException("Progress slot is not a file")
        if (!file.isFile || file.length() > MAX_FILE_BYTES) null else {
            val bytes = file.inputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count = input.read(buffer)
                while (count != -1) {
                    if (output.size() + count > MAX_FILE_BYTES) throw IOException("Progress file too large")
                    output.write(buffer, 0, count)
                    count = input.read(buffer)
                }
                output.toByteArray()
            }
            decode(bytes.toString(Charsets.UTF_8))
        }
    } catch (failure: IOException) {
        if (failOnIoError) throw failure else null
    } catch (failure: SecurityException) {
        if (failOnIoError) throw failure else null
    } catch (_: Exception) {
        null
    }

    private fun writeSlot(slot: Int, bytes: ByteArray) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create progress directory")
        val file = file(slot)
        val temporary = File(file.path + ".tmp")
        val backup = File(file.path + ".bak")
        recover(file)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (file.exists() && !file.renameTo(backup)) throw IOException("Cannot back up progress")
            beforeCommit?.invoke()
            if (!temporary.renameTo(file)) throw IOException("Cannot commit progress")
            remove(backup)
        } catch (failure: Exception) {
            recover(file)
            throw failure
        } finally {
            remove(temporary)
        }
    }

    private fun recover(file: File) {
        val backup = File(file.path + ".bak")
        if (backup.exists()) {
            remove(file)
            if (!backup.renameTo(file)) throw IOException("Cannot recover progress")
        }
        remove(File(file.path + ".tmp"))
    }

    private fun remove(file: File) {
        if (file.exists() && !file.delete()) throw IOException("Cannot remove progress file")
    }

    private fun freeze(snapshot: Snapshot, key: Key): Snapshot? = try {
        val checkpoint = snapshot.checkpoint
        if (snapshot.positionMs !in 0..MAX_POSITION_MS || checkpoint.graphVersion != key.graphVersion ||
            checkpoint.edgeId <= 0 || checkpoint.cid <= 0 || checkpoint.root.edgeId <= 0 || checkpoint.root.cid <= 0 ||
            !validVariables(checkpoint.variables) || checkpoint.visits.size > InteractionSession.MAX_VISITS ||
            checkpoint.visits.any {
                it.visitId !in 1..InteractionSession.MAX_VISIT_ID || it.edgeId <= 0 || it.cid <= 0 || !validVariables(it.variables)
            } || checkpoint.visits.zipWithNext().any { (previous, next) -> previous.visitId >= next.visitId } ||
            checkpoint.visits.lastOrNull()?.let { it.edgeId != checkpoint.edgeId || it.cid != checkpoint.cid } == true
        ) null else snapshot.copy(checkpoint = checkpoint.copy(
            variables = checkpoint.variables.toMap(),
            visits = checkpoint.visits.map { it.copy(variables = it.variables.toMap()) }
        ))
    } catch (_: Exception) {
        null
    }

    private fun validKey(key: Key): Boolean = key.accountKey >= 0 && key.graphVersion > 0 &&
        ((key.aid != null && key.aid > 0 && key.bvid == null) ||
            (key.aid == null && key.bvid != null && BVID.matches(key.bvid)))

    private fun validVariables(variables: Map<String, Double>): Boolean =
        variables.size <= InteractionSession.MAX_VARIABLES && variables.all { (name, value) ->
            name.isNotBlank() && name.length <= InteractionSession.MAX_VARIABLE_NAME && value.isFinite()
        }

    private fun encode(record: Record): JsonObject = JsonObject().apply {
        addProperty("schemaVersion", record.schemaVersion)
        add("key", JsonObject().apply {
            addProperty("accountKey", record.key.accountKey)
            addProperty("graphVersion", record.key.graphVersion)
            record.key.aid?.let { addProperty("aid", it) }
            record.key.bvid?.let { addProperty("bvid", it) }
        })
        val checkpoint = record.checkpoint
        add("checkpoint", JsonObject().apply {
            addProperty("graphVersion", checkpoint.graphVersion)
            addProperty("edgeId", checkpoint.edgeId)
            addProperty("cid", checkpoint.cid)
            add("variables", encodeVariables(checkpoint.variables))
            add("root", JsonObject().apply {
                addProperty("edgeId", checkpoint.root.edgeId)
                addProperty("cid", checkpoint.root.cid)
            })
            add("visits", JsonArray().apply {
                checkpoint.visits.forEach { visit ->
                    add(JsonObject().apply {
                        addProperty("visitId", visit.visitId)
                        addProperty("edgeId", visit.edgeId)
                        addProperty("cid", visit.cid)
                        add("variables", encodeVariables(visit.variables))
                    })
                }
            })
        })
        addProperty("positionMs", record.positionMs)
        addProperty("noBacktracking", record.noBacktracking)
        addProperty("updatedAt", record.updatedAt)
        addProperty("sessionId", record.sessionId)
        addProperty("revision", record.revision)
    }

    private fun encodeVariables(variables: Map<String, Double>): JsonObject = JsonObject().apply {
        variables.forEach { (name, value) -> addProperty(name, value) }
    }

    private fun decode(raw: String): Record? {
        if (!boundedNesting(raw)) return null
        val obj = gson.fromJson(raw, JsonObject::class.java) ?: return null
        if (obj.long("schemaVersion") != SCHEMA_VERSION.toLong()) return null
        val keyObject = obj.objectValue("key")
        val key = Key(
            keyObject.long("accountKey"), keyObject.long("graphVersion"),
            keyObject.optionalLong("aid"), keyObject.optionalString("bvid")
        )
        if (!validKey(key)) return null
        val cp = obj.objectValue("checkpoint")
        val root = cp.objectValue("root")
        val visitArray = cp.get("visits")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        if (visitArray.size() > InteractionSession.MAX_VISITS) return null
        val checkpoint = InteractionSession.Checkpoint(
            cp.long("graphVersion"), cp.long("edgeId"), cp.long("cid"), cp.variables(),
            InteractionSession.RootIdentity(root.long("edgeId"), root.long("cid")),
            visitArray.map {
                val visit = it.asJsonObject
                InteractionSession.Visit(visit.long("visitId"), visit.long("edgeId"), visit.long("cid"), visit.variables())
            }
        )
        val snapshot = Snapshot(checkpoint, obj.long("positionMs"), obj.boolean("noBacktracking"))
        val frozen = freeze(snapshot, key) ?: return null
        val updatedAt = obj.long("updatedAt")
        val revision = obj.long("revision")
        val sessionId = obj.string("sessionId")
        if (updatedAt < 0 || revision <= 0 || sessionId.length != 36 || UUID.fromString(sessionId).toString() != sessionId) return null
        return Record(SCHEMA_VERSION, key, frozen.checkpoint, frozen.positionMs, frozen.noBacktracking, updatedAt, sessionId, revision)
    }

    private fun JsonObject.variables(): Map<String, Double> {
        val obj = objectValue("variables")
        require(obj.size() <= InteractionSession.MAX_VARIABLES)
        return obj.entrySet().associate { (name, value) ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
            name to value.asDouble.also { require(it.isFinite()) }
        }
    }

    private fun JsonObject.objectValue(name: String): JsonObject =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: throw IllegalArgumentException(name)

    private fun JsonObject.long(name: String): Long {
        val value = get(name) ?: throw IllegalArgumentException(name)
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asString.takeIf { INTEGER.matches(it) }?.toLongOrNull() ?: throw IllegalArgumentException(name)
    }

    private fun JsonObject.string(name: String): String {
        val value = get(name) ?: throw IllegalArgumentException(name)
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.boolean(name: String): Boolean {
        val value = get(name) ?: throw IllegalArgumentException(name)
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
        return value.asBoolean
    }

    private fun JsonObject.optionalLong(name: String): Long? = if (has(name)) long(name) else null
    private fun JsonObject.optionalString(name: String): String? = if (has(name)) string(name) else null

    private fun boundedNesting(raw: String): Boolean {
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in raw) {
            if (quoted) {
                if (escaped) escaped = false else if (character == '\\') escaped = true else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > 12) return false
                '}', ']' -> if (--depth < 0) return false
            }
        }
        return depth == 0 && !quoted
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_RECORDS = 64
        const val MAX_FILE_BYTES = 262_144
        const val MAX_POSITION_MS = 604_800_000L
        private val INTEGER = Regex("-?(0|[1-9][0-9]*)")
        private val BVID = Regex("BV[0-9A-Za-z]{10}")
    }
}
