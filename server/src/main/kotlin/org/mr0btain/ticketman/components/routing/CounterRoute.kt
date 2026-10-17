package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.CounterDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.data.search.SearchQuery
import org.mr0btain.ticketman.server.request.RepeatCheckRequest

/**
 * Read only lookups for the shop counter, open to every logged in user so restricted staff can answer questions
 */
fun Route.counterRoute(
    database: CounterDatabase,
    shopDatabase: ShopDatabase
) {
    authenticate("restricted") {
        // Search tickets and customers at once
        post("/search") {
            val request = call.receive<SearchQuery>()

            call.respond(HttpStatusCode.OK, database.search(request.query, request.limit.coerceIn(1, MAX_LIMIT)))
        }

        get("/queue") {
            val recent = (call.queryParameters["recent"]?.toIntOrNull() ?: DEFAULT_RECENT).coerceIn(0, MAX_LIMIT)

            call.respond(HttpStatusCode.OK, database.queue(recent))
        }

        // Earlier repairs on the same device for this customer within the shop's repeat window
        post("/repeat-check") {
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
