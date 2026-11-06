package com.luma.lifeos.data.media

import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoLimits
import com.luma.lifeos.domain.model.PhotoMetadata
import com.luma.lifeos.domain.model.PhotoWorkingLimits
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Pure file operations. The repository serializes them with reference queries using one mutex. */
class ManagedPhotoFiles(directory: File, private val maxBytes: Long = PhotoLimits.BYTES) {
    private val root = directory.canonicalFile
    private val drafts = File(root, ".drafts")
    init { require(maxBytes > 0); check(root.isDirectory || root.mkdirs()); check(drafts.isDirectory || drafts.mkdirs()); check(drafts.canonicalFile == drafts.absoluteFile) }

    fun import(owner: UUID, id: UUID, open: () -> InputStream, inspect: (File) -> PhotoMetadata): PhotoAttachment {
        val path = "photos/$id.img"
        val target = managed(path) ?: error("Unsafe photo file")
        require(!operationFile(id).exists()) { "Photo identity is reserved for a derived image" }
        val previousLease = leasedBy(owner)
        if (target.exists()) {
            require(path in previousLease && target.isFile) { "Photo identity already exists outside this draft" }
            return PhotoAttachment(id, path, inspect(target))
        }
        retainPaths(owner, previousLease + path)
        val temporary = File(root, "$id.part")
        check(temporary.canonicalFile == temporary.absoluteFile)
        try {
            open().use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8192)
                    var size = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        size += count
                        require(size <= maxBytes) { "Photo is too large" }
                        output.write(buffer, 0, count)
                    }
                    require(size > 0) { "Photo is empty" }
                    output.fd.sync()
                }
            }
            val metadata = inspect(temporary)
            move(temporary, target)
            return PhotoAttachment(id, path, metadata)
        } catch (failure: Exception) {
            temporary.delete()
            retainPaths(owner, previousLease)
            throw failure
        }
    }
    /** Caller holds the shared photo mutex until reference commit or rollback. No draft lease. */
    fun publishBackup(id: UUID, open: () -> InputStream, inspect: (File) -> PhotoMetadata): PhotoAttachment {
        val path = "photos/$id.img"
        val target = requireNotNull(managed(path))
        require(!operationFile(id).exists()) { "Photo identity is reserved for a derived image" }
        require(!target.exists()) { "Backup file identity is already in use" }
        val temporary = File(root, "$id.part")
        require(temporary.canonicalFile == temporary.absoluteFile && !temporary.exists())
        try {
            FileOutputStream(temporary).use { output ->
                open().use { input ->
                    val buffer = ByteArray(8192); var size = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        size += count; require(size <= maxBytes)
                        output.write(buffer, 0, count)
                    }
                    require(size > 0)
                }
                output.fd.sync()
            }
            val metadata = inspect(temporary)
            move(temporary, target)
            return PhotoAttachment(id, path, metadata)
        } finally { temporary.delete() }
    }

    /**
     * Atomically publishes an immutable transformed PNG. The operation descriptor binds this output
     * ID to its source/edit payload while its lease or committed attachment keeps it active.
     */
    fun publishDerived(
        owner: UUID,
        id: UUID,
        operation: String,
        encode: (OutputStream) -> Unit,
        inspect: (File) -> PhotoMetadata,
        ensureActive: () -> Unit = {},
    ): PhotoAttachment {
        require(operation.isNotBlank() && operation.length <= 1024)
        val path = "photos/$id.img"
        val target = managed(path) ?: error("Unsafe photo file")
        val operationFile = operationFile(id)
        val previousLease = leasedBy(owner)
        val existingOperation = readOperation(operationFile)
        if (target.exists()) {
            require(path in previousLease) { "Derived photo identity is not retained by this draft" }
            require(existingOperation == operation) { "Derived photo identity belongs to another operation" }
            val metadata = inspect(target)
            validateDerived(target, metadata)
            return PhotoAttachment(id, path, metadata)
        }
        if (existingOperation == null) bindOperation(operationFile, id, operation)
        else require(existingOperation == operation) { "Derived photo identity belongs to another operation" }

        // Retain before creating the temporary file so cleanup cannot race publication.
        retainPaths(owner, previousLease + path)
        val temporary = File(root, "$id.part")
        check(temporary.canonicalFile == temporary.absoluteFile)
        try {
            ensureActive()
            FileOutputStream(temporary, false).use { output ->
                val bounded = BoundedOutputStream(output, maxBytes)
                encode(bounded)
                bounded.flush()
                require(bounded.size > 0L) { "Transformed photo is empty" }
                output.fd.sync()
            }
            ensureActive()
            val metadata = inspect(temporary)
            validateDerived(temporary, metadata)
            ensureActive()
            moveNew(temporary, target)
            return PhotoAttachment(id, path, metadata)
        } finally {
            temporary.delete()
        }
    }

    fun retain(owner: UUID, photos: List<PhotoAttachment>) {
        require(photos.all { photo ->
            photo.relativePath == "photos/${photo.id}.img" && managed(photo.relativePath) != null
        }) { "Photo identity does not match its managed path" }
        retainPaths(owner, photos.map { it.relativePath }.toSet())
    }
    fun release(owner: UUID) { check(!lease(owner).exists() || lease(owner).delete()) { "Could not release photo draft" } }
    fun resolve(path: String): File? = managed(path)?.takeIf { it.isFile }
    fun resolve(photo: PhotoAttachment): File? =
        photo.relativePath.takeIf { it == "photos/${photo.id}.img" }?.let(::resolve)

    fun isRetainedBy(owner: UUID, photo: PhotoAttachment): Boolean = try {
        val path = "photos/${photo.id}.img"
        if (photo.relativePath != path || managed(path) == null) false
        else {
            val lease = lease(owner)
            lease.isFile && lease.canonicalFile == lease.absoluteFile && lease.readLines().any { it == path }
        }
    } catch (_: Exception) {
        false
    }

    fun clean(referenced: Set<String>) {
        // An unreadable lease aborts cleanup rather than risking a draft's files.
        val protected = drafts.listFiles().orEmpty().filter { it.extension == "txt" }.flatMap { file ->
            check(file.canonicalFile == file.absoluteFile)
            file.readLines().filter(String::isNotBlank)
        }.toSet() + referenced
        root.listFiles().orEmpty().forEach { file ->
            val suffix = when { file.name.endsWith(".img") -> ".img"; file.name.endsWith(".part") -> ".part"; else -> return@forEach }
            val id = canonicalId(file.name.removeSuffix(suffix)) ?: return@forEach
            if ("photos/$id.img" !in protected && file.canonicalFile == file.absoluteFile) file.delete()
        }
        drafts.listFiles().orEmpty().filter { it.extension == "tmp" }.forEach { file ->
            if (file.canonicalFile == file.absoluteFile) file.delete()
        }
        drafts.listFiles().orEmpty().filter { it.name.endsWith(".operation") }.forEach { file ->
            val id = canonicalId(file.name.removeSuffix(".operation")) ?: return@forEach
            if ("photos/$id.img" !in protected && file.canonicalFile == file.absoluteFile) file.delete()
        }
    }
    private fun managed(path: String): File? {
        if (!path.startsWith("photos/") || !path.endsWith(".img")) return null
        val id = canonicalId(path.removePrefix("photos/").removeSuffix(".img")) ?: return null
        return File(root, "$id.img").takeIf { it.canonicalFile == it.absoluteFile && it.canonicalFile.parentFile == root }
    }
    private fun canonicalId(value: String): UUID? = runCatching { UUID.fromString(value).takeIf { it.toString() == value } }.getOrNull()
    private fun operationFile(id: UUID) = File(drafts, "$id.operation").also {
        check(it.canonicalFile == it.absoluteFile && it.canonicalFile.parentFile == drafts)
    }
    private fun readOperation(file: File): String? {
        if (!file.exists()) return null
        check(file.isFile && file.canonicalFile == file.absoluteFile)
        require(file.length() <= 4096L) { "Derived photo operation descriptor is too large" }
        return file.readText(Charsets.UTF_8)
    }
    fun isBoundToDerivedOperation(id: UUID, operation: String): Boolean =
        readOperation(operationFile(id)) == operation
    private fun bindOperation(file: File, id: UUID, operation: String) {
        val previous = readOperation(file)
        if (previous != null) {
            require(previous == operation) { "Derived photo identity belongs to another operation" }
            return
        }
        val temporary = File(drafts, "$id-${UUID.randomUUID()}.tmp")
        check(temporary.canonicalFile == temporary.absoluteFile)
        try {
            FileOutputStream(temporary, false).use { output ->
                output.write(operation.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            moveNew(temporary, file)
        } finally {
            temporary.delete()
        }
    }
    private fun validateDerived(file: File, metadata: PhotoMetadata) {
        require(file.isFile && file.length() in 1..maxBytes) { "Transformed photo is too large or empty" }
        require(metadata.byteCount == file.length() && metadata.mimeType.equals("image/png", ignoreCase = true)) {
            "Transformed photo is not a validated PNG"
        }
        require(
            maxOf(metadata.width, metadata.height) <= PhotoWorkingLimits.EDGE &&
                metadata.width.toLong() * metadata.height.toLong() <= PhotoWorkingLimits.PIXELS,
        ) { "Transformed photo dimensions are too large" }
    }
    private class BoundedOutputStream(
        private val delegate: OutputStream,
        private val maximumBytes: Long,
    ) : OutputStream() {
        var size = 0L
            private set

        override fun write(value: Int) {
            requireCapacity(1)
            delegate.write(value)
            size++
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (offset < 0 || length < 0 || length > bytes.size - offset) throw IndexOutOfBoundsException()
            requireCapacity(length.toLong())
            delegate.write(bytes, offset, length)
            size += length
        }

        override fun flush() = delegate.flush()

        private fun requireCapacity(additionalBytes: Long) {
            if (additionalBytes > maximumBytes - size) throw IOException("Transformed photo is too large")
        }
    }
    private fun lease(owner: UUID) = File(drafts, "$owner.txt").also { check(it.canonicalFile == it.absoluteFile) }
    private fun leasedBy(owner: UUID): Set<String> = lease(owner).takeIf { it.exists() }?.readLines()?.filter(String::isNotBlank)?.toSet().orEmpty()
    private fun retainPaths(owner: UUID, paths: Set<String>) {
        val target = lease(owner)
        val temporary = File(drafts, "${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(paths.filter { managed(it) != null }.sorted().joinToString("\n").toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            move(temporary, target)
        } finally { temporary.delete() }
    }
    private fun move(source: File, target: File) {
        try { Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }
    private fun moveNew(source: File, target: File) {
        try { Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(source.toPath(), target.toPath()) }
    }
}
