package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.InventoryDatabaseImpl
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.SyncResponse
import kotlin.uuid.Uuid

fun Route.inventoryRoute(
    database: InventoryDatabaseImpl
) {
    // Sync between client and server
    // TODO: There is prolly a way better way to do this
    authenticate {
        post("/sync") {
            val syncRequest = call.receive<SyncRequest>()

            database.all()?.let { data ->
                // Filter items by ones the client does not contain
                val responseData = data.filter { !syncRequest.clientData.contains(it.identifier) }

                // Map all the ids in the local database
                val dataIdentifiers = data.map { it.identifier }

                // Filter response data so we can send back a response to request those that are not in our database
                val requested = syncRequest.clientData.filter { !dataIdentifiers.contains(it) }

                call.respond(HttpStatusCode.OK, SyncResponse(responseData, requested))
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Get all
    authenticate("restricted") {
        get("/all") {
            // TODO: Setup large data transfers for these kinds of things
            database.all()?.let { data ->
                call.respond(HttpStatusCode.Accepted, data)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Adding new
    authenticate {
        post("/add") {
            database.add(call.receive())
        }
    }

    // Update
    authenticate {
        post("/update") {
            database.update(call.receive())
        }
    }

    // Bulk add mostly for syncing
    authenticate {
        post("/add/all") {
            database.addAll(call.receive())
        }
    }

    // Remove
    authenticate {
        delete("/remove/{identifier}") {
            val identifier = call.parameters["identifier"]

            if (identifier == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            database.remove(Uuid.parse(identifier))
            call.respond(HttpStatusCode.Accepted)
        }
    }
}