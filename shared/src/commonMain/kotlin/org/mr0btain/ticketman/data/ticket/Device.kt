package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem

/**
 * Data class for holding data about a specific device type
 */

@Serializable
data class Device(
    val manufacturer: String,
    val model: String
): SubItem

fun Device.toNameString(): String = "$manufacturer $model"