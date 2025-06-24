package org.mr0btain.ticketman.components.routing

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.mr0btain.ticketman.data.ticket.TicketInfo
import org.mr0btain.ticketman.database.LocalAppDatabase
import org.mr0btain.ticketman.server.response.ServerResponse
import org.mr0btain.ticketman.server.response.ServerResult
import kotlin.text.toIntOrNull

fun Route.ticketRoute(localAppDatabase: LocalAppDatabase) {
    // Get all tickets
    get("/all") { call.respond(localAppDatabase.getAllTickets()) }

    // Add New Ticket
    post("/add") {
        try {
            val ticket = call.receive<TicketInfo>()
            localAppDatabase.addTicket(ticket)
            call.respond(ServerResponse(ServerResult.SUCCESS))
        } catch (ex: Exception) {
            ex.printStackTrace()
            call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
        }
    }

    // Update ticket
    post("/update") {
        try {
            val ticket = call.receive<TicketInfo>()
            localAppDatabase.updateTicket(ticket)
            call.respond(ServerResponse(ServerResult.SUCCESS))
        } catch (ex: Exception) {
            ex.printStackTrace()
            call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
        }
    }

    // Bulk add tickets
    post("/add/all") {
        try {
            val tickets = call.receive<List<TicketInfo>>()

            if (localAppDatabase.addAllTickets(tickets)) {
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } else call.respond(ServerResponse(ServerResult.FAIL))
        } catch (ex: Exception) {
            ex.printStackTrace()
            call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
        }
    }

    // Delete ticket
    delete("/delete/{ticketIdentifier}") {
        val identifier = call.parameters["ticketIdentifier"]?.toIntOrNull()

        if (identifier == null) {
            call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
            return@delete
        }

        if (localAppDatabase.removeTicket(identifier)) {
            call.respond(ServerResponse(ServerResult.SUCCESS))
        } else {
            call.respond(ServerResponse(ServerResult.FAIL))
        }
    }
}