package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationCredentials

/**
 * Sent once when a device registers as a station. The secret can't be fetched again, only re-registered
 */
@Serializable
data class StationRegistrationResponse(
    val station: Station,
    val credentials: StationCredentials
)
