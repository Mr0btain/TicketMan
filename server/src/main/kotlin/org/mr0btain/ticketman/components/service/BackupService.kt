package org.mr0btain.ticketman.components.service

import io.ktor.server.config.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.ImageDatabase
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.image.ImageStore
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.server.AdminPaths
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.fill
import org.mr0btain.ticketman.server.response.BackupJob
import org.mr0btain.ticketman.server.response.BackupJobState
import org.mr0btain.ticketman.server.response.BackupManifest
import org.mr0btain.ticketman.server.response.BackupTicket
import org.mr0btain.ticketman.server.response.ScheduledBackupInfo
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Builds backup archives on disk and hands out short lived download links for them, so a backup of any size is
 * streamed from a file instead of being held in memory. Also makes the daily backup when `BACKUP_DIR` is set
 *
 * The archive is a zip of `manifest.json`, `database.zip` (H2's online backup) and, unless left out, `images/`. The database is copied first: photos are only removed a day
 * after they're deleted, so every photo the copied database points at is still on disk when they're copied after
 * @param backupsFolder Where finished archives wait to be downloaded
 * @param schedule The daily backup, off when its folder is blank
 * @param auditLog Where scheduled backups are logged
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class BackupService(
    private val database: LocalDatabase,
    private val images: ImageDatabase,
    private val imageStore: ImageStore,
    private val backupsFolder: File,
    private val schedule: BackupSchedule = BackupSchedule(),
    private val auditLog: AuditLogDatabase? = null
) {
    private val TAG = "BackupService"
    private val PREPARE_TAG = "$TAG:Prepare"
    private val SWEEP_TAG = "$TAG:Sweep"
    private val SCHEDULED_TAG = "$TAG:Scheduled"

    private val random = SecureRandom()
    private val json = Json { prettyPrint = true }

    // One at a time, a backup reads every photo on disk
    private val building = Mutex()

    private data class Prepared(val file: File, val fileName: String, val token: String, val expiresAt: Long)

    // Lost on restart, which is fine: the sweep then removes the files
    private val prepared = ConcurrentHashMap<String, Prepared>()

    // Backups started with start(), also lost on restart
    private val jobs = ConcurrentHashMap<String, BackupJob>()
    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastScheduledFailure: Long? = null

    init {
        backupsFolder.mkdirs()

        if (schedule.enabled && File(schedule.folder).canonicalFile == backupsFolder.canonicalFile) {
            AppLogger.e(SCHEDULED_TAG, "BACKUP_DIR can't be the server's own backups folder, the sweep empties it. Daily backups are off")
        }
    }

    /**
     * Builds an archive and returns how to download it, null if it couldn't be made
     */
    suspend fun prepare(includeImages: Boolean): BackupTicket? = building.withLock {
        withContext(Dispatchers.IO) { build(includeImages) }
    }

    /**
     * Starts building an archive in the background and returns straight away, for [job] to be asked after. Waits its
     * turn behind any backup already being built
     * @param onFinished Called with the ticket, or null if it couldn't be made, like for the audit log
     */
    fun start(includeImages: Boolean, onFinished: (BackupTicket?) -> Unit = {}): BackupJob {
        val now = Clock.System.now().toEpochMilliseconds()

        // Finished ones are only worth asking about while their download lasts
        jobs.values.removeIf { it.state != BackupJobState.RUNNING && it.startedAt < now - JOB_KEPT.inWholeMilliseconds }

        val job = BackupJob(id = Uuid.generateV7().toString(), state = BackupJobState.RUNNING, startedAt = now)
        jobs[job.id] = job

        jobScope.launch {
            val ticket = try {
                prepare(includeImages)
            } catch (ex: Exception) {
                AppLogger.e(PREPARE_TAG, SimpleResult.FAIL, ex)
                null
            }

            jobs[job.id] = if (ticket != null) job.copy(state = BackupJobState.DONE, ticket = ticket)
            else job.copy(state = BackupJobState.FAILED, error = "The backup couldn't be made, check the server log")
            onFinished(ticket)
        }

        return job
    }

    /** A backup [start]ed earlier, null if there's no such one or it finished too long ago */
    fun job(id: String): BackupJob? = jobs[id]

    private fun build(includeImages: Boolean): BackupTicket? {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = Uuid.generateV7().toString()
        val archive = File(backupsFolder, "$id.zip")

        return try {
            val (manifest, sha256) = writeArchive(archive, includeImages, now) ?: return null

            val token = ByteArray(TOKEN_BYTES).also { random.nextBytes(it) }
                .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
            val expiresAt = now + DOWNLOAD_WINDOW.inWholeMilliseconds

            val fileName = archiveName(now, includeImages)

            prepared[id] = Prepared(archive, fileName, token, expiresAt)
            AppLogger.m(PREPARE_TAG, "Built $id, ${archive.length()} bytes, ${manifest.imageCount} photos")

            BackupTicket(
                id = id,
                fileName = fileName,
                sizeBytes = archive.length(),
                sha256 = sha256,
                downloadPath = "${ApiRoutes.ADMIN}${AdminPaths.BACKUP_DOWNLOAD.fill(id)}?token=$token",
                expiresAt = expiresAt,
                includesImages = includeImages
            )
        } catch (ex: Exception) {
            AppLogger.e(PREPARE_TAG, SimpleResult.FAIL, ex)
            archive.delete()
            null
        }
    }

    /**
     * Writes a whole backup to [archive]
     * @return The manifest written and the archive's SHA-256, or null if the database couldn't be copied. Throws
     * if writing failed
     */
    private fun writeArchive(archive: File, includeImages: Boolean, now: Long): Pair<BackupManifest, String>? {
        val work = File(archive.parentFile, ".work-${Uuid.generateV7()}")

        try {
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

            return manifest to digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            work.deleteRecursively()
        }
    }

    /**
     * Makes the daily backup if the newest one in the folder is a day old, then deletes all but the newest
     * [BackupSchedule.keep]. Called by the hourly sweep
     * @return Whether one was made
     */
    suspend fun runSchedule(now: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        if (!schedule.enabled) return false

        val folder = File(schedule.folder)
        if (folder.canonicalFile == backupsFolder.canonicalFile) return false
        val last = scheduledBackups(folder).firstOrNull()?.let { archiveTime(it) }
        if (last != null && now - last < SCHEDULE_INTERVAL.inWholeMilliseconds) return false

        val made = building.withLock {
            withContext(Dispatchers.IO) { writeScheduled(folder, now) }
        }

        if (made != null) {
            lastScheduledFailure = null
            val contents = if (schedule.includeImages) "database and photos" else "database only"
            auditLog?.log(null, AuditAction.BACKUP, "database", details = "Daily backup, $contents, ${made.length()} bytes", actorName = SCHEDULE_ACTOR)
        } else {
            lastScheduledFailure = now
        }

        scheduledBackups(folder).drop(schedule.keep).forEach { old ->
            if (old.delete()) AppLogger.m(SCHEDULED_TAG, "Removed old backup ${old.name}")
        }

        return made != null
    }

    // Written under a temporary name, so a half written one never counts as the newest
    private fun writeScheduled(folder: File, now: Long): File? {
        val archive = File(folder, archiveName(now, schedule.includeImages))
        val partial = File(folder, ".${archive.name}.part")

        return try {
            folder.mkdirs()
            writeArchive(partial, schedule.includeImages, now) ?: return null
            check(partial.renameTo(archive)) { "Couldn't rename ${partial.name}" }

            AppLogger.m(SCHEDULED_TAG, "Made ${archive.absolutePath}, ${archive.length()} bytes")
            archive
        } catch (ex: Exception) {
            AppLogger.e(SCHEDULED_TAG, SimpleResult.FAIL, ex)
            null
        } finally {
            partial.delete()
        }
    }

    /** The daily backup's settings and how it last went, null when it's off */
    fun scheduleInfo(): ScheduledBackupInfo? {
        if (!schedule.enabled) return null

        val newest = scheduledBackups(File(schedule.folder)).firstOrNull()
        return ScheduledBackupInfo(
            folder = File(schedule.folder).absolutePath,
            keep = schedule.keep,
            includesImages = schedule.includeImages,
            lastBackupAt = newest?.let { archiveTime(it) },
            lastSizeBytes = newest?.length(),
            lastFailedAt = lastScheduledFailure
        )
    }

    // Backups this server made in the folder, newest first. Anything else in there is left alone
    private fun scheduledBackups(folder: File): List<File> =
        folder.listFiles().orEmpty()
            .filter { it.isFile && archiveTime(it) != null }
            .sortedByDescending { archiveTime(it) }

    private fun archiveTime(file: File): Long? = ARCHIVE_NAME.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()

    private fun archiveName(time: Long, includeImages: Boolean) =
        "ticketman-backup-$time${if (includeImages) "" else "-database"}.zip"

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
     * The archive for a download link and the name to save it as, null if the id or token is wrong or it has
     * expired. The link is a login in itself and ends up in browser history, so once it's used it only lasts
     * [RESUME_WINDOW] past each request: long enough to resume a dropped download with a Range request, not long
     * enough to be useful to someone finding it later
     */
    fun fileFor(id: String, token: String?, now: Long = Clock.System.now().toEpochMilliseconds()): Pair<File, String>? {
        val backup = prepared[id] ?: return null
        if (token == null || now > backup.expiresAt) return null

        // Constant time, so the token can't be guessed a character at a time
        if (!MessageDigest.isEqual(backup.token.encodeToByteArray(), token.encodeToByteArray())) return null

        val file = backup.file.takeIf { it.isFile } ?: return null
        prepared[id] = backup.copy(expiresAt = now + RESUME_WINDOW.inWholeMilliseconds)

        return file to backup.fileName
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
        /** How long a download link works if nobody uses it */
        val DOWNLOAD_WINDOW = 30.minutes

        /** How long a backup started in the background can be asked about, its build plus its download */
        val JOB_KEPT = DOWNLOAD_WINDOW * 2

        /** How long a download link keeps working after each time it's used */
        val RESUME_WINDOW = 5.minutes

        /** How often the daily backup is made */
        val SCHEDULE_INTERVAL = 1.days - 30.minutes

        /** Name the daily backup is logged under in the audit log */
        const val SCHEDULE_ACTOR = "server"

        private val ARCHIVE_NAME = Regex("""ticketman-backup-(\d+)(-database)?\.zip""")

        private const val TOKEN_BYTES = 32
        private const val BUFFER_BYTES = 256 * 1024
    }
}

/**
 * The daily backup, from application.yaml's `backup` section
 * @param folder Where to write it, blank turns it off
 * @param keep How many to keep, the oldest are deleted after each one
 */
data class BackupSchedule(
    val folder: String = "",
    val keep: Int = DEFAULT_KEEP,
    val includeImages: Boolean = true
) {
    val enabled: Boolean get() = folder.isNotBlank()

    companion object {
        const val DEFAULT_KEEP = 7

        fun from(config: ApplicationConfig): BackupSchedule {
            fun value(key: String) = config.propertyOrNull("backup.$key")?.getString()?.trim().orEmpty()

            return BackupSchedule(
                folder = value("folder"),
                keep = (value("keep").toIntOrNull() ?: DEFAULT_KEEP).coerceAtLeast(1),
                includeImages = value("photos").ifBlank { "true" }.toBoolean()
            )
        }
    }
}
