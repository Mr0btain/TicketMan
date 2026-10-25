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
    val approvedAt: Long? = null,
    /** Locks the device to the counter with PIN sign in, only for approved stations */
    val kioskEnabled: Boolean = false,
    /** Seconds with nothing touched before a kiosk locks */
    val idleTimeoutSeconds: Int = DEFAULT_IDLE_TIMEOUT_SECONDS,
    /** The location it's at, which work done on it belongs to. Set by an admin, null for the main location */
    val locationIdentifier: Uuid? = null
) {
    companion object {
        const val DEFAULT_IDLE_TIMEOUT_SECONDS = 120
        val IDLE_TIMEOUT_RANGE = 30..3_600
    }
}

/** Turns kiosk mode on or off for a station */
@Serializable
data class KioskSettings(
    val enabled: Boolean,
    val idleTimeoutSeconds: Int = Station.DEFAULT_IDLE_TIMEOUT_SECONDS
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
