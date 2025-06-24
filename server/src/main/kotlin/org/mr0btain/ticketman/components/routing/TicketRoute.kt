package org.mr0btain.ticketman.components.routing

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.database.LocalDatabase
import org.mr0btain.ticketman.server.response.ServerResponse
import org.mr0btain.ticketman.server.response.ServerResult
import kotlin.text.toIntOrNull

fun Route.ticketRoute(localDatabase: LocalDatabase) {
    // Get all tickets
    authenticate("restricted") {
        get("/all") { call.respond(localDatabase.getAllTickets()) }
    }

    // Add New Ticket
    authenticate("normal") {
        post("/add") {
            try {
                val ticket = call.receive<TicketInfo>()
                localDatabase.addTicket(ticket)
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Update ticket
    authenticate("normal") {
        post("/update") {
            try {
                val ticket = call.receive<TicketInfo>()
                localDatabase.updateTicket(ticket)
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Bulk add tickets
    authenticate("normal") {
        post("/add/all") {
            try {
                val tickets = call.receive<List<TicketInfo>>()

                if (localDatabase.addAllTickets(tickets)) {
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } else call.respond(ServerResponse(ServerResult.FAIL))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Delete ticket
    authenticate("normal") {
        delete("/delete/{ticketIdentifier}") {
            val identifier = call.parameters["ticketIdentifier"]?.toIntOrNull()

            if (identifier == null) {
                call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
                return@delete
            }

            if (localDatabase.removeTicket(identifier)) {
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } else {
                call.respond(ServerResponse(ServerResult.FAIL))
            }
        }
    }
}