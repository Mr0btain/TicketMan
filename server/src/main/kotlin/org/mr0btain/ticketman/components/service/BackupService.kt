package org.mr0btain.ticketman.components.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.ImageDatabase
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.image.ImageStore
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.server.response.BackupManifest
import org.mr0btain.ticketman.server.response.BackupTicket
import java.io.BufferedOutputStream
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Builds backup archives on disk and hands out short lived download links for them, so a backup of any size is
 * streamed from a file instead of being held in memory
 *
 * The archive is a zip of `manifest.json`, `database.zip` (H2's online backup) and, unless left out, `images/`.
 * The database is copied first: photos are only removed a day after they're deleted, so every photo the copied
 * database points at is still on disk when they're copied after
 * @param backupsFolder Where finished archives wait to be downloaded
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class BackupService(
    private val database: LocalDatabase,
    private val images: ImageDatabase,
    private val imageStore: ImageStore,
    private val backupsFolder: File
) {
    private val TAG = "BackupService"
    private val PREPARE_TAG = "$TAG:Prepare"
    private val SWEEP_TAG = "$TAG:Sweep"

    private val random = SecureRandom()
    private val json = Json { prettyPrint = true }

    // One at a time, a backup reads every photo on disk
    private val building = Mutex()

    private data class Prepared(val file: File, val fileName: String, val token: String, val expiresAt: Long)

    // Lost on restart, which is fine: the sweep then removes the files
    private val prepared = ConcurrentHashMap<String, Prepared>()

    init {
        backupsFolder.mkdirs()
    }

    /**
     * Builds an archive and returns how to download it, null if it couldn't be made
     */
    suspend fun prepare(includeImages: Boolean): BackupTicket? = building.withLock {
        withContext(Dispatchers.IO) { build(includeImages) }
    }

    private fun build(includeImages: Boolean): BackupTicket? {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = Uuid.generateV7().toString()
        val work = File(backupsFolder, ".work-$id")
        val archive = File(backupsFolder, "$id.zip")

        return try {
            work.mkdirs()

            // H2 won't write over an existing file
            val databaseCopy = File(work, BackupManifest.DATABASE_FILE)
            if (!database.backupTo(databaseCopy)) return null

            val imageFiles = if (includeImages) imageFiles() else emptyList()
            val manifest = BackupManifest(
                serverVersion = AdminService.SERVER_VERSION,
                createdAt = now,
                includesImages = includeImages,
                imageCount = imageFiles.map { it.first.substringBeforeLast('/') }.distinct().size,
                imageBytes = imageFiles.sumOf { it.second.length() }
            )

            val digest = MessageDigest.getInstance("SHA-256")

            ZipOutputStream(BufferedOutputStream(DigestOutputStream(archive.outputStream(), digest), BUFFER_BYTES)).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED)
                zip.putNextEntry(ZipEntry(BackupManifest.FILE_NAME))
                zip.write(json.encodeToString(manifest).encodeToByteArray())
                zip.closeEntry()

                // The database copy and the JPEGs are already compressed, squeezing them again only costs time
                zip.setLevel(Deflater.NO_COMPRESSION)
                zip.addFile(BackupManifest.DATABASE_FILE, databaseCopy)
                imageFiles.forEach { (path, file) -> zip.addFile("${BackupManifest.IMAGES_FOLDER}/$path", file) }
            }

            val token = ByteArray(TOKEN_BYTES).also { random.nextBytes(it) }
                .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
            val expiresAt = now + DOWNLOAD_WINDOW.inWholeMilliseconds

            val fileName = "ticketman-backup-$now${if (includeImages) "" else "-database"}.zip"

            prepared[id] = Prepared(archive, fileName, token, expiresAt)
            AppLogger.m(PREPARE_TAG, "Built $id, ${archive.length()} bytes, ${manifest.imageCount} photos")

            BackupTicket(
                id = id,
                fileName = fileName,
                sizeBytes = archive.length(),
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                downloadPath = "$DOWNLOAD_ROUTE/$id?token=$token",
                expiresAt = expiresAt,
                includesImages = includeImages
            )
        } catch (ex: Exception) {
            AppLogger.e(PREPARE_TAG, SimpleResult.FAIL, ex)
            archive.delete()
            null
        } finally {
            work.deleteRecursively()
        }
    }

    /** Every photo file worth keeping, as its path inside the image store and the file */
    private fun imageFiles(): List<Pair<String, File>> =
        images.liveIdentifiers().flatMap { identifier ->
            ImageVariant.entries.mapNotNull { variant ->
                imageStore.fileFor(identifier, variant).takeIf { it.isFile }?.let { file ->
                    "${imageStore.relativeFolder(identifier)}/${file.name}" to file
                }
            }
        }

    private fun ZipOutputStream.addFile(name: String, file: File) {
        putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
        file.inputStream().use { it.copyTo(this, BUFFER_BYTES) }
        closeEntry()
    }

    /**
     * The archive for a download link and the name to save it as, null if the id or token is wrong or it has expired. The link can be used
     * more than once until then, so an interrupted download can be resumed with a Range request
     */
    fun fileFor(id: String, token: String?, now: Long = Clock.System.now().toEpochMilliseconds()): Pair<File, String>? {
        val backup = prepared[id] ?: return null
        if (token == null || now > backup.expiresAt) return null

        // Constant time, so the token can't be guessed a character at a time
        if (!MessageDigest.isEqual(backup.token.encodeToByteArray(), token.encodeToByteArray())) return null

        return backup.file.takeIf { it.isFile }?.let { it to backup.fileName }
    }

    /**
     * Removes archives whose link has expired, and any left from before a restart or a crash mid build. Skipped
     * while a backup is being built, since its files aren't known yet
     * @return How many were removed
     */
    fun sweep(now: Long = Clock.System.now().toEpochMilliseconds()): Int {
        if (!building.tryLock()) return 0

        return try {
            prepared.entries.removeIf { (_, backup) ->
                (now > backup.expiresAt).also { expired -> if (expired) backup.file.delete() }
            }

            val known = prepared.values.map { it.file.name }.toSet()
            val stale = backupsFolder.listFiles().orEmpty().filter { it.name !in known }
            stale.forEach { it.deleteRecursively() }

            if (stale.isNotEmpty()) AppLogger.m(SWEEP_TAG, "Removed ${stale.size} old backups")
            stale.size
        } finally {
            building.unlock()
        }
    }

    companion object {
        const val DOWNLOAD_ROUTE = "/api/admin/backup/download"

        /** How long a download link works */
        val DOWNLOAD_WINDOW = 30.minutes

        private const val TOKEN_BYTES = 32
        private const val BUFFER_BYTES = 256 * 1024
    }
}
