package org.mr0btain.ticketman.components.database

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.mr0btain.ticketman.components.database.tables.customer.CustomerTicketTable
import org.mr0btain.ticketman.components.database.tables.device.CustomerDeviceTable
import org.jetbrains.exposed.v1.core.isNull
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.components.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.data.ticket.CustomerDevice
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.DeviceKind
import org.mr0btain.ticketman.data.ticket.Ticket
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Customers' devices, and the tickets on each
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class DeviceDatabase {
    private val TAG = "DeviceDatabase"
    private val GET_TAG = "$TAG:Get"
    private val WRITE_TAG = "$TAG:Write"

    fun get(identifier: Uuid): CustomerDevice? =
        dbQuery(GET_TAG) { CustomerDeviceTable.selectAll().where { CustomerDeviceTable.id eq identifier }.singleOrNull()?.toDevice() }

    /** Oldest first. Null when the read failed */
    fun forCustomer(customerIdentifier: Uuid): List<CustomerDevice>? =
        dbQuery(GET_TAG) {
            CustomerDeviceTable.selectAll()
                .where { CustomerDeviceTable.customer eq customerIdentifier }
                .orderBy(CustomerDeviceTable.id to SortOrder.ASC)
                .map { it.toDevice() }
        }

    /** The customer's device with this serial, ignoring case, for a check in that didn't pick one */
    fun withSerial(customerIdentifier: Uuid, serial: String): CustomerDevice? =
        dbQuery(GET_TAG) {
            CustomerDeviceTable.selectAll()
                .where { (CustomerDeviceTable.customer eq customerIdentifier) and (CustomerDeviceTable.serial.lowerCase() eq serial.trim().lowercase()) }
                .firstOrNull()?.toDevice()
        }

    fun add(device: CustomerDevice): Boolean =
        dbWrite(WRITE_TAG) {
            CustomerDeviceTable.insert { row ->
                row[CustomerDeviceTable.id] = device.identifier
                row[customer] = device.customerIdentifier
                row[createdAt] = device.createdAt
                row.fill(device)
            }
            true
        }

    /** Saves its details, never who owns it */
    fun update(device: CustomerDevice): Boolean =
        dbWrite(WRITE_TAG) { CustomerDeviceTable.update(where = { CustomerDeviceTable.id eq device.identifier }) { row -> row.fill(device) } > 0 }

    fun remove(identifier: Uuid): Boolean =
        dbWrite(WRITE_TAG) { CustomerDeviceTable.deleteWhere { CustomerDeviceTable.id eq identifier } > 0 }

    /** The device's tickets, newest first. Null when the read failed */
    fun tickets(identifier: Uuid): List<Ticket>? =
        dbQuery(GET_TAG) {
            TicketEntity.find { TicketTable.deviceRef eq identifier }.orderBy(TicketTable.id to SortOrder.DESC).map { it.toTicket() }
        }

    /**
     * Makes devices from tickets saved before devices were kept, one per customer for each serial (or make and model
     * when there's no serial), and points the tickets at them. Run once, inside its transaction
     * @return How many devices were made
     */
    fun backfill(): Int {
        var made = 0
        val owners = CustomerTicketTable.selectAll().associate { it[CustomerTicketTable.ticket].value to it[CustomerTicketTable.customer].value }

        TicketEntity.find { TicketTable.deviceRef.isNull() }.orderBy(TicketTable.id to SortOrder.ASC).groupBy { owners[it.id.value] }.forEach { (customer, tickets) ->
            if (customer == null) return@forEach
            val known = mutableMapOf<String, Uuid>()

            tickets.forEach { ticket ->
                val device = ticket.device
                if (device.manufacturer.isBlank() && device.model.isBlank() && device.serial.isBlank()) return@forEach
                val key = device.serial.trim().lowercase().ifEmpty { "${device.manufacturer.trim().lowercase()}|${device.model.trim().lowercase()}" }

                ticket.deviceRef = known.getOrPut(key) {
                    val record = CustomerDevice(customerIdentifier = customer, device = device, createdAt = ticket.id.value.timestampMillis())
                    CustomerDeviceTable.insert { row ->
                        row[CustomerDeviceTable.id] = record.identifier
                        row[CustomerDeviceTable.customer] = customer
                        row[createdAt] = record.createdAt
                        row.fill(record)
                    }
                    made++
                    record.identifier
                }
            }
        }

        return made
    }

    private fun UpdateBuilder<*>.fill(device: CustomerDevice) {
        this[CustomerDeviceTable.manufacturer] = device.device.manufacturer.trim()
        this[CustomerDeviceTable.model] = device.device.model.trim()
        this[CustomerDeviceTable.serial] = device.device.serial.trim()
        this[CustomerDeviceTable.kind] = device.device.kind.name
        this[CustomerDeviceTable.color] = device.color.trim()
        this[CustomerDeviceTable.notes] = device.notes.trim()
    }

    private fun ResultRow.toDevice(): CustomerDevice =
        CustomerDevice(
            identifier = this[CustomerDeviceTable.id].value,
            customerIdentifier = this[CustomerDeviceTable.customer].value,
            device = Device(
                manufacturer = this[CustomerDeviceTable.manufacturer],
                model = this[CustomerDeviceTable.model],
                serial = this[CustomerDeviceTable.serial],
                kind = DeviceKind.entries.firstOrNull { it.name == this[CustomerDeviceTable.kind] } ?: DeviceKind.OTHER
            ),
            color = this[CustomerDeviceTable.color],
            notes = this[CustomerDeviceTable.notes],
            createdAt = this[CustomerDeviceTable.createdAt]
        )
}
