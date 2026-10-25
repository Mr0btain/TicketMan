package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.DeviceDatabase
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.data.ticket.CustomerDevice
import org.mr0btain.ticketman.components.database.StoredValueDatabase
import org.mr0btain.ticketman.components.service.StoredValueService
import org.mr0btain.ticketman.server.request.BalanceAdjustRequest
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CounterDatabase
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.CustomerPaths
import org.mr0btain.ticketman.server.RecordPaths
import kotlin.uuid.Uuid

/**
 * Customer routes. Reading is open to every logged in user, changes need NORMAL access
 */
fun Route.customerRoute(
    database: CustomerDatabase,
    ticketDatabase: TicketDatabase,
    counterDatabase: CounterDatabase,
    userDatabase: UserDatabase,
    auditLog: AuditLogDatabase,
    storedValue: StoredValueDatabase,
    storedValueService: StoredValueService,
    invoiceDatabase: InvoiceDatabase,
    deviceDatabase: DeviceDatabase
) {
    val TAG = "CustomerRoute"
    val GET_TAG = "$TAG:Get"
    val FETCH_TAG = "$TAG:Fetch"
    val SEARCH_TAG = "$TAG:Search"
    val ENTITY_TYPE = "customer"

    // ------------------------------------------ Reading ------------------------------------------

    authenticate("restricted") {
        get(RecordPaths.FETCH) {
            val request = call.pagedRequest() ?: return@get call.respondBadQuery()

            val result = database.getPagedItems(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(FETCH_TAG, "Couldn't read $request")
            }
        }

        get(RecordPaths.GET) {
            val identifier = call.uuidParameter("identifier")
            val result = identifier?.let { database.getById(it) }

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(GET_TAG, "$identifier not found")
            }
        }

        get(RecordPaths.SEARCH) {
            val request = call.searchQuery() ?: return@get call.respondBadQuery()

            val result = database.search(request.query, request.limit)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(SEARCH_TAG, "Couldn't read $request")
            }
        }

        // The customer's tickets, newest first
        get(CustomerPaths.GET_TICKETS) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null || database.getById(identifier) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.respond(HttpStatusCode.OK, counterDatabase.customerTickets(identifier).map { it.ticket })
        }

        // Store credit given and spent, newest first
        get(CustomerPaths.CREDIT) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null || database.getById(identifier) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val history = storedValue.creditHistory(identifier, HISTORY_LIMIT)
            if (history != null) call.respond(HttpStatusCode.OK, history) else call.respond(HttpStatusCode.InternalServerError)
        }

        get(CustomerPaths.DEVICES) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null || database.getById(identifier) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val devices = deviceDatabase.forCustomer(identifier)
            if (devices != null) call.respond(HttpStatusCode.OK, devices) else call.respond(HttpStatusCode.InternalServerError)
        }

        get(CustomerPaths.DEVICE_TICKETS) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null || deviceDatabase.get(identifier) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val tickets = deviceDatabase.tickets(identifier)
            if (tickets != null) call.respond(HttpStatusCode.OK, tickets) else call.respond(HttpStatusCode.InternalServerError)
        }

        // Tickets' invoices and sales, newest first
        get(CustomerPaths.INVOICES) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null || database.getById(identifier) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val invoices = invoiceDatabase.getByCustomer(identifier, HISTORY_LIMIT)
            if (invoices != null) call.respond(HttpStatusCode.OK, invoices) else call.respond(HttpStatusCode.InternalServerError)
        }
    }

    authenticate("admin") {
        // Store credit given or taken by hand
        post(CustomerPaths.CREDIT) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(
                storedValueService.adjustCredit(identifier, call.receive<BalanceAdjustRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.OK
            )
        }
    }

    // ------------------------------------------ Changing ------------------------------------------

    authenticate {
        // Answers with the customer as saved
        post(RecordPaths.ADD) {
            val received = call.receive<Customer>()
            val saved = received.takeIf { database.add(it) }?.let { database.getById(it.identifier) }

            if (saved == null) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            call.audit(auditLog, AuditAction.CREATE, ENTITY_TYPE, saved.identifier, saved.name)
            call.respond(HttpStatusCode.Created, saved)
        }

        post(RecordPaths.UPDATE) {
            val received = call.receive<Customer>()

            if (database.getById(received.identifier) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            if (!database.update(received)) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, received.identifier, received.name)
            call.respond(HttpStatusCode.Accepted)
        }

        delete(RecordPaths.REMOVE) {
            val identifier = call.uuidParameter("identifier")

            // Their credit would go with them
            val credit = identifier?.let { database.getById(it) }?.creditCents ?: 0
            if (credit > 0) {
                call.respondText("They still have store credit, take it off first", status = HttpStatusCode.Conflict)
                return@delete
            }

            if (identifier == null || !database.remove(identifier)) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.audit(auditLog, AuditAction.DELETE, ENTITY_TYPE, identifier)
                call.respond(HttpStatusCode.Accepted)
            }
        }

        // A device for the customer, like one being kept on file before it's ever repaired
        post(CustomerPaths.DEVICES) {
            val identifier = call.uuidParameter("identifier")
            val received = call.receive<CustomerDevice>()

            if (identifier == null || database.getById(identifier) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            deviceProblem(received)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }

            val device = received.copy(customerIdentifier = identifier, createdAt = System.currentTimeMillis())
            if (!deviceDatabase.add(device)) {
                call.respond(HttpStatusCode.Conflict, "That device already exists")
                return@post
            }

            call.audit(auditLog, AuditAction.CREATE, "device", device.identifier, device.label)
            call.respond(HttpStatusCode.Created, deviceDatabase.get(device.identifier)!!)
        }

        post(CustomerPaths.DEVICE_UPDATE) {
            val received = call.receive<CustomerDevice>()
            deviceProblem(received)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }

            if (!deviceDatabase.update(received)) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            call.audit(auditLog, AuditAction.UPDATE, "device", received.identifier, received.label)
            call.respond(HttpStatusCode.OK, deviceDatabase.get(received.identifier)!!)
        }

        delete(CustomerPaths.DEVICE) {
            val identifier = call.uuidParameter("identifier")
            val device = identifier?.let { deviceDatabase.get(it) }

            if (device == null || !deviceDatabase.remove(device.identifier)) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            call.audit(auditLog, AuditAction.DELETE, "device", device.identifier, device.label)
            call.respond(HttpStatusCode.Accepted)
        }

        // Link a ticket to this customer, the body is the ticket's identifier
        post(CustomerPaths.ADD_TICKET) {
            val identifier = call.uuidParameter("identifier")
            val ticket = call.receive<Uuid>()

            if (identifier != null && ticketDatabase.setCustomer(ticket, identifier)) {
                call.audit(auditLog, AuditAction.UPDATE, "ticket", ticket, "Linked to customer $identifier")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    noteRoute(database, userDatabase, auditLog, ENTITY_TYPE)
}

private const val HISTORY_LIMIT = 200

/** What's wrong with a device's details, or null */
private fun deviceProblem(device: CustomerDevice): String? = when {
    device.device.manufacturer.isBlank() && device.device.model.isBlank() -> "Enter the device"
    else -> null
}
