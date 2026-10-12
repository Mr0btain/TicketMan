package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.InventoryDatabaseImpl
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.SyncResponse
import kotlin.uuid.Uuid

fun Route.inventoryRoute(
    database: InventoryDatabaseImpl
) {
    val TAG = "InventoryRoute"
    val GET_TAG = "$TAG:Get"
    val FETCH_TAG = "$TAG:Fetch"
    val DELETE_TAG = "$TAG:Delete"
    val UPDATE_TAG = "$TAG:Update"
    val SEARCH_TAG = "$TAG:Search"

    authenticate {
        post("/get") {
            val request = call.receive<Uuid>()

            val result = database.getById(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(GET_TAG, "$request not found")
            }
        }
    }

    authenticate {
        post("/fetch") {
            val request = call.receive<PagedRequest>()

            val result = database.getPagedItems(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(FETCH_TAG, "$request not found")
            }
        }
    }

    authenticate {
        post("/search") {
            val request = call.receive<String>()

            val result = database.search(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(SEARCH_TAG, "$request not found")
            }
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