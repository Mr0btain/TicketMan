package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.TicketDatabaseImpl
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.search.SearchQuery
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.Uuid

/**
 * Ticket routes. Reading is open to every logged in user, changes need NORMAL access
 */
fun Route.ticketRoute(
    database: TicketDatabaseImpl,
    userDatabase: UserDatabaseImpl,
    auditLog: AuditLogDatabase
) {
    val TAG = "TicketRoute"
    val GET_TAG = "$TAG:Get"
    val FETCH_TAG = "$TAG:Fetch"
    val SEARCH_TAG = "$TAG:Search"
    val HISTORY_TAG = "$TAG:History"
    val NUMBER_TAG = "$TAG:Number"
    val ENTITY_TYPE = "ticket"

    // ------------------------------------------ Reading ------------------------------------------

    authenticate("restricted") {
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

        // Look up by the number on a claim slip, like 26-00000042
        get("/number/{number}") {
            val number = call.parameters["number"]?.let { TicketNumber.parse(it) }
            val result = number?.let { database.getByNumber(it) }

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(NUMBER_TAG, "${call.parameters["number"]} not found")
            }
        }

        get("/get/history/{identifier}") {
            val identifier = call.uuidParameter("identifier")
            val result = identifier?.let { database.getStatusHistory(it) }

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(HISTORY_TAG, "$identifier not found")
            }
        }

        get("/get/customer/{identifier}") {
            val result = call.uuidParameter("identifier")?.let { database.getCustomer(it) }

            if (result != null) call.respond(HttpStatusCode.OK, result) else call.respond(HttpStatusCode.NotFound)
        }

        get("/get/user/{identifier}") {
            val result = call.uuidParameter("identifier")?.let { database.getUser(it) }

            if (result != null) call.respond(HttpStatusCode.OK, result) else call.respond(HttpStatusCode.NotFound)
        }
    }

    // ------------------------------------------ Changing ------------------------------------------

    authenticate {
        // Responds with the saved ticket so the caller gets its number
        post("/add") {
            val received = call.receive<Ticket>()

            database.add(received)
            val saved = database.getById(received.identifier)

            if (saved == null) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            database.recordStatusChange(saved.identifier, saved.status.status, call.username(userDatabase))
            call.audit(auditLog, AuditAction.CREATE, ENTITY_TYPE, saved.identifier, "Ticket ${saved.label}")
            call.respond(HttpStatusCode.Created, saved)
        }

        post("/update") {
            val received = call.receive<Ticket>()
            val existing = database.getById(received.identifier)

            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            database.update(received)

            if (existing.status.status != received.status.status) {
                database.recordStatusChange(received.identifier, received.status.status, call.username(userDatabase))
            }

            call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, received.identifier, "Ticket ${existing.label}: status ${received.status.status}")
            call.respond(HttpStatusCode.Accepted)
        }

        delete("/remove/{identifier}") {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            database.remove(identifier)
            call.audit(auditLog, AuditAction.DELETE, ENTITY_TYPE, identifier)
            call.respond(HttpStatusCode.Accepted)
        }

        // Body is the customer's identifier
        post("/set/customer/{identifier}") {
            val identifier = call.uuidParameter("identifier")
            val customer = call.receive<Uuid>()

            if (identifier != null && database.setCustomer(identifier, customer)) {
                call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, identifier, "Customer set to $customer")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // Body is the tech's identifier
        post("/set/user/{identifier}") {
            val identifier = call.uuidParameter("identifier")
            val user = call.receive<Uuid>()

            if (identifier != null && database.setUser(identifier, user)) {
                call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, identifier, "Assigned to ${userDatabase.getById(user)?.username}")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    noteRoutes(database, userDatabase, auditLog, ENTITY_TYPE)
}
