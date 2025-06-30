package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.database.LocalDatabaseClient
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.SyncResponse

fun Route.ticketRoute(database: LocalDatabaseClient<TicketInfo>) {
    // Sync between client and server
    authenticate {
        post("/sync") {
            val request = call.receive<SyncRequest>()

            database.getAll()?.let { data ->
                // Filter items by ones the client does not contain
                val responseData = data.filter { !request.clientData.contains(it.identifier) }

                // Map all the ids in the local database
                val dataIdentifiers = data.map { it.identifier }

                // Filter response data so we can send back a response to request those that are not in our database
                val requested = request.clientData.filter { !dataIdentifiers.contains(it) }

                // Send response
                call.respond(HttpStatusCode.OK, SyncResponse(responseData, requested))
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Get all
    authenticate("restricted") {
        get("/all") {
            database.getAll()?.let { data ->
                call.respond(HttpStatusCode.Accepted, data)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Adding new
    authenticate {
        post("/add") {
            try {
                val item = call.receive<TicketInfo>()

                database.add(item)

                call.respond(HttpStatusCode.Accepted)
            } catch (ex: IllegalStateException) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            } catch (ex: JsonConvertException) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    // Update Customer
    authenticate {
        post("/update") {
            try {
                val data = call.receive<TicketInfo>()

                if (database.update(data)) call.respond(HttpStatusCode.Accepted) else call.respond(HttpStatusCode.BadRequest)
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    // Bulk add customers mostly for syncing
    authenticate {
        post("/add/all") {
            try {
                val data = call.receive<List<TicketInfo>>()

                if (database.addAll(data)) call.respond(HttpStatusCode.Accepted)
                else call.respond(HttpStatusCode.NotFound)
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    // Remove customer
    authenticate {
        delete("/remove/{identifier}") {
            val identifier = call.parameters["identifier"]

            if (identifier == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            if (database.remove(identifier)) call.respond(HttpStatusCode.Accepted)
            else call.respond(HttpStatusCode.BadRequest)
        }
    }
}