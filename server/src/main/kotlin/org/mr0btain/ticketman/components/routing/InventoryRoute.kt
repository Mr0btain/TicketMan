package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.InventoryPaths
import org.mr0btain.ticketman.server.RecordPaths
import org.mr0btain.ticketman.server.request.StockChangeRequest

fun Route.inventoryRoute(
    database: InventoryDatabase,
    userDatabase: UserDatabase,
    auditLog: AuditLogDatabase
) {
    val TAG = "InventoryRoute"
    val GET_TAG = "$TAG:Get"
    val FETCH_TAG = "$TAG:Fetch"
    val DELETE_TAG = "$TAG:Delete"
    val UPDATE_TAG = "$TAG:Update"
    val SEARCH_TAG = "$TAG:Search"
    val ENTITY_TYPE = "inventory"

    authenticate("restricted") {
        get(RecordPaths.GET) {
            val identifier = call.uuidParameter("identifier")
            val result = identifier?.let { database.getById(it) }

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(GET_TAG, "$identifier not found")
            }
        }

        get(RecordPaths.FETCH) {
            val request = call.pagedRequest() ?: return@get call.respondBadQuery()

            val result = database.getPagedItems(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(FETCH_TAG, "Couldn't read $request")
            }
        }

        get(RecordPaths.SEARCH) {
            val request = call.searchQuery() ?: return@get call.respondBadQuery()

            val result = database.search(request.query, request.limit)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(SEARCH_TAG, "Couldn't read $request")
            }
        }
    }

    // Photos of the item itself
    authenticate("restricted") {
        get(InventoryPaths.GET_IMAGES) {
            val result = call.uuidParameter("identifier")?.let { database.getImages(it) }

            if (result != null) call.respond(HttpStatusCode.OK, result)
            else call.respond(HttpStatusCode.NotFound)
        }
    }

    // Body is every photo the item should have, uploaded ones get linked and missing ones deleted
    authenticate {
        post(InventoryPaths.SET_IMAGES) {
            val identifier = call.uuidParameter("identifier")
            val images = call.receive<List<StoredImage>>()

            if (identifier != null && database.setImages(identifier, images)) {
                call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, identifier, "${images.size} photos")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    // Adding new
    authenticate {
        // Answers with the item as saved
        post(RecordPaths.ADD) {
            val received = call.receive<Inventory>()

            if (received.stock < 0) {
                call.respond(HttpStatusCode.BadRequest, "Stock can't be below zero")
                return@post
            }

            val saved = received.takeIf { database.add(it) }?.let { database.getById(it.identifier) }

            if (saved == null) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            call.audit(auditLog, AuditAction.CREATE, ENTITY_TYPE, saved.identifier, saved.name)
            call.respond(HttpStatusCode.Created, saved)
        }
    }

    // Update, everything but the stock count
    authenticate {
        post(RecordPaths.UPDATE) {
            val received = call.receive<Inventory>()

            if (!database.update(received)) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, received.identifier, received.name)
            call.respond(HttpStatusCode.Accepted)
        }
    }

    // Stock count by hand. Refused if it changed since the item was opened, so a pickup's change isn't lost
    authenticate {
        post(InventoryPaths.SET_STOCK) {
            val identifier = call.uuidParameter("identifier")
            val request = call.receive<StockChangeRequest>()

            if (identifier == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            if (request.to < 0) {
                call.respond(HttpStatusCode.BadRequest, "Stock can't be below zero")
                return@post
            }

            when (database.setStock(identifier, request.from, request.to)) {
                InventoryDatabase.StockChange.DONE -> {
                    call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, identifier, "Stock ${request.from} to ${request.to}")
                    call.respond(HttpStatusCode.Accepted)
                }
                InventoryDatabase.StockChange.CHANGED_SINCE -> {
                    val now = database.getById(identifier)?.stock
                    val count = if (now == Int.MAX_VALUE) "untracked" else "$now"
                    call.respond(HttpStatusCode.Conflict, "Stock changed to $count since this was opened, check the count and try again")
                }
                InventoryDatabase.StockChange.NOT_FOUND -> call.respond(HttpStatusCode.NotFound)
                null -> call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }

    // Remove
    authenticate {
        delete(RecordPaths.REMOVE) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null || !database.remove(identifier)) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            call.audit(auditLog, AuditAction.DELETE, ENTITY_TYPE, identifier)
            call.respond(HttpStatusCode.Accepted)
        }
    }

    noteRoute(database, userDatabase, auditLog, ENTITY_TYPE)
}
