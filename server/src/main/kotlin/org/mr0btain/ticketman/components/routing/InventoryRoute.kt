package org.mr0btain.ticketman.components.routing

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.database.LocalDatabase
import org.mr0btain.ticketman.server.response.ServerResponse
import org.mr0btain.ticketman.server.response.ServerResult
import kotlin.text.toIntOrNull

/**
 * Inventory routes
 */
fun Route.inventoryRoute(localDatabase: LocalDatabase) {
    // Get all inventory
    authenticate("restricted") {
        get("/all") { call.respond(localDatabase.getInventory()) }
    }

    // Add New item
    authenticate("normal") {
        post("/add") {
            try {
                val inventoryItem = call.receive<InventoryItem>()
                localDatabase.addInventoryItem(inventoryItem)
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Update item
    authenticate("normal") {
        post("/update") {
            try {
                val inventoryItem = call.receive<InventoryItem>()
                localDatabase.updateInventoryItem(inventoryItem)
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Bulk add employees
    authenticate("normal") {
        post("/add/all") {
            try {
                val inventoryItems = call.receive<List<InventoryItem>>()

                if (localDatabase.addAllInventory(inventoryItems)) {
                    call.respond(ServerResponse(ServerResult.SUCCESS))
                } else call.respond(ServerResponse(ServerResult.FAIL))
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
            }
        }
    }

    // Delete item
    authenticate("normal") {
        delete("/delete/{inventoryIdentifier}") {
            val identifier = call.parameters["inventoryIdentifier"]?.toIntOrNull()

            if (identifier == null) {
                call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
                return@delete
            }

            if (localDatabase.removeInventoryItem(identifier)) {
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } else {
                call.respond(ServerResponse(ServerResult.FAIL))
            }
        }
    }
}