package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.LocationDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.InventoryPaths
import org.mr0btain.ticketman.server.RecordPaths
import org.mr0btain.ticketman.server.request.StockChangeRequest
import org.mr0btain.ticketman.data.inventory.StockTransfer
import org.mr0btain.ticketman.data.inventory.StockTransferRequest

fun Route.inventoryRoute(
    database: InventoryDatabase,
    userDatabase: UserDatabase,
    auditLog: AuditLogDatabase,
    locations: LocationDatabase = LocationDatabase()
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
            // Its count where the caller works, or added up across every location
            val result = identifier?.let { database.getById(it, call.locationScope().identifier) }

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(GET_TAG, "$identifier not found")
            }
        }

        get(RecordPaths.FETCH) {
            val request = call.pagedRequest()?.at(call.locationScope()) ?: return@get call.respondBadQuery()

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

    authenticate("restricted") {
        // Exactly matching a barcode or SKU, for a scanner at the counter
        get(InventoryPaths.BY_CODE) {
            val item = call.parameters["code"]?.let { database.getByCode(it, call.locationScope().identifier) }

            if (item != null) call.respond(HttpStatusCode.OK, item) else call.respond(HttpStatusCode.NotFound)
        }

        get(InventoryPaths.CATEGORIES) {
            val categories = database.categories()

            if (categories != null) call.respond(HttpStatusCode.OK, categories) else call.respond(HttpStatusCode.InternalServerError)
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

            detailsProblem(received)?.let { problem ->
                call.respond(HttpStatusCode.BadRequest, problem)
                return@post
            }

            database.codeClash(received)?.let { clash ->
                call.respond(HttpStatusCode.Conflict, clash)
                return@post
            }

            // Its stock is the count where the caller works, the other locations have none yet
            val location = call.workingLocation().identifier
            val saved = received.takeIf { database.add(it, location) }?.let { database.getById(it.identifier, location) }

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

            detailsProblem(received)?.let { problem ->
                call.respond(HttpStatusCode.BadRequest, problem)
                return@post
            }

            database.codeClash(received)?.let { clash ->
                call.respond(HttpStatusCode.Conflict, clash)
                return@post
            }

            // The reorder point is the one where the caller works
            if (!database.update(received, call.workingLocation().identifier)) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, received.identifier, received.name)
            call.respond(HttpStatusCode.Accepted)
        }
    }

    authenticate("restricted") {
        get(InventoryPaths.TRANSFERS) {
            val identifier = call.uuidParameter("identifier") ?: return@get call.respond(HttpStatusCode.NotFound)
            database.transfers(identifier)?.let { call.respond(HttpStatusCode.OK, it) } ?: call.respond(HttpStatusCode.InternalServerError)
        }
    }

    // Parts from one location to another, both counts changed together
    authenticate {
        post(InventoryPaths.TRANSFER) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            val request = call.receive<StockTransferRequest>()
            request.problem()?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }

            val from = locations.get(request.fromLocation)?.takeIf { !it.retired }
            val to = locations.get(request.toLocation)?.takeIf { !it.retired }
            if (from == null || to == null) return@post call.respond(HttpStatusCode.BadRequest, "That location isn't one of the shop's")

            val transfer = StockTransfer(
                itemIdentifier = identifier,
                fromLocation = from.identifier,
                toLocation = to.identifier,
                quantity = request.quantity,
                note = request.note.trim(),
                movedBy = call.username(userDatabase),
                movedAt = System.currentTimeMillis()
            )
            when (database.transfer(transfer)) {
                InventoryDatabase.Transfer.DONE -> {
                    call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, identifier, "Moved ${request.quantity} from ${from.name} to ${to.name}" + transfer.note.ifBlank { null }?.let { ": $it" }.orEmpty())
                    call.respond(HttpStatusCode.Created, database.transfers(identifier, limit = 1)?.firstOrNull() ?: transfer)
                }
                InventoryDatabase.Transfer.SHORT -> {
                    val there = database.stockAt(identifier, from.identifier) ?: 0
                    call.respond(HttpStatusCode.Conflict, "Only $there at ${from.name}")
                }
                InventoryDatabase.Transfer.UNTRACKED -> call.respond(HttpStatusCode.BadRequest, "Its stock isn't counted")
                InventoryDatabase.Transfer.NOT_FOUND -> call.respond(HttpStatusCode.NotFound)
                null -> call.respond(HttpStatusCode.InternalServerError)
            }
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

            // The count at the location asked about, or where the caller works
            val location = request.locationIdentifier?.let { id -> locations.get(id)?.takeIf { !it.retired } ?: return@post call.respond(HttpStatusCode.BadRequest, "That location isn't one of the shop's") }
                ?: call.workingLocation()

            when (database.setStock(identifier, location.identifier, request.from, request.to)) {
                InventoryDatabase.StockChange.DONE -> {
                    call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, identifier, "Stock at ${location.name} ${request.from} to ${request.to}")
                    call.respond(HttpStatusCode.Accepted)
                }
                InventoryDatabase.StockChange.CHANGED_SINCE -> {
                    val now = database.stockAt(identifier, location.identifier)
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

/** What's wrong with an item's codes, category or reorder point, or null */
private fun detailsProblem(item: Inventory): String? = when {
    item.sku.trim().length > MAX_CODE -> "SKU can be up to $MAX_CODE characters"
    item.barcode.trim().length > MAX_CODE -> "Barcode can be up to $MAX_CODE characters"
    item.barcode.any { it.isWhitespace() } -> "A barcode can't have spaces in it"
    item.category.trim().length > MAX_CATEGORY -> "Category can be up to $MAX_CATEGORY characters"
    (item.reorderPoint ?: 0) < 0 -> "Reorder point can't be below zero"
    else -> null
}

private const val MAX_CODE = 64
private const val MAX_CATEGORY = 60
