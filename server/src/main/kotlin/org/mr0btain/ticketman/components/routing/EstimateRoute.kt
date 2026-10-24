package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.EstimateService
import org.mr0btain.ticketman.server.EstimatePaths
import org.mr0btain.ticketman.server.request.EstimateDecisionRequest
import org.mr0btain.ticketman.server.request.EstimateRequest

/**
 * Estimates on tickets, under /api/estimate. Customers answer on the status page; staff send them and write down
 * answers given another way
 */
fun Route.estimateRoute(
    service: EstimateService,
    userDatabase: UserDatabase
) {
    authenticate("restricted") {
        get(EstimatePaths.TICKET) {
            val ticket = call.uuidParameter("ticketIdentifier") ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.forTicket(ticket), HttpStatusCode.OK)
        }
    }

    authenticate {
        post(EstimatePaths.TICKET) {
            val ticket = call.uuidParameter("ticketIdentifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(
                service.create(ticket, call.receive<EstimateRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.Created
            )
        }

        post(EstimatePaths.DECIDE) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(
                service.decide(identifier, call.receive<EstimateDecisionRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.OK
            )
        }

        post(EstimatePaths.WITHDRAW) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.withdraw(identifier, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }
    }
}
