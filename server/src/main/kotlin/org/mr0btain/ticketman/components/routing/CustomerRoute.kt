package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CounterDatabase
import org.mr0btain.ticketman.components.database.CustomerDatabaseImpl
import org.mr0btain.ticketman.components.database.TicketDatabaseImpl
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.search.SearchQuery
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.Uuid

/**
 * Customer routes. Reading is open to every logged in user, changes need NORMAL access
 */
fun Route.customerRoute(
    database: CustomerDatabaseImpl,
    ticketDatabase: TicketDatabaseImpl,
    counterDatabase: CounterDatabase,
    userDatabase: UserDatabaseImpl,
    auditLog: AuditLogDatabase
) {
    val TAG = "CustomerRoute"
    val GET_TAG = "$TAG:Get"
    val FETCH_TAG = "$TAG:Fetch"
    val SEARCH_TAG = "$TAG:Search"
    val ENTITY_TYPE = "customer"

    // ------------------------------------------ Reading ------------------------------------------

    authenticate("restricted") {
        post("/fetch") {
            val request = call.receive<PagedRequest>()

            val result = database.getPagedItems(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(FETCH_TAG, "$request not found")
            }
        }

        post("/get") {
            val request = call.receive<Uuid>()

            val result = database.getById(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(GET_TAG, "$request not found")
            }
        }

        post("/search") {
            val request = call.receive<SearchQuery>()

            val result = database.search(request.query, request.limit)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(SEARCH_TAG, "$request not found")
            }
        }

        // The customer's tickets, newest first
        get("/get/tickets/{identifier}") {
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
        post("/add") {
            val received = call.receive<Customer>()

            database.add(received)

            call.audit(auditLog, AuditAction.CREATE, ENTITY_TYPE, received.identifier, received.cleanName())
            call.respond(HttpStatusCode.Accepted)
        }

        post("/update") {
            val received = call.receive<Customer>()

            if (database.getById(received.identifier) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            database.update(received)
            call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, received.identifier, received.cleanName())
            call.respond(HttpStatusCode.Accepted)
        }

        delete("/remove/{identifier}") {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                database.remove(identifier)
                call.audit(auditLog, AuditAction.DELETE, ENTITY_TYPE, identifier)
                call.respond(HttpStatusCode.Accepted)
            }
        }

        // Link a ticket to this customer, the body is the ticket's identifier
        post("/add/ticket/{identifier}") {
            val identifier = call.uuidParameter("identifier")
            val ticket = call.receive<Uuid>()

            if (identifier != null && ticketDatabase.setCustomer(ticket, identifier)) {
                call.audit(auditLog, AuditAction.UPDATE, "ticket", ticket, "Linked to customer $identifier")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // Images aren't uploaded through the client yet
        post("/add/images/{identifier}") {
            call.respond(HttpStatusCode.NotImplemented)
        }

        delete("/remove/image/{identifier}") {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                database.removeImage(identifier)
                call.respond(HttpStatusCode.Accepted)
            }
        }
    }

    noteRoutes(database, userDatabase, auditLog, ENTITY_TYPE)
}
