package org.mr0btain.ticketman.components.image

import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Where photo files live on disk:
 * ```
 * <root>/<first 2 chars of id>/<id>/{original,display,thumb}.jpg
 * ```
 * Paths only ever come from the id, never from anything a client sends. The two character fan out keeps any
 * one folder from holding tens of thousands of entries
 */
@OptIn(ExperimentalUuidApi::class)
class ImageStore(val root: File) {
    private val TAG = "ImageStore"
    private val DELETE_TAG = "$TAG:Delete"

    init {
        root.mkdirs()
    }

    /** Folder for one photo's files, relative to [root] */
    fun relativeFolder(identifier: Uuid): String {
        val id = identifier.toString()
        return "${id.take(2)}/$id"
    }

    fun folderFor(identifier: Uuid): File = File(root, relativeFolder(identifier))

    fun fileFor(identifier: Uuid, variant: ImageVariant): File = File(folderFor(identifier), "${variant.path}.jpg")

    /** Whether every variant of this photo is on disk */
    fun exists(identifier: Uuid): Boolean = ImageVariant.entries.all { fileFor(identifier, it).isFile }

    /** Removes the photo's files, true if nothing is left */
    fun delete(identifier: Uuid): Boolean {
        val folder = folderFor(identifier)
        val deleted = !folder.exists() || folder.deleteRecursively()

        if (!deleted) AppLogger.i(DELETE_TAG, "Couldn't delete $folder")
        folder.parentFile?.takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete()

        return deleted
    }

    /** Ids of every photo folder on disk, for finding files with no row */
    fun storedIdentifiers(): List<Uuid> =
        root.listFiles { file -> file.isDirectory }.orEmpty()
            .flatMap { bucket -> bucket.listFiles { file -> file.isDirectory }.orEmpty().toList() }
            .mapNotNull { Uuid.parseOrNull(it.name) }

    /** Total size of every photo file */
    fun sizeOnDisk(): Long =
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** A scratch folder inside the store, so moving finished files into place stays on one disk */
    fun tempFolder(): File = File(root, ".tmp").apply { mkdirs() }

    /**
     * Moves processed files from [source] into this photo's folder, replacing anything there
     * @return Whether the move worked
     */
    fun moveIn(identifier: Uuid, source: File): Boolean {
        return try {
            val target = folderFor(identifier)
            target.parentFile.mkdirs()
            if (target.exists()) target.deleteRecursively()

            // Same disk as the temp folder, so this is a rename
            val moved = source.renameTo(target) || run {
                source.copyRecursively(target, overwrite = true)
                source.deleteRecursively()
            }

            moved && exists(identifier)
        } catch (ex: Exception) {
            AppLogger.e(TAG, SimpleResult.FAIL, ex)
            false
        }
    }
}
