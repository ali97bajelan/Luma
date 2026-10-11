package com.luma.lifeos.data.media

import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoLimits
import com.luma.lifeos.domain.model.PhotoMetadata
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
        val previousLease = leasedBy(owner)
        retainPaths(owner, previousLease + path)
        if (target.isFile) return PhotoAttachment(id, path, inspect(target))
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
    fun retain(owner: UUID, photos: List<PhotoAttachment>) = retainPaths(owner, photos.map { it.relativePath }.toSet())
    fun release(owner: UUID) { check(!lease(owner).exists() || lease(owner).delete()) { "Could not release photo draft" } }
    fun resolve(path: String): File? = managed(path)?.takeIf { it.isFile }

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
    }
    private fun managed(path: String): File? {
        if (!path.startsWith("photos/") || !path.endsWith(".img")) return null
        val id = canonicalId(path.removePrefix("photos/").removeSuffix(".img")) ?: return null
        return File(root, "$id.img").takeIf { it.canonicalFile == it.absoluteFile && it.canonicalFile.parentFile == root }
    }
    private fun canonicalId(value: String): UUID? = runCatching { UUID.fromString(value).takeIf { it.toString() == value } }.getOrNull()
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
}
