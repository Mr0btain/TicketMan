package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.notify.NotificationService
import org.mr0btain.ticketman.data.notify.NotificationSettings
import org.mr0btain.ticketman.server.NotifyPaths
import org.mr0btain.ticketman.server.request.TestMessageRequest
import org.mr0btain.ticketman.server.request.TicketMessageRequest

/**
 * Messages to customers, under /api/notify. Admins set up how they're sent and see every one; staff see a ticket's
 * and send one by hand
 */
fun Route.notifyRoute(
    service: NotificationService,
    userDatabase: UserDatabase
) {
    authenticate("admin") {
        // Without the mail password and text token, which never leave the server
        get(NotifyPaths.SETTINGS) {
            call.respond(HttpStatusCode.OK, service.settings())
        }

        post(NotifyPaths.SETTINGS) {
            call.respondAccount(service.saveSettings(call.receive<NotificationSettings>(), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        post(NotifyPaths.TEST) {
            call.respondAccount(service.test(call.receive<TestMessageRequest>(), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        get(NotifyPaths.OUTBOX) {
            val request = call.pagedRequest() ?: return@get call.respondBadQuery()
            service.page(request)?.let { call.respond(HttpStatusCode.OK, it) } ?: call.respond(HttpStatusCode.InternalServerError)
        }

        post(NotifyPaths.RETRY) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.retry(identifier, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        post(NotifyPaths.CANCEL) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.cancel(identifier, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }
    }

    authenticate("restricted") {
        get(NotifyPaths.TICKET) {
            val identifier = call.uuidParameter("identifier") ?: return@get call.respond(HttpStatusCode.NotFound)
            service.forTicket(identifier)?.let { call.respond(HttpStatusCode.OK, it) } ?: call.respond(HttpStatusCode.InternalServerError)
        }
    }

    authenticate {
        // Answers with the messages queued, one per channel
        post(NotifyPaths.TICKET_SEND) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(
                service.sendForTicket(identifier, call.receive<TicketMessageRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.Created
            )
        }
    }
}
