package org.mr0btain.ticketman.components.routing

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.database.LocalAppDatabase
import org.mr0btain.ticketman.server.response.ServerResponse
import org.mr0btain.ticketman.server.response.ServerResult
import kotlin.text.toIntOrNull

fun Route.customerRoute(localAppDatabase: LocalAppDatabase) {
    // Get all customers
    get("/all") { call.respond(localAppDatabase.getAllCustomers()) }

    // Adding new customer
    post("/add") {
        try {
            val customer = call.receive<CustomerData>()
            localAppDatabase.addCustomer(customer)
            call.respond(HttpStatusCode.NoContent)
        } catch (ex: IllegalStateException) {
            call.respond(HttpStatusCode.BadRequest)
        } catch (ex: JsonConvertException) {
            call.respond(HttpStatusCode.BadRequest)
        }
    }

    // Update Customer
    post("/update") {
        try {
            val customer = call.receive<CustomerData>()

            if (localAppDatabase.updateCustomer(customer)) {
                call.respond(HttpStatusCode.Accepted)
            } else call.respond(HttpStatusCode.BadRequest)
        } catch (ex: Exception) {
            ex.printStackTrace()
            call.respond(HttpStatusCode.BadRequest)
        }
    }

    // Bulk add customers
    post("/add/all") {
        try {
            val customers = call.receive<List<CustomerData>>()

            if (localAppDatabase.addAllCustomers(customers)) {
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } else call.respond(ServerResponse(ServerResult.FAIL))
        } catch (ex: Exception) {
            ex.printStackTrace()
            call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
        }
    }

    // Delete customer
    delete("/delete/{customerIdentifier}") {
        val identifier = call.parameters["customerIdentifier"]?.toIntOrNull()

        if (identifier == null) {
            call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
            return@delete
        }

        if (localAppDatabase.removeCustomer(identifier)) {
            call.respond(ServerResponse(ServerResult.SUCCESS))
        } else {
            call.respond(ServerResponse(ServerResult.FAIL))
        }
    }
}