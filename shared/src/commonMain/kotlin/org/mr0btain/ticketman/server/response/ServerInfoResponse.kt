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
    val lastRestore: RestoreRecord? = null,
    /** Behind a reverse proxy (`PROXY=on`), which does HTTPS. False for the server doing it itself */
    val proxy: Boolean = false,
    /** Addresses and ranges whose `X-Forwarded-For` the server believes (`TRUSTED_PROXIES`), empty when it ignores the header */
    val trustedProxies: List<String> = emptyList(),
    /**
     * The address the server sees for the device asking. Behind a working proxy that's the device's own; the proxy's
     * address for every device means the proxy isn't in [trustedProxies] or doesn't send `X-Forwarded-For`
     */
    val clientAddress: String = "",
    /** Whether [clientAddress] came from a trusted proxy's forwarded headers */
    val forwarded: Boolean = false,
    /** The time zone "today", due dates and the yearly number rollover use, like `America/New_York` */
    val timeZone: String = ""
)
