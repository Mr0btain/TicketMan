package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem

/**
 * Data class for holding data about a specific device type
 */

@Serializable
data class Device(
    val manufacturer: String = "",
    val model: String = "",
    val serial: String = ""
): SubItem {
    override fun toString(): String {
        return "$manufacturer $model"
    }

    companion object {
        fun fromString(device: String): Device {
            val result = device.split(" ").map { it.trim() }

            return Device(
                manufacturer = result.getOrElse(0, {""}),
                model = result.getOrElse(1, {""})
            )
        }
    }
}
