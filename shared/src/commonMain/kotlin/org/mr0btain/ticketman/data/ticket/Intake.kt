package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable

/** How something on the intake checklist was at check in */
@Serializable
enum class CheckResult(val label: String) {
    WORKS("Works"),
    FAULTY("Faulty"),
    NOT_TESTED("Not tested")
}

/** One thing checked at check in, like "Cameras", and how it was */
@Serializable
data class ChecklistItem(
    val name: String,
    val result: CheckResult = CheckResult.NOT_TESTED
)

/** The shop's own list of what to check on one kind of device */
@Serializable
data class IntakeChecklist(
    val kind: DeviceKind,
    val items: List<String>
)

object IntakeChecklists {
    /** What's checked on each kind of device when the shop hasn't made its own list */
    val defaults: Map<DeviceKind, List<String>> = mapOf(
        DeviceKind.PHONE to listOf("Powers on", "Screen and touch", "Front camera", "Back camera", "Speakers and mic", "Charging port", "Buttons", "Face or fingerprint unlock", "Wi-Fi and cellular"),
        DeviceKind.TABLET to listOf("Powers on", "Screen and touch", "Cameras", "Speakers and mic", "Charging port", "Buttons", "Wi-Fi"),
        DeviceKind.LAPTOP to listOf("Powers on", "Screen", "Keyboard", "Trackpad", "Ports", "Battery charges", "Wi-Fi", "Webcam", "Speakers"),
        DeviceKind.DESKTOP to listOf("Powers on", "Boots", "Ports", "Fans", "Wi-Fi or network"),
        DeviceKind.CONSOLE to listOf("Powers on", "Video out", "Controllers pair", "Disc drive", "Network"),
        DeviceKind.WATCH to listOf("Powers on", "Screen and touch", "Buttons and crown", "Charges", "Pairs with phone"),
        DeviceKind.OTHER to listOf("Powers on")
    )

    /** The shop's list for [kind] when it made one, the default otherwise. An empty list of its own means none */
    fun itemsFor(kind: DeviceKind, shopLists: List<IntakeChecklist>): List<String> =
        shopLists.firstOrNull { it.kind == kind }?.items ?: defaults[kind].orEmpty()

    /** Most characters a passcode can have */
    const val MAX_PASSCODE = 64
}
