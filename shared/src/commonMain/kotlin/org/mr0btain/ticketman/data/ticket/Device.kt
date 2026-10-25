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
    val serial: String = "",
    /** What sort of device, which picks the intake checklist */
    val kind: DeviceKind = DeviceKind.OTHER
): SubItem {
    override fun toString(): String {
        return "$manufacturer $model"
    }
}

/** Sorts of device, for checklists and icons */
@Serializable
enum class DeviceKind(val label: String) {
    PHONE("Phone"),
    TABLET("Tablet"),
    LAPTOP("Laptop"),
    DESKTOP("Desktop"),
    CONSOLE("Console"),
    WATCH("Watch"),
    OTHER("Other")
}
