package org.mr0btain.ticketman.components.data.local

/**
 * How often open screens check the server for changes made on other devices, a per device setting
 * @param millis Time between checks, 0 for never
 */
enum class UpdateInterval(val millis: Long, val label: String) {
    OFF(0, "Off"),
    SECONDS_15(15_000, "15 seconds"),
    SECONDS_30(30_000, "30 seconds"),
    MINUTE_1(60_000, "1 minute"),
    MINUTES_5(300_000, "5 minutes");

    companion object {
        val DEFAULT = SECONDS_30

        /** The saved one, the default for anything unknown */
        fun byName(name: String): UpdateInterval = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
