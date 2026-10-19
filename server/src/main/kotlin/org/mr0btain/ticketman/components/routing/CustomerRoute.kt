package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
    auditLog: AuditLogDatabase
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

            call.audit(auditLog, AuditAction.CREATE, ENTITY_TYPE, saved.identifier, saved.cleanName())
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

            call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, received.identifier, received.cleanName())
            call.respond(HttpStatusCode.Accepted)
        }

        delete(RecordPaths.REMOVE) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null || !database.remove(identifier)) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.audit(auditLog, AuditAction.DELETE, ENTITY_TYPE, identifier)
                call.respond(HttpStatusCode.Accepted)
            }
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
