package org.mr0btain.ticketman.data.station

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * A device an admin has allowed staff to PIN in at, like the front counter
 * @param requestedAt Epoch millis
 * @param approvedAt Epoch millis, null until approved
 */
@Serializable
data class Station(
    val identifier: Uuid,
    val name: String,
    val deviceId: String,
    val status: StationStatus,
    val requestedBy: String,
    val requestedAt: Long,
    val approvedBy: String? = null,
    val approvedAt: Long? = null
)

@Serializable
enum class StationStatus {
    PENDING,
    APPROVED
}

/**
 * Proves a request comes from the station. The secret is handed out once when the station is registered
 */
@Serializable
data class StationCredentials(
    val stationId: Uuid,
    val secret: String
)

/** Someone who can PIN in at a station */
@Serializable
data class StaffMember(
    val identifier: Uuid,
    val username: String,
    val name: String
)
