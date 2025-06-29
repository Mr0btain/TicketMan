package org.mr0btain.ticketman.components.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.database.TicketDatabase
import org.mr0btain.ticketman.server.response.ServerResponse
import org.mr0btain.ticketman.server.response.ServerResult
import kotlin.text.toIntOrNull

fun Route.ticketRoute(database: TicketDatabase) {
    // Get all tickets
    authenticate("restricted") {
        get("/all") {
            database.getAll()?.let { data -> call.respond(data) } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Add New Ticket
    authenticate {
        post("/add") {
            try {
                val ticket = call.receive<TicketInfo>()

                database.add(ticket)
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Update ticket
    authenticate {
        post("/update") {
            try {
                val ticket = call.receive<TicketInfo>()

                database.update(ticket)
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Bulk add tickets
    authenticate {
        post("/add/all") {
            try {
                val tickets = call.receive<List<TicketInfo>>()

                if (database.addAll(tickets)) call.respond(ServerResponse(ServerResult.SUCCESS))
                else call.respond(ServerResponse(ServerResult.FAIL))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Delete ticket
    authenticate {
        delete("/delete/{ticketIdentifier}") {
            val identifier = call.parameters["ticketIdentifier"]?.toIntOrNull()

            if (identifier == null) {
                call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
                return@delete
            }

            if (database.remove(identifier)) call.respond(ServerResponse(ServerResult.SUCCESS))
            else call.respond(ServerResponse(ServerResult.FAIL))
        }
    }
}