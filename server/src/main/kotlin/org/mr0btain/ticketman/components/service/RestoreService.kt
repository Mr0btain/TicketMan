package org.mr0btain.ticketman.components.service

import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.server.response.BackupManifest
import org.mr0btain.ticketman.server.response.PendingRestore
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.time.Clock

/**
 * Restoring from a backup archive, in two halves:
 * 1. [stage] while running: checks an uploaded archive and parks it in `<serverDir>/restore/`
 * 2. [applyPending] on the next start, before the database is opened: moves `database/` and `images/` aside to
 *    `pre-restore-<time>/` (kept, so a restore can be undone by hand) and unpacks the archive in their place
 *
 * Swapping the H2 files under a running server isn't safe, open connections and caches would see half of each,
 * so the restart is the switch over
 * @param serverDir The server's folder, holding `database/` and `images/`
 */
@Suppress("PrivatePropertyName")
class RestoreService(private val serverDir: File) {
    private val STAGE_TAG = "$TAG:Stage"

    private val restoreDir = File(serverDir, RESTORE_FOLDER)
    private val pendingArchive = File(restoreDir, PENDING_ARCHIVE)
    private val pendingInfo = File(restoreDir, PENDING_INFO)

    /** A new file to receive an upload into, on the same disk as where it'll be kept */
    fun newUploadFile(): File = File.createTempFile("restore", ".upload", restoreDir.apply { mkdirs() })

    /** Space left where the upload and the unpacked copy will go */
    fun usableSpace(): Long = restoreDir.apply { mkdirs() }.usableSpace

    /**
     * Checks an uploaded archive and keeps it for the next start, replacing any restore already waiting.
     * Deletes [upload] either way
     * @return What's waiting, or why the archive was refused
     */
    fun stage(upload: File): Result<PendingRestore> {
        return try {
            val manifest = inspect(upload).getOrElse { return Result.failure(it) }
            val pending = PendingRestore(manifest, upload.length(), Clock.System.now().toEpochMilliseconds())

            pendingArchive.delete()
            if (!upload.renameTo(pendingArchive)) upload.copyTo(pendingArchive, overwrite = true)
            pendingInfo.writeText(json.encodeToString(pending))

            AppLogger.m(STAGE_TAG, "Restore waiting for restart: ${manifest.imageCount} photos from ${manifest.createdAt}")
            Result.success(pending)
        } catch (ex: Exception) {
            AppLogger.e(STAGE_TAG, SimpleResult.FAIL, ex)
            Result.failure(RestoreException("The backup couldn't be kept for restoring"))
        } finally {
            upload.delete()
        }
    }

    /** The restore waiting for the next start, if there is one */
    fun pending(): PendingRestore? =
        if (pendingArchive.isFile && pendingInfo.isFile) {
            runCatching { json.decodeFromString<PendingRestore>(pendingInfo.readText()) }.getOrNull()
        } else null

    /** Drops the restore that was waiting */
    fun cancel(): Boolean {
        val had = pendingArchive.exists()
        pendingArchive.delete()
        pendingInfo.delete()
        return had
    }

    /** Reads the manifest and makes sure every entry is something we'd unpack, and unpacks somewhere safe */
    private fun inspect(archive: File): Result<BackupManifest> {
        val zip = try {
            ZipFile(archive)
        } catch (ex: Exception) {
            return Result.failure(RestoreException("That file isn't a zip"))
        }

        return zip.use {
            val manifestEntry = zip.getEntry(BackupManifest.FILE_NAME)
                ?: return Result.failure(RestoreException("That zip isn't a TicketMan backup, it has no manifest"))

            val manifest = runCatching {
                json.decodeFromString<BackupManifest>(zip.getInputStream(manifestEntry).readBytes().decodeToString())
            }.getOrElse { return Result.failure(RestoreException("The backup's manifest couldn't be read")) }

            if (manifest.formatVersion > BackupManifest.CURRENT_FORMAT_VERSION) {
                return Result.failure(RestoreException("That backup is from a newer server, update this one first"))
            }

            if (zip.getEntry(manifest.databaseFile) == null) {
                return Result.failure(RestoreException("The backup has no database in it"))
            }

            zip.entries().asSequence().forEach { entry ->
                if (!isAllowed(entry.name, manifest)) {
                    return Result.failure(RestoreException("The backup has an unexpected file in it: ${entry.name}"))
                }
            }

            Result.success(manifest)
        }
    }

