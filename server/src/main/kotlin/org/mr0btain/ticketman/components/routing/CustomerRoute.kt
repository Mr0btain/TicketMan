package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.CustomerDatabaseImpl
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.SyncResponse
import kotlin.uuid.Uuid

// TODO: Add more comments to help understand whats going on

fun Route.customerRoute(
    database: CustomerDatabaseImpl
) {
    // Sync between client and server
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

    // Adding new
    authenticate {
        post("/add") {
            val received = call.receive<Customer>()

            database.add(received)

            call.respond(HttpStatusCode.Accepted)
        }
    }

    // Update
    authenticate {
        post("/update") {
            val received = call.receive<Customer>()
            database.update(received)
            call.respond(HttpStatusCode.Accepted)
        }
    }

    // Bulk add mostly for syncing
    authenticate {
        post("/add/all") {
            val list = call.receive<List<Customer>>()

            database.addAll(list)

            call.respond(HttpStatusCode.Accepted)
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

            database.remove(Uuid.parse(identifier))
            call.respond(HttpStatusCode.Accepted)
        }
    }
}