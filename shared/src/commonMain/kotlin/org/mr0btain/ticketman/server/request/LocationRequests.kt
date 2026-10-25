package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Sends a ticket to another location, like a device taken across town for a specialist */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class MoveTicketRequest(val locationIdentifier: Uuid)

/** Which location a station is at */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class StationLocationRequest(val locationIdentifier: Uuid)
