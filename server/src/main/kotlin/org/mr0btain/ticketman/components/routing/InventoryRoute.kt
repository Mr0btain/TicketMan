package org.mr0btain.ticketman.components.routing

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.mr0btain.ticketman.data.inventory.InventoryItem
import org.mr0btain.ticketman.database.LocalAppDatabase
import org.mr0btain.ticketman.server.response.ServerResponse
import org.mr0btain.ticketman.server.response.ServerResult
import kotlin.text.toIntOrNull

fun Route.inventoryRoute(localAppDatabase: LocalAppDatabase) {
    // Get all inventory
    get("/all") { call.respond(localAppDatabase.getInventory()) }

    // Add New item
    post("/add") {
        try {
            val inventoryItem = call.receive<InventoryItem>()
            localAppDatabase.addInventoryItem(inventoryItem)
            call.respond(ServerResponse(ServerResult.SUCCESS))
        } catch (ex: Exception) {
            ex.printStackTrace()
            call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
        }
    }

    // Update item
    post("/update") {
        try {
            val inventoryItem = call.receive<InventoryItem>()
            localAppDatabase.updateInventoryItem(inventoryItem)
            call.respond(ServerResponse(ServerResult.SUCCESS))
        } catch (ex: Exception) {
            ex.printStackTrace()
            call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
        }
    }

    // Bulk add employees
    post("/add/all") {
        try {
            val inventoryItems = call.receive<List<InventoryItem>>()

            if (localAppDatabase.addAllInventory(inventoryItems)) {
                call.respond(ServerResponse(ServerResult.SUCCESS))
            } else call.respond(ServerResponse(ServerResult.FAIL))
        } catch (ex: Exception) {
            ex.printStackTrace()
            call.respond(ServerResponse(ServerResult.FAIL, ex.toString()))
        }
    }

    // Delete item
    delete("/delete/{inventoryIdentifier}") {
        val identifier = call.parameters["inventoryIdentifier"]?.toIntOrNull()

        if (identifier == null) {
            call.respond(ServerResponse(ServerResult.FAIL, "Identifier is null"))
            return@delete
        }

        if (localAppDatabase.removeInventoryItem(identifier)) {
            call.respond(ServerResponse(ServerResult.SUCCESS))
        } else {
            call.respond(ServerResponse(ServerResult.FAIL))
        }
    }
}