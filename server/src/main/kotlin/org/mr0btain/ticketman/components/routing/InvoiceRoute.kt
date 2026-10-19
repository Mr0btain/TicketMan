package org.mr0btain.ticketman.components.routing

import org.mr0btain.ticketman.server.InvoicePaths
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.InvoiceService
import org.mr0btain.ticketman.server.request.InvoiceUpdateRequest
import org.mr0btain.ticketman.server.request.PaymentRequest

/**
 * Invoice routes. Anyone logged in can look, NORMAL and up can change open invoices, only admins can void
 */
fun Route.invoiceRoute(
    database: InvoiceDatabase,
    service: InvoiceService,
    userDatabase: UserDatabase
) {
    authenticate("restricted") {
        get(InvoicePaths.FOR_TICKET) {
            val invoice = call.uuidParameter("ticketIdentifier")?.let { database.getByTicket(it) }

            if (invoice != null) call.respond(HttpStatusCode.OK, invoice) else call.respond(HttpStatusCode.NotFound)
        }

        get(InvoicePaths.GET) {
            val invoice = call.uuidParameter("identifier")?.let { database.getById(it) }

            if (invoice != null) call.respond(HttpStatusCode.OK, invoice) else call.respond(HttpStatusCode.NotFound)
        }

        get(InvoicePaths.FETCH) {
            val request = call.pagedRequest() ?: return@get call.respondBadQuery()

            call.respond(HttpStatusCode.OK, database.getPagedItems(request))
        }
    }

    authenticate {
        // Gets or makes the ticket's invoice
        post(InvoicePaths.FOR_TICKET) {
            val ticket = call.uuidParameter("ticketIdentifier")

            if (ticket == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(service.forTicket(ticket, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        post(InvoicePaths.UPDATE) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(service.update(identifier, call.receive<InvoiceUpdateRequest>(), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        post(InvoicePaths.PAYMENTS) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(
                service.addPayment(identifier, call.receive<PaymentRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.OK
            )
        }

        delete(InvoicePaths.PAYMENT) {
            val identifier = call.uuidParameter("identifier")
            val payment = call.uuidParameter("paymentIdentifier")

            if (identifier == null || payment == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            call.respondAccount(service.removePayment(identifier, payment, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }
    }

    authenticate("admin") {
        post(InvoicePaths.VOID) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(service.void(identifier, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }
    }
}
