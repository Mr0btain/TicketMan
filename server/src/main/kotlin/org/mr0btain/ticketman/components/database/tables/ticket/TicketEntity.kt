package org.mr0btain.ticketman.components.database.tables.ticket

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.mr0btain.ticketman.components.database.tables.note.NoteEntity
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class TicketEntity(entityID: EntityID<Uuid>) : UuidEntity(entityID) {
    companion object : UuidEntityClass<TicketEntity>(TicketTable)

    var device by TicketTable.device
    var status by TicketTable.status
    var priority by TicketTable.priority
    var numberYear by TicketTable.numberYear
    var numberSequence by TicketTable.numberSequence
    var problem by TicketTable.problem
    var condition by TicketTable.condition
    var accessories by TicketTable.accessories
    var estimateCents by TicketTable.estimateCents
    var promisedAt by TicketTable.promisedAt
    var pickedUpAt by TicketTable.pickedUpAt
    var statusToken by TicketTable.statusToken
    var deviceRef by TicketTable.deviceRef
    var group by TicketTable.group
    var checklist by TicketTable.checklist
    val passcodeSet by TicketTable.passcodeSet
    var warrantyUntil by TicketTable.warrantyUntil
    var warrantyOf by TicketTable.warrantyOf
    var abandonedAt by TicketTable.abandonedAt
    var numberPrefix by TicketTable.numberPrefix
    var location by TicketTable.location

    // For notes
    var notes by NoteEntity via TicketNoteTable

    fun toTicket(): Ticket {
        return Ticket(
            identifier = id.value,
            device = device,
            status = status,
            priority = priority,
            number = numberYear?.let { year -> numberSequence?.let { TicketNumber(year, it, numberPrefix) } },
            problem = problem,
            condition = condition,
            accessories = accessories,
            estimateCents = estimateCents,
            promisedAt = promisedAt,
            pickedUpAt = pickedUpAt,
            statusToken = statusToken,
            deviceIdentifier = deviceRef,
            groupIdentifier = group,
            checklist = checklist,
            passcodeSet = passcodeSet,
            warrantyUntil = warrantyUntil,
            warrantyOf = warrantyOf,
            abandonedAt = abandonedAt,
            locationIdentifier = location
        )
    }
}