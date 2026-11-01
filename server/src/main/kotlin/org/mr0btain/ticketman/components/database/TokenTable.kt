package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

/**
 * One row per logged in device. The refresh token keeps the session alive, the session id ties access tokens to it
 */
object TokenTable: IntIdTable("token") {
    /** The user's identifier */
    val identifier = text("identifier")
    /** The SHA-256 of the current refresh token, so a copy of the database or a backup holds nothing that logs in */
    val token = text("token")

    // Columns added for sessions per device, rows from before these existed are dropped on start
    val sessionId = text("session_id").default("")
    val deviceId = text("device_id").default("")
    val deviceName = text("device_name").default("")
    val kind = text("kind").default("PASSWORD")
    val createdAt = long("created_at").default(0)
    val lastUsedAt = long("last_used_at").default(0)

    /** Epoch millis the refresh token runs out, 0 for rows from before this was kept (filled in on start) */
    val expiresAt = long("expires_at").default(0)
    /** A kiosk PIN session ends after this long unused, 0 for none */
    val idleTimeoutSeconds = integer("idle_timeout_seconds").default(0)

    /** The hash of the refresh token [token] replaced and when, see `UserService.refreshToken` */
    val previousToken = text("previous_token").nullable()
    val rotatedAt = long("rotated_at").default(0)
}
