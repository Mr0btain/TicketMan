package org.mr0btain.ticketman.data.appointment

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.ticket.Device
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Where an appointment is at */
@Serializable
enum class AppointmentStatus(val label: String) {
    BOOKED("Booked"),

    /** They came and it was checked in, see [Appointment.ticketIdentifier]. Only check in sets it */
    ARRIVED("Checked in"),
    NO_SHOW("No show"),
    CANCELLED("Cancelled")
}

/**
 * A time a customer is coming in with a device. Checking it in at the counter makes the ticket and marks it
 * [AppointmentStatus.ARRIVED]
 * @param startsAt Epoch millis
 * @param minutes How long it's booked for
 * @param customerIdentifier The customer, when they're on file. Someone new is just [customerName] and their phone
 * or email until they're checked in
 * @param customerName The customer's name as typed, or theirs when linked (filled in by the server)
 * @param technicianName Filled in by the server from [technicianIdentifier]
 * @param ticketIdentifier The ticket it became, set by check in
 * @param reminderSentAt When the reminder message was queued, null before
 * @param createdBy Username of whoever booked it, set by the server
 * @param createdAt Epoch millis, set by the server
 * @param locationIdentifier The location it's at, set by the server from where it was booked
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Appointment(
    val identifier: Uuid = Uuid.generateV7(),
    val startsAt: Long = 0,
    val minutes: Int = AppointmentRules.DEFAULT_MINUTES,
    val customerIdentifier: Uuid? = null,
    val customerName: String = "",
    val phone: String = "",
    val email: String = "",
    val device: Device = Device(),
    val problem: String = "",
    val notes: String = "",
    val technicianIdentifier: Uuid? = null,
    val technicianName: String = "",
    val status: AppointmentStatus = AppointmentStatus.BOOKED,
    val ticketIdentifier: Uuid? = null,
    val reminderSentAt: Long? = null,
    val createdBy: String = "",
    val createdAt: Long = 0,
    val locationIdentifier: Uuid? = null
) {
    val endsAt: Long
        get() = startsAt + minutes * 60_000L

    /** Still to come or to turn up, so it can be changed or checked in */
    val isOpen: Boolean
        get() = status == AppointmentStatus.BOOKED

    /** What's wrong with it before it's saved, null when it's fine */
    fun problem(): String? = when {
        startsAt <= 0 -> "Pick a day and time"
        minutes !in AppointmentRules.MINUTES -> "An appointment is ${AppointmentRules.MINUTES.first} minutes to ${AppointmentRules.MINUTES.last / 60} hours"
        customerIdentifier == null && customerName.isBlank() -> "Pick a customer or type their name"
        customerName.length > AppointmentRules.MAX_TEXT || problem.length > AppointmentRules.MAX_NOTES || notes.length > AppointmentRules.MAX_NOTES ->
            "That's too long"
        else -> null
    }
}

object AppointmentRules {
    const val DEFAULT_MINUTES = 30
    val MINUTES = 5..8 * 60

    /** The most days one calendar read covers */
    const val MAX_RANGE_DAYS = 62

    /** Booked appointments starting within this many hours get their reminder, once */
    const val REMINDER_HOURS = 24

    const val MAX_TEXT = 200
    const val MAX_NOTES = 2000
}
