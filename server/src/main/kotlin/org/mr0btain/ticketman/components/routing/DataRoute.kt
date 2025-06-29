package org.mr0btain.ticketman.components.routing

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.database.CustomerDatabase
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.ServerResponse
import org.mr0btain.ticketman.server.response.ServerResult
import org.mr0btain.ticketman.server.response.SyncResponse
import kotlin.collections.filter
import kotlin.collections.map
import kotlin.text.toIntOrNull

fun Route.dataRoute(database: CustomerDatabase) {
    // Sync between client and server
    authenticate {
        post("/sync") {
            val request = call.receive<SyncRequest>()
            val data = database.getAll()

            if (data != null) {
                // Filter items by ones the client does not contain
                val responseData = data.filter { !request.clientData.contains(it.identifier) }

                // Map all the ids in the local database
                val dataIdentifiers = data.map { it.identifier }

                // Filter response data so we can send back a response to request those that are not in our database
                val requested = request.clientData.filter { !dataIdentifiers.contains(it) }

                // Send response
                call.respond(HttpStatusCode.OK, SyncResponse(responseData, requested))
            } else call.respond(HttpStatusCode.NotFound)
        }
    }

    // Get all customers
    authenticate("restricted") {
        get("/all") {
            database.getAll()?.let { data -> call.respond(data) } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Adding new customer
    authenticate {
        post("/add") {
            try {
                val customer = call.receive<CustomerData>()
                database.add(customer)
                call.respond(HttpStatusCode.NoContent)
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
                val customer = call.receive<CustomerData>()

                if (database.update(customer)) call.respond(HttpStatusCode.Accepted) else call.respond(HttpStatusCode.BadRequest)
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
                val customers = call.receive<List<CustomerData>>()

                if (database.addAll(customers)) call.respond(ServerResponse(ServerResult.SUCCESS))
                else call.respond(ServerResponse(ServerResult.FAIL))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Remove customer
    authenticate {
        delete("/remove/{customerIdentifier}") {
            val identifier = call.parameters["customerIdentifier"]?.toIntOrNull()

            if (identifier == null) {
                call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
                return@delete
            }

            if (database.remove(identifier)) call.respond(ServerResponse(ServerResult.SUCCESS))
            else call.respond(ServerResponse(ServerResult.FAIL))
        }
    }
}