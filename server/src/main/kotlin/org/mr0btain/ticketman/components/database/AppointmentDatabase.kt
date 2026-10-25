package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.appointment.AppointmentTable
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTable
import org.mr0btain.ticketman.components.database.tables.user.UserTable
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Appointments on the calendar. Rules like only changing booked ones are the appointment service's
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class AppointmentDatabase {
    private val TAG = "AppointmentDatabase"
    private val GET_TAG = "$TAG:Get"
    private val WRITE_TAG = "$TAG:Write"

    fun get(identifier: Uuid): Appointment? =
        dbQuery(GET_TAG) {
            AppointmentTable.selectAll().where { AppointmentTable.id eq identifier }.toList().toAppointments().singleOrNull()
        }

    /** Starting from [fromMillis] up to but not including [toMillis], earliest first. Null when the read failed */
    fun between(fromMillis: Long, toMillis: Long): List<Appointment>? =
        dbQuery(GET_TAG) {
            AppointmentTable.selectAll()
                .where { (AppointmentTable.startsAt greaterEq fromMillis) and (AppointmentTable.startsAt less toMillis) }
                .orderBy(AppointmentTable.startsAt to SortOrder.ASC)
                .toList()
                .toAppointments()
        }

    /** Booked ones starting after [fromMillis] and by [toMillis] whose reminder hasn't gone. Null when the read failed */
    fun dueReminders(fromMillis: Long, toMillis: Long): List<Appointment>? =
        dbQuery(GET_TAG) {
            AppointmentTable.selectAll()
                .where {
                    (AppointmentTable.status eq AppointmentStatus.BOOKED.name) and AppointmentTable.reminderSentAt.isNull() and
                        AppointmentTable.customer.isNotNull() and
                        (AppointmentTable.startsAt greater fromMillis) and (AppointmentTable.startsAt lessEq toMillis)
                }
                .toList()
                .toAppointments()
        }

    fun add(appointment: Appointment): Boolean =
        dbWrite(WRITE_TAG) {
            AppointmentTable.insert { row ->
                row[AppointmentTable.id] = appointment.identifier
                row[status] = appointment.status.name
                row[createdBy] = appointment.createdBy
                row[createdAt] = appointment.createdAt
                row.fill(appointment)
            }
            true
        }

    /** Saves what can be changed: never its status, ticket, reminder or who booked it */
    fun update(appointment: Appointment): Boolean =
        dbWrite(WRITE_TAG) {
            AppointmentTable.update(where = { AppointmentTable.id eq appointment.identifier }) { row -> row.fill(appointment) } > 0
        }

    fun setStatus(identifier: Uuid, status: AppointmentStatus): Boolean =
        dbWrite(WRITE_TAG) {
            AppointmentTable.update(where = { AppointmentTable.id eq identifier }) { row -> row[AppointmentTable.status] = status.name } > 0
        }

    /**
     * Marks a booked one checked in as [ticket], linking [customer] when it had none. Part of the check in's
     * transaction; false when it isn't booked any more, like checked in at the same moment somewhere else
     */
    fun markArrived(identifier: Uuid, ticket: Uuid, customer: Uuid): Boolean =
        dbWrite(WRITE_TAG) {
            AppointmentTable.update(where = { (AppointmentTable.id eq identifier) and (AppointmentTable.status eq AppointmentStatus.BOOKED.name) }) { row ->
                row[status] = AppointmentStatus.ARRIVED.name
                row[AppointmentTable.ticket] = ticket
                row[AppointmentTable.customer] = customer
            } > 0
        }

    fun markReminded(identifier: Uuid, at: Long): Boolean =
        dbWrite(WRITE_TAG) {
            AppointmentTable.update(where = { AppointmentTable.id eq identifier }) { row -> row[reminderSentAt] = at } > 0
        }

    /** So it's sent again, after the time changed */
    fun clearReminder(identifier: Uuid): Boolean =
        dbWrite(WRITE_TAG) {
            AppointmentTable.update(where = { AppointmentTable.id eq identifier }) { row -> row[reminderSentAt] = null } > 0
        }

    fun remove(identifier: Uuid): Boolean =
        dbWrite(WRITE_TAG) { AppointmentTable.deleteWhere { AppointmentTable.id eq identifier } > 0 }

    private fun UpdateBuilder<*>.fill(appointment: Appointment) {
        this[AppointmentTable.startsAt] = appointment.startsAt
        this[AppointmentTable.minutes] = appointment.minutes
        this[AppointmentTable.customer] = appointment.customerIdentifier
        this[AppointmentTable.customerName] = appointment.customerName.trim()
        this[AppointmentTable.phone] = appointment.phone.trim()
        this[AppointmentTable.email] = appointment.email.trim()
        this[AppointmentTable.device] = appointment.device
        this[AppointmentTable.problem] = appointment.problem.trim()
        this[AppointmentTable.notes] = appointment.notes.trim()
        this[AppointmentTable.technician] = appointment.technicianIdentifier
    }

    // Inside a transaction. A linked customer's and the technician's names come from their records
    private fun List<ResultRow>.toAppointments(): List<Appointment> {
        val customerIds = mapNotNull { it[AppointmentTable.customer] }.distinct()
        val customers = if (customerIds.isEmpty()) emptyMap() else CustomerTable
            .select(CustomerTable.id, CustomerTable.name)
            .where { CustomerTable.id inList customerIds }
            .associate { it[CustomerTable.id].value to it[CustomerTable.name] }

        val technicianIds = mapNotNull { it[AppointmentTable.technician] }.distinct()
        val technicians = if (technicianIds.isEmpty()) emptyMap() else UserTable
            .select(UserTable.id, UserTable.name, UserTable.username)
            .where { UserTable.id inList technicianIds }
            .associate { it[UserTable.id].value to it[UserTable.name].ifBlank { it[UserTable.username] } }

        return map { row ->
            val customer = row[AppointmentTable.customer]
            val technician = row[AppointmentTable.technician]

            Appointment(
                identifier = row[AppointmentTable.id].value,
                startsAt = row[AppointmentTable.startsAt],
                minutes = row[AppointmentTable.minutes],
                customerIdentifier = customer,
                customerName = customer?.let { customers[it] } ?: row[AppointmentTable.customerName],
                phone = row[AppointmentTable.phone],
                email = row[AppointmentTable.email],
                device = row[AppointmentTable.device],
                problem = row[AppointmentTable.problem],
                notes = row[AppointmentTable.notes],
                technicianIdentifier = technician,
                technicianName = technician?.let { technicians[it] }.orEmpty(),
                status = AppointmentStatus.valueOf(row[AppointmentTable.status]),
                ticketIdentifier = row[AppointmentTable.ticket],
                reminderSentAt = row[AppointmentTable.reminderSentAt],
                createdBy = row[AppointmentTable.createdBy],
                createdAt = row[AppointmentTable.createdAt]
            )
        }
    }
}
