package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

@Serializable
data class ServerInfoResponse(
    val name: String,
    val version: String,
    val startedAt: Long,
    val uptimeMillis: Long,
    val dataPath: String,
    val databaseSizeBytes: Long,
    /** Photo files on disk, kept outside the database */
    val imagesSizeBytes: Long = 0,
    val javaVersion: String,
    val operatingSystem: String,
    val developmentMode: Boolean,
    /** Null when HTTPS is off */
    val certificate: ServerCertificateInfo? = null,
    /** Null when `BACKUP_DIR` isn't set */
    val scheduledBackup: ScheduledBackupInfo? = null,
    /** The last restore applied, while its set aside data is still kept */
    val lastRestore: RestoreRecord? = null
)
