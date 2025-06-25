package org.mr0btain.ticketman.components.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.database.InventoryDatabase
import org.mr0btain.ticketman.server.response.ServerResponse
import org.mr0btain.ticketman.server.response.ServerResult
import kotlin.text.toIntOrNull

/**
 * Inventory routes
 */
fun Route.inventoryRoute(database: InventoryDatabase) {
    // Get all inventory
    authenticate("restricted") {
        get("/all") {
            database.getAll()?.let { data ->
                call.respond(data)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Add New item
    authenticate {
        post("/add") {
            try {
                val inventoryItem = call.receive<InventoryItem>()
                database.add(inventoryItem)
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Update item
    authenticate {
        post("/update") {
            try {
                val inventoryItem = call.receive<InventoryItem>()
                database.update(inventoryItem)
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Bulk add employees
    authenticate {
        post("/add/all") {
            try {
                val inventoryItems = call.receive<List<InventoryItem>>()

                if (database.addAll(inventoryItems)) {
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } else call.respond(ServerResponse(ServerResult.FAIL))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Delete item
    authenticate {
        delete("/delete/{inventoryIdentifier}") {
            val identifier = call.parameters["inventoryIdentifier"]?.toIntOrNull()

            if (identifier == null) {
                call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
                return@delete
            }

            if (database.remove(identifier)) {
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } else {
                call.respond(ServerResponse(ServerResult.FAIL))
            }
        }
    }
}