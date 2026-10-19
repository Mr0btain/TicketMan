package org.mr0btain.ticketman.components.service

import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.server.response.BackupManifest
import org.mr0btain.ticketman.components.tls.ServerTls
import org.mr0btain.ticketman.server.response.PendingRestore
import org.mr0btain.ticketman.server.response.RestoreRecord
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Restoring from a backup archive, in two halves:
 * 1. [stage] while running: checks an uploaded archive and parks it in `<serverDir>/restore/`
 * 2. [applyPending] on the next start, before the database is opened: moves `database/` and `images/` (and `tls/`
 *    when the archive has a certificate) aside to `pre-restore-<time>/`, kept for [LEFTOVER_AGE] so a restore can be
 *    undone by hand, and unpacks the archive in their place
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

    /** The last restore applied, while its set aside data is still kept */
    fun lastRestore(): RestoreRecord? =
        File(restoreDir, LAST_INFO).takeIf { it.isFile }?.let { file ->
            runCatching { json.decodeFromString<RestoreRecord>(file.readText()) }.getOrNull()
        }

    /**
     * Deletes what restores leave behind once it's older than [maxAge]: `pre-restore-<time>` folders, and
     * `restore/failed-<time>.zip` archives that couldn't be applied. Called by the hourly sweep
     * @return How many were removed
     */
    fun sweepLeftovers(maxAge: Duration = LEFTOVER_AGE, now: Long = Clock.System.now().toEpochMilliseconds()): Int {
        val cutoff = now - maxAge.inWholeMilliseconds

        fun timeIn(name: String, prefix: String, suffix: String = ""): Long? =
            name.takeIf { it.startsWith(prefix) && it.endsWith(suffix) }?.removePrefix(prefix)?.removeSuffix(suffix)?.toLongOrNull()

        val old = serverDir.listFiles().orEmpty().filter { it.isDirectory && (timeIn(it.name, SET_ASIDE_PREFIX) ?: Long.MAX_VALUE) < cutoff } +
            restoreDir.listFiles().orEmpty().filter { it.isFile && (timeIn(it.name, FAILED_PREFIX, ".zip") ?: Long.MAX_VALUE) < cutoff }

        val removed = old.count { it.deleteRecursively() }

        // The record says where the old data went, which is now gone
        lastRestore()?.let { record ->
            if (!File(serverDir, record.setAsideFolder).exists()) File(restoreDir, LAST_INFO).delete()
        }

        if (removed > 0) AppLogger.m(SWEEP_TAG, "Removed $removed old restore leftovers")
        return removed
    }

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
        private const val SWEEP_TAG = "$TAG:Sweep"

        const val RESTORE_FOLDER = "restore"
        const val PENDING_ARCHIVE = "pending.zip"
        const val PENDING_INFO = "pending.json"
        const val LAST_INFO = "last.json"
        const val DATABASE_FOLDER = "database"
        const val IMAGES_FOLDER = "images"
        const val TLS_FOLDER = "tls"
        const val SET_ASIDE_PREFIX = "pre-restore-"
        const val FAILED_PREFIX = "failed-"

        /** How long the data a restore replaced, and archives that failed to restore, are kept */
        val LEFTOVER_AGE = 30.days

        private val CERTIFICATE_FILES = setOf(ServerTls.KEYSTORE_FILE, ServerTls.PASSWORD_FILE)

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Only the manifest, the database copy, photo files under images/ and the certificate's two files under
         * tls/, with no way out of their folder
         */
        private fun isAllowed(name: String, manifest: BackupManifest): Boolean {
            if (name.startsWith("/") || name.contains('\\') || name.split('/').any { it == ".." }) return false

            return name == BackupManifest.FILE_NAME ||
                name == manifest.databaseFile ||
                name.startsWith("${BackupManifest.IMAGES_FOLDER}/") ||
                (manifest.includesCertificate && name.removePrefix("${BackupManifest.TLS_FOLDER}/") in CERTIFICATE_FILES &&
                    name.startsWith("${BackupManifest.TLS_FOLDER}/"))
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

            val manifest = try {
                readManifest(archive)
            } catch (ex: Exception) {
                AppLogger.e(APPLY_TAG, "The waiting restore's manifest couldn't be read", ex)
                null
            }

            val now = System.currentTimeMillis()
            // The certificate is only replaced when the backup has one, a server using a supplied one keeps its own
            val folders = listOfNotNull(DATABASE_FOLDER, IMAGES_FOLDER, TLS_FOLDER.takeIf { manifest?.includesCertificate == true })
            val setAside = File(serverDir, "$SET_ASIDE_PREFIX$now").apply { mkdirs() }

            return try {
                checkNotNull(manifest) { "No manifest" }

                folders.forEach { name ->
                    val folder = File(serverDir, name)
                    if (folder.exists()) check(folder.renameTo(File(setAside, name))) { "Couldn't move $name aside" }
                }

                unpack(archive, manifest, serverDir)

                archive.delete()
                File(restoreDir, PENDING_INFO).delete()
                File(restoreDir, LAST_INFO).writeText(json.encodeToString(RestoreRecord(manifest, now, setAside.name)))
                AppLogger.m(APPLY_TAG, "Restored from backup, the old data is in ${setAside.name}")
                if (!manifest.includesImages) AppLogger.i(APPLY_TAG, "That backup had no photos, photos on records won't load")
                true
            } catch (ex: Exception) {
                AppLogger.e(APPLY_TAG, "Restore failed, putting the old data back", ex)

                folders.forEach { name ->
                    File(serverDir, name).deleteRecursively()
                    File(setAside, name).renameTo(File(serverDir, name))
                }
                setAside.deleteRecursively()

                // Don't try again every start, keep it for a look
                archive.renameTo(File(restoreDir, "$FAILED_PREFIX$now.zip"))
                File(restoreDir, PENDING_INFO).delete()
                false
            }
        }

        private fun readManifest(archive: File): BackupManifest = ZipFile(archive).use { zip ->
            json.decodeFromString<BackupManifest>(zip.getInputStream(zip.getEntry(BackupManifest.FILE_NAME)).readBytes().decodeToString())
        }

        private fun unpack(archive: File, manifest: BackupManifest, serverDir: File) {
            val databaseDir = File(serverDir, DATABASE_FOLDER).apply { mkdirs() }
            val imagesDir = File(serverDir, IMAGES_FOLDER).apply { mkdirs() }
            val tlsDir = File(serverDir, TLS_FOLDER)

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

                        // The key and its password, only the server's account should read them
                        entry.name.startsWith("${BackupManifest.TLS_FOLDER}/") ->
                            zip.getInputStream(entry).use { input ->
                                safeFile(tlsDir, entry.name.removePrefix("${BackupManifest.TLS_FOLDER}/"))
                                    .apply { parentFile.mkdirs() }
                                    .also { file -> file.outputStream().use { input.copyTo(it) } }
                                    .also { ServerTls.ownerOnly(it) }
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
