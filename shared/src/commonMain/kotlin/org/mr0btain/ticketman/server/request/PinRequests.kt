package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.station.StationCredentials
import kotlin.uuid.Uuid

/** Switching to a user at a station with their PIN */
@Serializable
data class PinLoginRequest(
    val station: StationCredentials,
    val userIdentifier: Uuid,
    val pin: String
)

/** Changing your own PIN, needs your password */
@Serializable
data class SetPinRequest(
    val currentPassword: String,
    val pin: String
)

/** Asking for this device to become a station */
@Serializable
data class StationRegistrationRequest(
    val deviceId: String,
    val name: String
)
