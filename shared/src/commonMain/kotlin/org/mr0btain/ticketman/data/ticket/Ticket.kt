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
): MainItem(), SubItem {
    /** Number if it has one, otherwise the start of the identifier */
    val label: String
        get() = number?.toString() ?: identifier.toString().substringBefore("-")
}
