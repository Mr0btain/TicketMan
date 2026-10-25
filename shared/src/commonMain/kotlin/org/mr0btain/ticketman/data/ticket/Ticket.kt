package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.SubItem
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class Ticket(
    override val identifier: Uuid = Uuid.generateV7(),
    val device: Device = Device("", ""),
    val status: TicketStatus = TicketStatus(TicketStatusType.IN_PROGRESS, Clock.System.now().toEpochMilliseconds()),
    val priority: TicketPriority = TicketPriority(TicketPriorityType.LOW, Clock.System.now().toEpochMilliseconds()),
    /** Given out by the server when the ticket is added, null until then */
    val number: TicketNumber? = null,
    /** What the customer says is wrong */
    val problem: String = "",
    /** Damage or wear noted at check in */
    val condition: String = "",
    /** Anything left with the device, like a charger or case */
    val accessories: String = "",
    /** Quoted price in cents */
    val estimateCents: Long? = null,
    /** Epoch millis the customer was told it'd be ready */
    val promisedAt: Long? = null,
    /** Epoch millis it was picked up, only set by a pickup */
    val pickedUpAt: Long? = null,
    /**
     * The unguessable part of the customer's status page link ([StatusLink]). Given out by the server when the
     * ticket is added and only changed by replacing it, null until then
     */
    val statusToken: String? = null,
    /** The customer's [CustomerDevice] this repair is on, set at check in. The server's to set */
    val deviceIdentifier: Uuid? = null,
    /** Shared by tickets checked in together, several devices at one visit. The server's to set */
    val groupIdentifier: Uuid? = null,
    /** What was checked at check in and how it was */
    val checklist: List<ChecklistItem> = emptyList(),
    /** Whether a passcode is on file, which is kept encrypted apart from the ticket. The server's to set */
    val passcodeSet: Boolean = false,
    /** Epoch millis the repair's warranty runs out, set at pickup from the invoice's lines. Null for none */
    val warrantyUntil: Long? = null,
    /** The earlier ticket this is a warranty return of, set at check in. Its work is free unless staff charge */
    val warrantyOf: Uuid? = null,
    /** Epoch millis it was left too long after it was ready and treated as abandoned, by the hourly sweep */
    val abandonedAt: Long? = null,
    /** The [org.mr0btain.ticketman.data.location.Location] it's at, set when it's added and only changed by moving it. The server's to set */
    val locationIdentifier: Uuid? = null,
): MainItem(), SubItem {
    /** Number if it has one, otherwise the start of the identifier */
    val label: String
        get() = number?.toString() ?: identifier.toString().substringBefore("-")

    /** Whether its warranty still covers it at [now] */
    fun underWarranty(now: Long): Boolean = warrantyUntil?.let { it >= now } == true
}
