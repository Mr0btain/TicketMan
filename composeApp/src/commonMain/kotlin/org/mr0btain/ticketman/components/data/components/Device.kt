package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable

/**
 * Data class for holding data about a specific device type
 */

@Serializable
data class Device(
    val manufacturer: String,
    val model: String
)