    companion object {
        private const val TAG = "RestoreService"
        private const val APPLY_TAG = "$TAG:Apply"

        const val RESTORE_FOLDER = "restore"
        const val PENDING_ARCHIVE = "pending.zip"
        const val PENDING_INFO = "pending.json"
        const val DATABASE_FOLDER = "database"
        const val IMAGES_FOLDER = "images"
        const val SET_ASIDE_PREFIX = "pre-restore-"

        private val json = Json { ignoreUnknownKeys = true }

        /** Only the manifest, the database copy and photo files under images/, with no way out of their folder */
        private fun isAllowed(name: String, manifest: BackupManifest): Boolean {
            if (name.startsWith("/") || name.contains('\\') || name.split('/').any { it == ".." }) return false

            return name == BackupManifest.FILE_NAME ||
                name == manifest.databaseFile ||
                name.startsWith("${BackupManifest.IMAGES_FOLDER}/")
        }

        /**
         * Applies a staged restore. Call before the database is opened. On any failure the data that was set
         * aside is put back, so the server starts on what it had
         * @return Whether a restore was applied
         */
        fun applyPending(serverDir: File): Boolean {
            val restoreDir = File(serverDir, RESTORE_FOLDER)
            val archive = File(restoreDir, PENDING_ARCHIVE)
            if (!archive.isFile) return false

            val databaseDir = File(serverDir, DATABASE_FOLDER)
            val imagesDir = File(serverDir, IMAGES_FOLDER)
            val setAside = File(serverDir, "$SET_ASIDE_PREFIX${System.currentTimeMillis()}").apply { mkdirs() }

            return try {
                if (databaseDir.exists()) check(databaseDir.renameTo(File(setAside, DATABASE_FOLDER))) { "Couldn't move the database aside" }
                if (imagesDir.exists()) check(imagesDir.renameTo(File(setAside, IMAGES_FOLDER))) { "Couldn't move the photos aside" }

                unpack(archive, databaseDir, imagesDir)

                archive.delete()
                File(restoreDir, PENDING_INFO).delete()
                AppLogger.m(APPLY_TAG, "Restored from backup, the old data is in ${setAside.name}")
                true
            } catch (ex: Exception) {
                AppLogger.e(APPLY_TAG, "Restore failed, putting the old data back", ex)

                databaseDir.deleteRecursively()
                imagesDir.deleteRecursively()
                File(setAside, DATABASE_FOLDER).renameTo(databaseDir)
                File(setAside, IMAGES_FOLDER).renameTo(imagesDir)
                setAside.deleteRecursively()

                // Don't try again every start, keep it for a look
                archive.renameTo(File(restoreDir, "failed-${System.currentTimeMillis()}.zip"))
                File(restoreDir, PENDING_INFO).delete()
                false
            }
        }

        private fun unpack(archive: File, databaseDir: File, imagesDir: File) {
            val manifest = ZipFile(archive).use { zip ->
                json.decodeFromString<BackupManifest>(zip.getInputStream(zip.getEntry(BackupManifest.FILE_NAME)).readBytes().decodeToString())
            }

            databaseDir.mkdirs()
            imagesDir.mkdirs()

            ZipFile(archive).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                    check(isAllowed(entry.name, manifest)) { "Unexpected entry ${entry.name}" }

                    when {
                        // H2's backup is itself a zip of the database files
                        entry.name == manifest.databaseFile ->
                            ZipInputStream(zip.getInputStream(entry)).use { inner -> unzipInto(inner, databaseDir) }

                        entry.name.startsWith("${BackupManifest.IMAGES_FOLDER}/") ->
                            zip.getInputStream(entry).use { input ->
                                safeFile(imagesDir, entry.name.removePrefix("${BackupManifest.IMAGES_FOLDER}/"))
                                    .apply { parentFile.mkdirs() }
                                    .outputStream().use { input.copyTo(it) }
                            }
                    }
                }
            }
        }

        private fun unzipInto(zip: ZipInputStream, target: File) {
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    safeFile(target, entry.name).apply { parentFile.mkdirs() }.outputStream().use { zip.copyTo(it) }
                }
            }
        }

        /** [name] inside [root], refusing anything that would land outside it */
        private fun safeFile(root: File, name: String): File {
            val file = File(root, name).canonicalFile
            check(file.toPath().startsWith(root.canonicalFile.toPath())) { "$name is outside ${root.name}" }
            return file
        }
    }
}

/** Why an archive can't be restored, with a message for the admin */
class RestoreException(message: String) : Exception(message)
