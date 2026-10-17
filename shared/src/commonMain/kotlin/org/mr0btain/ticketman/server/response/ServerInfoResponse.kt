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
    val javaVersion: String,
    val operatingSystem: String,
    val developmentMode: Boolean
)
