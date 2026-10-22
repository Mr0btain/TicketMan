package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

/** Where a backup started with `POST /api/admin/backup/start` is up to */
@Serializable
enum class BackupJobState { RUNNING, DONE, FAILED }

/**
 * A backup being built in the background, so the request that starts it answers straight away. A reverse proxy
 * gives up on a request the server is quiet on for long (nginx after 60s), and a backup with photos can take longer
 * @param ticket Set once it's [BackupJobState.DONE]
 * @param error Why it [BackupJobState.FAILED]
 */
@Serializable
data class BackupJob(
    val id: String,
    val state: BackupJobState,
    val startedAt: Long,
    val ticket: BackupTicket? = null,
    val error: String? = null
)

/**
 * A built backup waiting to be downloaded. The download needs no login, the token in [downloadPath] is the key,
 * so a browser can save it straight to disk. It stops working at [expiresAt]
 * @param downloadPath Path on the server, with the token, like `/api/admin/backup/download/{id}?token=...`
 * @param sha256 Hash of the whole archive, to check a copy against
 */
@Serializable
data class BackupTicket(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val downloadPath: String,
    val expiresAt: Long,
    val includesImages: Boolean
)

/**
 * `manifest.json` inside every backup archive, next to `database.zip` (H2's own backup), `images/`
 * (`images/<aa>/<id>/<variant>.jpg`, the same layout as on the server) and, only in archives from before the
 * server stopped doing HTTPS itself, `tls/` (the certificate it made, skipped on restore)
 * @param includesCertificate Whether `tls/` is in it. Never set on new archives
 */
@Serializable
data class BackupManifest(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val serverVersion: String,
    val createdAt: Long,
    val databaseFile: String = DATABASE_FILE,
    val includesImages: Boolean,
    val imageCount: Int,
    val imageBytes: Long,
    val includesCertificate: Boolean = false
) {
    companion object {
        /** 2 added `tls/`, which new archives no longer have */
        const val CURRENT_FORMAT_VERSION = 2
        const val FILE_NAME = "manifest.json"
        const val DATABASE_FILE = "database.zip"
        const val IMAGES_FOLDER = "images"
        const val TLS_FOLDER = "tls"
    }
}

/**
 * A backup uploaded to restore from. It's applied the next time the server starts, when the current data is
 * moved aside to a `pre-restore-<time>` folder first
 * @param stagedAt Epoch millis it was uploaded
 */
@Serializable
data class PendingRestore(
    val manifest: BackupManifest,
    val sizeBytes: Long,
    val stagedAt: Long
)

/**
 * The last restore applied, so the admin screen can say so. Kept as long as its `pre-restore-<time>` folder is
 * @param restoredAt Epoch millis it was applied
 * @param setAsideFolder The `pre-restore-<time>` folder the old data was moved to
 */
@Serializable
data class RestoreRecord(
    val manifest: BackupManifest,
    val restoredAt: Long,
    val setAsideFolder: String
)

/**
 * The daily backup set up with `BACKUP_DIR`
 * @param folder Where the backups are written, on the server
 * @param keep How many are kept, older ones are deleted
 * @param lastBackupAt Epoch millis of the newest one in [folder]
 * @param lastFailedAt Epoch millis the last try failed, null if it hasn't since the server started
 */
@Serializable
data class ScheduledBackupInfo(
    val folder: String,
    val keep: Int,
    val includesImages: Boolean,
    val lastBackupAt: Long? = null,
    val lastSizeBytes: Long? = null,
    val lastFailedAt: Long? = null
)
