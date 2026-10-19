package org.mr0btain.ticketman.components.routing

import org.mr0btain.ticketman.server.CounterPaths
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.CounterDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.CheckInService
import org.mr0btain.ticketman.components.service.InvoiceService
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.PickupRequest
import org.mr0btain.ticketman.server.request.RepeatCheckRequest

/**
 * Read only lookups for the shop counter, open to every logged in user so restricted staff can answer questions
 */
fun Route.counterRoute(
    database: CounterDatabase,
    shopDatabase: ShopDatabase,
    invoiceService: InvoiceService,
    checkInService: CheckInService,
    userDatabase: UserDatabase
) {
    // Hand a ticket back: pay off its invoice, take parts from stock and close it. Needs NORMAL access
    authenticate {
        // A device coming in: ticket, customer, tech, deposit and signature in one go
        post(CounterPaths.CHECK_IN) {
            call.respondAccount(
                checkInService.checkIn(call.receive<CheckInRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.Created
            )
        }

        post(CounterPaths.PICKUP) {
            call.respondAccount(
                invoiceService.pickup(call.receive<PickupRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.OK
            )
        }
    }

    authenticate("restricted") {
        // Search tickets and customers at once
        get(CounterPaths.SEARCH) {
            val request = call.searchQuery() ?: return@get call.respondBadQuery()

            call.respond(HttpStatusCode.OK, database.search(request.query, request.limit.coerceIn(1, MAX_LIMIT)))
        }

        get(CounterPaths.QUEUE) {
            val recent = (call.queryParameters["recent"]?.toIntOrNull() ?: DEFAULT_RECENT).coerceIn(0, MAX_LIMIT)

            call.respond(HttpStatusCode.OK, database.queue(recent))
        }

        // Earlier repairs on the same device for this customer within the shop's repeat window. A POST although
        // it only reads, since it takes a whole device
        post(CounterPaths.REPEAT_CHECK) {
            val request = call.receive<RepeatCheckRequest>()
            val windowDays = shopDatabase.get().repeatWindowDays

            call.respond(
                HttpStatusCode.OK,
                database.repeatCheck(request.customerIdentifier, request.device, windowDays, request.excludeTicket)
            )
        }
    }
}

private const val DEFAULT_RECENT = 10
private const val MAX_LIMIT = 100
