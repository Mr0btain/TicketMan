package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

/**
 * One row per logged in device. The refresh token keeps the session alive, the session id ties access tokens to it
 */
object TokenTable: IntIdTable("token") {
    /** The user's identifier */
    val identifier = text("identifier")
    val token = text("token")

    // Columns added for sessions per device, rows from before these existed are dropped on start
    val sessionId = text("session_id").default("")
    val deviceId = text("device_id").default("")
    val deviceName = text("device_name").default("")
    val kind = text("kind").default("PASSWORD")
    val createdAt = long("created_at").default(0)
    val lastUsedAt = long("last_used_at").default(0)
}
