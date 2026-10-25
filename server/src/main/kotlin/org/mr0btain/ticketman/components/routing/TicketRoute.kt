package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import org.mr0btain.ticketman.server.request.PasscodeBody
import org.mr0btain.ticketman.data.ticket.IntakeChecklists
import org.mr0btain.ticketman.components.service.PasscodeVault
import org.mr0btain.ticketman.components.database.PasscodeDatabase
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.database.WorkDatabase
import org.mr0btain.ticketman.components.service.InvoiceService
import org.mr0btain.ticketman.components.notify.NotificationService
import org.mr0btain.ticketman.server.request.WorkRequest
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.ticket.CustomStatusRules
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.RecordPaths
import org.mr0btain.ticketman.server.TicketPaths
import kotlin.uuid.Uuid

/**
 * Ticket routes. Reading is open to every logged in user, changes need NORMAL access
 */
fun Route.ticketRoute(
    database: TicketDatabase,
    userDatabase: UserDatabase,
    auditLog: AuditLogDatabase,
    workDatabase: WorkDatabase,
    shopDatabase: ShopDatabase,
    invoiceService: InvoiceService,
    notificationService: NotificationService,
    passcodes: PasscodeDatabase = PasscodeDatabase(),
    vault: PasscodeVault? = null
) {
    val TAG = "TicketRoute"
    val GET_TAG = "$TAG:Get"
    val FETCH_TAG = "$TAG:Fetch"
    val FETCH_MINE_TAG = "$TAG:FetchMine"
    val SEARCH_TAG = "$TAG:Search"
    val HISTORY_TAG = "$TAG:History"
    val NUMBER_TAG = "$TAG:Number"
    val ENTITY_TYPE = "ticket"

    // ------------------------------------------ Reading ------------------------------------------

    authenticate("restricted") {
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

        // Tickets assigned to the caller, who comes from the token so nobody can list someone else's
        get(TicketPaths.FETCH_MINE) {
            val request = call.pagedRequest() ?: return@get call.respondBadQuery()
            val caller = call.getIdentifierFromToken()

            if (caller == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val result = database.getPagedItemsForUser(caller, request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(FETCH_MINE_TAG, "Couldn't read $request")
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

        // Look up by the number on a claim slip, like 26-00000042
        get(TicketPaths.BY_NUMBER) {
            val number = call.parameters["number"]?.let { TicketNumber.parse(it) }
            val result = number?.let { database.getByNumber(it) }

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(NUMBER_TAG, "${call.parameters["number"]} not found")
            }
        }

        get(TicketPaths.HISTORY) {
            val identifier = call.uuidParameter("identifier")
            val result = identifier?.let { database.getStatusHistory(it) }

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(HISTORY_TAG, "$identifier not found")
            }
        }

        get(TicketPaths.SIGNATURE) {
            val result = call.uuidParameter("identifier")?.let { database.getSignature(it) }

            if (result != null) call.respond(HttpStatusCode.OK, result) else call.respond(HttpStatusCode.NotFound)
        }

        get(TicketPaths.GET_CUSTOMER) {
            val result = call.uuidParameter("identifier")?.let { database.getCustomer(it) }

            if (result != null) call.respond(HttpStatusCode.OK, result) else call.respond(HttpStatusCode.NotFound)
        }

        get(TicketPaths.GET_USER) {
            val result = call.uuidParameter("identifier")?.let { database.getUser(it) }

            if (result != null) call.respond(HttpStatusCode.OK, result) else call.respond(HttpStatusCode.NotFound)
        }
    }

    // ------------------------------------------ Work log ------------------------------------------

    authenticate("restricted") {
        get(TicketPaths.GET_WORK) {
            val identifier = call.uuidParameter("identifier")

            when {
                identifier == null || database.getById(identifier) == null -> call.respond(HttpStatusCode.NotFound)
                else -> workDatabase.getForTicket(identifier)
                    ?.let { call.respond(HttpStatusCode.OK, it) }
                    ?: call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }

    authenticate {
        // Answers with the entry as saved, its price and who logged it filled in. It's on the invoice too
        post(TicketPaths.ADD_WORK) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)

            call.respondAccount(
                invoiceService.logWork(identifier, call.receive<WorkRequest>(), call.getIdentifierFromToken(), call.username(userDatabase)),
                HttpStatusCode.Created
            )
        }

        // Time and parts together, all or none. Answers with the entries as saved, in the order sent
        post(TicketPaths.ADD_WORK_ALL) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)

            call.respondAccount(
                invoiceService.logWork(identifier, call.receive<List<WorkRequest>>(), call.getIdentifierFromToken(), call.username(userDatabase)),
                HttpStatusCode.Created
            )
        }

        // The identifier is the work entry's
        delete(TicketPaths.REMOVE_WORK) {
            val identifier = call.uuidParameter("identifier") ?: return@delete call.respond(HttpStatusCode.NotFound)

            call.respondAccount(invoiceService.removeWork(identifier, call.getIdentifierFromToken()))
        }
    }

    // ------------------------------------------ Changing ------------------------------------------

    authenticate {
        // Responds with the saved ticket so the caller gets its number
        post(RecordPaths.ADD) {
            val received = call.receive<Ticket>().let { ticket ->
                when (val resolved = CustomStatusRules.resolve(ticket.status, shopDatabase.get().statuses)) {
                    is CustomStatusRules.Resolved.Ok -> ticket.copy(status = resolved.status)
                    is CustomStatusRules.Resolved.Problem -> return@post call.respond(HttpStatusCode.BadRequest, resolved.message)
                }
            }

            if (received.status.status == TicketStatusType.PICKED_UP) {
                call.respond(HttpStatusCode.BadRequest, "New tickets can't start picked up")
                return@post
            }

            // Device and group links are made by check in, never sent
            val saved = received.copy(deviceIdentifier = null, groupIdentifier = null, warrantyOf = null, warrantyUntil = null)
                .takeIf { database.add(it) }?.let { database.getById(it.identifier) }

            if (saved == null) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            database.recordStatusChange(saved.identifier, saved.status, call.username(userDatabase))
            call.audit(auditLog, AuditAction.CREATE, ENTITY_TYPE, saved.identifier, "Ticket ${saved.label}")
            call.respond(HttpStatusCode.Created, saved)
        }

        post(RecordPaths.UPDATE) {
            val sent = call.receive<Ticket>()
            val existing = database.getById(sent.identifier)

            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            // A ticket already in a status that's since been retired can stay in it
            val received = when (val resolved = CustomStatusRules.resolve(sent.status, shopDatabase.get().statuses, existing.status)) {
                is CustomStatusRules.Resolved.Ok -> sent.copy(status = resolved.status)
                is CustomStatusRules.Resolved.Problem -> return@post call.respond(HttpStatusCode.BadRequest, resolved.message)
            }

            // Pickup pays the invoice and takes the parts, so it only happens at the counter, and a picked up ticket stays closed
            if (existing.status.status != received.status.status) {
                if (existing.status.status == TicketStatusType.PICKED_UP) {
                    call.respond(HttpStatusCode.Conflict, "Ticket ${existing.label} was picked up, its status can't change")
                    return@post
                }

                if (received.status.status == TicketStatusType.PICKED_UP) {
                    call.respond(HttpStatusCode.Conflict, "Pick tickets up at the counter, so the invoice gets paid")
                    return@post
                }
            }

            if (!database.update(received)) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            if (existing.status.choice != received.status.choice) {
                database.recordStatusChange(received.identifier, received.status, call.username(userDatabase))
                notificationService.statusChanged(received.copy(number = existing.number, statusToken = existing.statusToken), existing.status.choice)
            }

            call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, received.identifier, "Ticket ${existing.label}: status ${received.status.status}")
            call.respond(HttpStatusCode.Accepted)
        }

        delete(RecordPaths.REMOVE) {
            val identifier = call.uuidParameter("identifier")

            // Looked at first, the delete decides whether the invoice stays
            val kept = identifier?.let { invoiceService.invoiceFor(it) }?.takeIf { it.outlivesTicket }

            if (identifier == null || !database.remove(identifier)) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            call.audit(auditLog, AuditAction.DELETE, ENTITY_TYPE, identifier, kept?.let { "Invoice ${it.label} kept, it has payments or was voided" }.orEmpty())
            call.respond(HttpStatusCode.Accepted)
        }

        // Shows the device's passcode to staff who can edit. Every look is in the audit log
        get(TicketPaths.PASSCODE) {
            val ticket = call.uuidParameter("identifier")?.let { database.getById(it) } ?: return@get call.respond(HttpStatusCode.NotFound)
            val sealed = passcodes.get(ticket.identifier) ?: return@get call.respond(HttpStatusCode.NotFound)
            val passcode = vault?.open(sealed)
                ?: return@get call.respond(HttpStatusCode.Conflict, "This passcode was saved under another server secret and can't be read, ask the customer again")

            call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, ticket.identifier, "Passcode for ${ticket.label} viewed")
            call.respond(HttpStatusCode.OK, PasscodeBody(passcode))
        }

        post(TicketPaths.PASSCODE) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            val passcode = call.receive<PasscodeBody>().passcode.trim()
            val ticket = database.getById(identifier) ?: return@post call.respond(HttpStatusCode.NotFound)

            when {
                vault == null -> return@post call.respond(HttpStatusCode.InternalServerError)
                passcode.isEmpty() -> return@post call.respond(HttpStatusCode.BadRequest, "Type the passcode, or clear it instead")
                passcode.length > IntakeChecklists.MAX_PASSCODE -> return@post call.respond(HttpStatusCode.BadRequest, "A passcode can be up to ${IntakeChecklists.MAX_PASSCODE} characters")
                ticket.status.status == TicketStatusType.PICKED_UP -> return@post call.respond(HttpStatusCode.Conflict, "Ticket ${ticket.label} was picked up")
            }

            if (!passcodes.set(identifier, vault!!.seal(passcode), System.currentTimeMillis(), call.username(userDatabase))) {
                return@post call.respond(HttpStatusCode.NotFound)
            }
            call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, identifier, "Passcode for ${ticket.label} set")
            call.respond(HttpStatusCode.Accepted)
        }

        delete(TicketPaths.PASSCODE) {
            val identifier = call.uuidParameter("identifier") ?: return@delete call.respond(HttpStatusCode.NotFound)
            val ticket = database.getById(identifier) ?: return@delete call.respond(HttpStatusCode.NotFound)

            passcodes.clear(identifier)
            call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, identifier, "Passcode for ${ticket.label} cleared")
            call.respond(HttpStatusCode.Accepted)
        }

        // Cuts off the old status page link. Answers with the ticket and its new token
        post(TicketPaths.STATUS_TOKEN) {
            val identifier = call.uuidParameter("identifier")
            val saved = identifier?.takeIf { database.replaceStatusToken(it) != null }?.let { database.getById(it) }

            if (saved == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, saved.identifier, "Ticket ${saved.label}: status page link replaced")
            call.respond(HttpStatusCode.OK, saved)
        }

        // Body is the customer's identifier
        post(TicketPaths.SET_CUSTOMER) {
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
        post(TicketPaths.SET_USER) {
            val identifier = call.uuidParameter("identifier")
            val user = call.receive<Uuid>()

            // Same rule as check-in: only active users with Normal access or higher take tickets
            val technician = userDatabase.getById(user)
            if (technician == null || !technician.canAccess(AccessLevel.NORMAL)) {
                call.respond(HttpStatusCode.BadRequest, "That technician can't take tickets")
                return@post
            }

            if (identifier != null && database.setUser(identifier, user)) {
                call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, identifier, "Assigned to ${technician.username}")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    noteRoute(database, userDatabase, auditLog, ENTITY_TYPE)
}
