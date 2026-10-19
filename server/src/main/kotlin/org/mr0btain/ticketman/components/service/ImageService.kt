package org.mr0btain.ticketman.components.service

import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.mr0btain.ticketman.components.database.ImageDatabase
import org.mr0btain.ticketman.components.database.tables.image.ImageStatus
import org.mr0btain.ticketman.components.image.ImageProcessor
import org.mr0btain.ticketman.components.image.ImageStore
import org.mr0btain.ticketman.data.image.ImageLimits
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.error.ImageError
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.logging.AppLogger
import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Takes photo uploads from first byte to a pending row, serves their files, and cleans up after them
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class ImageService(
    private val database: ImageDatabase,
    val store: ImageStore
) {
    private val TAG = "ImageService"
    private val UPLOAD_TAG = "$TAG:Upload"
    private val SWEEP_TAG = "$TAG:Sweep"

    // Decoding a big photo takes a few hundred MB, so only a couple at once
    private val processing = Semaphore(MAX_CONCURRENT_PROCESSING)

    /** A file being received, made in the store's temp folder */
    fun newUploadFile(): File = File.createTempFile("upload", ".bin", store.tempFolder())

    /**
     * Streams [channel] into [target], stopping once it passes [ImageLimits.MAX_UPLOAD_BYTES]
     * @return Bytes written, or null if it was too big
     */
    suspend fun receive(channel: ByteReadChannel, target: File): Long? =
        channel.copyToFile(target, ImageLimits.MAX_UPLOAD_BYTES)

    /**
     * Makes the variants of a received file and adds it as a pending photo. Deletes [upload] either way
     */
    suspend fun store(upload: File, description: String, uploadedBy: Uuid?): Result<StoredImage, ImageError> {
        val identifier = Uuid.generateV7()
        val work = File(store.tempFolder(), identifier.toString())

        val result = try {
            val processed = processing.withPermit {
                withContext(Dispatchers.IO) { ImageProcessor.process(upload, work) }
            }

            when (processed) {
                is Result.Error -> processed

                is Result.Success -> if (!store.moveIn(identifier, work)) {
                    Result.Error(ImageError.FAILED)
                } else {
                    database.insertPending(
                        identifier = identifier,
                        description = description.take(MAX_DESCRIPTION_LENGTH),
                        uploadedBy = uploadedBy,
                        processed = processed.data,
                        folder = store.relativeFolder(identifier)
                    )?.let { Result.Success(it) } ?: run {
                        store.delete(identifier)
                        Result.Error(ImageError.FAILED)
                    }
                }
            }
        } finally {
            upload.delete()
            if (work.exists()) work.deleteRecursively()
        }

        if (result is Result.Success) AppLogger.m(UPLOAD_TAG, "Stored $identifier")
        else AppLogger.i(UPLOAD_TAG, "Upload refused: $result")

        return result
    }

    /**
     * A photo's file and its hash for the ETag, null if it's deleted, missing or not [viewer]'s to see
     * @param viewer Who's asking, see [visibleTo]
     */
    fun fileFor(identifier: Uuid, variant: ImageVariant, viewer: Uuid?): Pair<File, String>? {
        val record = database.getServable(identifier)?.takeIf { it.visibleTo(viewer) } ?: return null
        val file = store.fileFor(identifier, variant)

        return if (file.isFile) Pair(file, record.sha256.ifBlank { identifier.toString() }) else null
    }

    /** A photo's details, null if there's no such photo, it's been deleted or it's not [viewer]'s to see */
    fun get(identifier: Uuid, viewer: Uuid?): StoredImage? =
        database.getServable(identifier)?.takeIf { it.visibleTo(viewer) }?.image

    /** A pending photo isn't on anything yet, so only the person who uploaded it can see it */
    private fun ImageDatabase.ImageRecord.visibleTo(viewer: Uuid?): Boolean =
        status != ImageStatus.PENDING || (viewer != null && uploadedBy == viewer)

    /**
     * Purges photos deleted or left pending a day ago, folders no row knows about, and stale temp files
     * @return How many photos' files were removed
     */
    fun sweep(now: Long = System.currentTimeMillis()): Int {
        val expired = database.sweep(now, GRACE_PERIOD.inWholeMilliseconds)
        expired.forEach { store.delete(it) }

        // Folders with no row, left by a crash between writing files and the insert. Recent ones may be an
        // upload still finishing
        val known = database.allIdentifiers()
        val orphans = store.storedIdentifiers().filter { identifier ->
            identifier !in known && now - store.folderFor(identifier).lastModified() > ORPHAN_AGE.inWholeMilliseconds
        }
        orphans.forEach { store.delete(it) }

        store.tempFolder().listFiles().orEmpty()
            .filter { now - it.lastModified() > ORPHAN_AGE.inWholeMilliseconds }
            .forEach { it.deleteRecursively() }

        if (expired.isNotEmpty() || orphans.isNotEmpty()) {
            AppLogger.m(SWEEP_TAG, "Removed ${expired.size} expired and ${orphans.size} orphaned photos")
        }

        return expired.size + orphans.size
    }

    companion object {
        const val MAX_CONCURRENT_PROCESSING = 2
        const val MAX_DESCRIPTION_LENGTH = 2_000

        /** How long a deleted or never saved photo keeps its files */
        val GRACE_PERIOD = 24.hours

        /** How old a file with no row must be before the sweeper takes it */
        val ORPHAN_AGE = 1.hours

        /** How often the sweeper runs */
        val SWEEP_INTERVAL = 1.hours
    }
}
