package com.luma.lifeos.ui.mood

import android.content.Context
import android.util.AtomicFile
import com.luma.lifeos.domain.model.EventId
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FileMoodDraftStore(context: Context) : MoodDraftStore {
    private val directory = File(context.filesDir, "mood-drafts")
    private val locks = ConcurrentHashMap<UUID, Mutex>()

    override suspend fun read(id: UUID): MoodDraftSnapshot? = withContext(Dispatchers.IO) {
        lockFor(id).withLock { readUnlocked(id) }
    }

    override suspend fun write(id: UUID, snapshot: MoodDraftSnapshot) = withContext(Dispatchers.IO) {
        lockFor(id).withLock {
            require(directory.isDirectory || directory.mkdirs()) { "Unable to create Mood draft directory" }
            val file = atomicFile(id)
            val current = readUnlocked(id)
            if (current != null && current.revision >= snapshot.revision) {
                if (current == snapshot) return@withLock
                throw IllegalStateException("Mood draft revision ${snapshot.revision} is older than the stored revision ${current.revision}")
            }
            val stream = file.startWrite()
            try {
                val output = DataOutputStream(BufferedOutputStream(stream))
                writeSnapshot(output, snapshot)
                output.flush()
                file.finishWrite(stream)
            } catch (failure: Throwable) {
                file.failWrite(stream)
                throw failure
            }
        }
    }

    override suspend fun remove(id: UUID) = withContext(Dispatchers.IO) {
        lockFor(id).withLock { atomicFile(id).delete() }
    }

    private fun lockFor(id: UUID): Mutex = locks.getOrPut(id) { Mutex() }

    private fun atomicFile(id: UUID) = AtomicFile(File(directory, "$id.bin"))

    private fun readUnlocked(id: UUID): MoodDraftSnapshot? {
        val target = File(directory, "$id.bin")
        if (!target.exists() && !File(target.path + ".bak").exists()) return null
        return DataInputStream(BufferedInputStream(atomicFile(id).openRead())).use(::readSnapshot)
    }

    private fun writeSnapshot(output: DataOutputStream, snapshot: MoodDraftSnapshot) {
        output.writeInt(MAGIC)
        output.writeInt(VERSION)
        output.writeLong(snapshot.revision)
        writeDraft(output, snapshot.draft)
        output.writeNullableString(snapshot.pendingDeleteId?.toString())
        output.writeBoolean(snapshot.completion != null)
        snapshot.completion?.let {
            output.writeUTF(it.eventId.toString())
            output.writeBoolean(it.deleted)
        }
    }

    private fun readSnapshot(input: DataInputStream): MoodDraftSnapshot {
        require(input.readInt() == MAGIC) { "Invalid Mood draft file" }
        require(input.readInt() == VERSION) { "Unsupported Mood draft file version" }
        val revision = input.readLong()
        val draft = readDraft(input)
        val pendingDelete = input.readNullableString()?.let(EventId::parse)
        val completion = if (input.readBoolean()) MoodDraftCompletion(EventId.parse(input.readUTF()), input.readBoolean()) else null
        return MoodDraftSnapshot(draft, revision, pendingDelete, completion)
    }

    private fun writeDraft(output: DataOutputStream, draft: MoodDraft) {
        output.writeLong(draft.occurredAtMillis)
        output.writeNullableInt(draft.score)
        output.writeUuidSet(draft.emotionIds)
        output.writeNullableInt(draft.energy)
        output.writeNullableInt(draft.stress)
        output.writeLongString(draft.note)
        output.writeUuidSet(draft.personIds)
        output.writeNullableString(draft.placeId?.toString())
    }

    private fun readDraft(input: DataInputStream) = MoodDraft(
        occurredAtMillis = input.readLong(),
        score = input.readNullableInt(),
        emotionIds = input.readUuidSet(),
        energy = input.readNullableInt(),
        stress = input.readNullableInt(),
        note = input.readLongString(),
        personIds = input.readUuidSet(),
        placeId = input.readNullableString()?.let(UUID::fromString),
    )

    private fun DataOutputStream.writeNullableInt(value: Int?) {
        writeBoolean(value != null)
        if (value != null) writeInt(value)
    }

    private fun DataInputStream.readNullableInt(): Int? = if (readBoolean()) readInt() else null

    private fun DataOutputStream.writeLongString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readLongString(): String {
        val length = readInt()
        require(length >= 0 && length <= available()) { "Invalid Mood note length" }
        val bytes = ByteArray(length)
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun DataOutputStream.writeUuidSet(values: Set<UUID>) {
        writeInt(values.size)
        values.sortedBy(UUID::toString).forEach { writeUTF(it.toString()) }
    }

    private fun DataInputStream.readUuidSet(): Set<UUID> {
        val count = readInt()
        require(count in 0..MAX_SET_SIZE) { "Invalid Mood draft selection size" }
        return buildSet(count) { repeat(count) { add(UUID.fromString(readUTF())) } }
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullableString(): String? = if (readBoolean()) readUTF() else null

    private companion object {
        const val MAGIC = 0x4D4F4F44
        const val VERSION = 1
        const val MAX_SET_SIZE = 10_000
    }
}
