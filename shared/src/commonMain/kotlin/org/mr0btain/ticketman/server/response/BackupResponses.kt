package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

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
 * `manifest.json` inside every backup archive, next to `database.zip` (H2's own backup) and `images/`
 * (`images/<aa>/<id>/<variant>.jpg`, the same layout as on the server)
 */
@Serializable
data class BackupManifest(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val serverVersion: String,
    val createdAt: Long,
    val databaseFile: String = DATABASE_FILE,
    val includesImages: Boolean,
    val imageCount: Int,
    val imageBytes: Long
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val FILE_NAME = "manifest.json"
        const val DATABASE_FILE = "database.zip"
        const val IMAGES_FOLDER = "images"
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
