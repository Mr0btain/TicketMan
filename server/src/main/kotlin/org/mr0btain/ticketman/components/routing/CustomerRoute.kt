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
import org.mr0btain.ticketman.server.response.ServerResponse
import org.mr0btain.ticketman.server.response.ServerResult
import kotlin.text.toIntOrNull

/**
 * Customer routes
 */
fun Route.customerRoute(database: CustomerDatabase) {
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

    // Delete customer
    authenticate {
        delete("/delete/{customerIdentifier}") {
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