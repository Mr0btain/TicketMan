package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.payments.PaymentService
import org.mr0btain.ticketman.data.payment.PaymentSettings
import org.mr0btain.ticketman.server.PaymentPaths
import org.mr0btain.ticketman.server.request.ChargeRefundRequest
import org.mr0btain.ticketman.server.request.PaymentLinkRequest
import org.mr0btain.ticketman.server.request.ReaderChargeRequest

/**
 * Card payments through the shop's processor, under /api/payment. Admins set it up, staff send amounts to readers
 * and make payment links, anyone logged in follows a charge. Webhooks come in under the status page, see
 * [statusRoute]
 */
fun Route.paymentRoute(
    service: PaymentService,
    userDatabase: UserDatabase
) {
    authenticate("admin") {
        // Without the keys, which never leave the server
        get(PaymentPaths.SETTINGS) {
            call.respond(HttpStatusCode.OK, service.settings())
        }

        post(PaymentPaths.SETTINGS) {
            call.respondAccount(service.saveSettings(call.receive<PaymentSettings>(), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        get(PaymentPaths.ACCOUNT_READERS) {
            call.respondAccount(service.accountReaders(), HttpStatusCode.OK)
        }

        get(PaymentPaths.CHARGES) {
            val request = call.pagedRequest() ?: return@get call.respondBadQuery()
            service.page(request)?.let { call.respond(HttpStatusCode.OK, it) } ?: call.respond(HttpStatusCode.InternalServerError)
        }

        post(PaymentPaths.REFUND) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.refundUnclaimed(identifier, call.receive<ChargeRefundRequest>().reason, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }
    }

    authenticate("restricted") {
        get(PaymentPaths.OPTIONS) {
            call.respond(HttpStatusCode.OK, service.options())
        }

        // Polled by the counter while it waits for the card
        get(PaymentPaths.CHARGE) {
            val charge = call.uuidParameter("identifier")?.let { service.charge(it) }
            if (charge != null) call.respond(HttpStatusCode.OK, charge) else call.respond(HttpStatusCode.NotFound)
        }

        get(PaymentPaths.FOR_INVOICE) {
            val identifier = call.uuidParameter("identifier") ?: return@get call.respond(HttpStatusCode.NotFound)
            service.forInvoice(identifier)?.let { call.respond(HttpStatusCode.OK, it) } ?: call.respond(HttpStatusCode.InternalServerError)
        }
    }

    authenticate {
        post(PaymentPaths.READER) {
            call.respondAccount(
                service.startReader(call.receive<ReaderChargeRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.Created
            )
        }

        post(PaymentPaths.LINK) {
            call.respondAccount(
                service.link(call.receive<PaymentLinkRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.Created
            )
        }

        post(PaymentPaths.CANCEL) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.cancel(identifier, call.username(userDatabase), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }
    }
}
