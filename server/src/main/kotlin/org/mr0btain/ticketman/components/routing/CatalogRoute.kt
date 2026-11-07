package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CatalogDatabase
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.catalog.CatalogModel
import org.mr0btain.ticketman.data.catalog.RepairCatalog
import org.mr0btain.ticketman.server.CatalogPaths
import kotlin.uuid.ExperimentalUuidApi

private const val ENTITY_TYPE = "catalog"

/**
 * The repair menu, under /api/catalog. Anyone logged in reads it, for check-in; admins change it
 */
@OptIn(ExperimentalUuidApi::class)
fun Route.catalogRoute(
    database: CatalogDatabase,
    inventory: InventoryDatabase,
    shop: ShopDatabase,
    auditLog: AuditLogDatabase
) {
    // What's wrong with a model besides the shared rules: parts and tax classes that aren't the shop's
    fun problem(model: CatalogModel, menu: List<CatalogModel>): String? {
        RepairCatalog.problem(model, menu)?.let { return it }
        val classes = shop.get().taxClasses.map { it.identifier }.toSet()
        model.repairs.forEach { offer ->
            if (offer.taxClass != null && offer.taxClass !in classes) return "\"${offer.name}\" has a tax class the shop doesn't have"
            if (offer.inventoryIdentifier != null && inventory.getById(offer.inventoryIdentifier!!) == null) return "The part for \"${offer.name}\" isn't in inventory"
        }
        return null
    }

    authenticate("restricted") {
        get {
            val menu = database.all() ?: return@get call.respond(HttpStatusCode.InternalServerError)
            call.respond(HttpStatusCode.OK, menu)
        }
    }

    authenticate("admin") {
        post(CatalogPaths.ADD) {
            val model = RepairCatalog.cleaned(call.receive<CatalogModel>())
            val menu = database.all() ?: return@post call.respond(HttpStatusCode.InternalServerError)

            if (menu.any { it.identifier == model.identifier }) return@post call.respond(HttpStatusCode.Conflict, "That model is on the menu already")
            problem(model, menu)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
            if (!database.add(model)) return@post call.respond(HttpStatusCode.InternalServerError)

            call.audit(auditLog, AuditAction.CREATE, ENTITY_TYPE, model.identifier, "Added ${model.label} with ${model.repairs.size} repairs")
            call.respond(HttpStatusCode.Created, database.get(model.identifier)!!)
        }

        post(CatalogPaths.UPDATE) {
            val model = RepairCatalog.cleaned(call.receive<CatalogModel>())
            val menu = database.all() ?: return@post call.respond(HttpStatusCode.InternalServerError)

            if (menu.none { it.identifier == model.identifier }) return@post call.respond(HttpStatusCode.NotFound)
            problem(model, menu)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
            if (!database.update(model)) return@post call.respond(HttpStatusCode.NotFound)

            call.audit(auditLog, AuditAction.UPDATE, ENTITY_TYPE, model.identifier, "Updated ${model.label}, ${model.repairs.size} repairs")
            call.respond(HttpStatusCode.OK, database.get(model.identifier)!!)
        }

        delete(CatalogPaths.REMOVE) {
            val identifier = call.uuidParameter("identifier") ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val existing = database.get(identifier) ?: return@delete call.respond(HttpStatusCode.NotFound)
            if (!database.remove(identifier)) return@delete call.respond(HttpStatusCode.NotFound)

            call.audit(auditLog, AuditAction.DELETE, ENTITY_TYPE, identifier, "Removed ${existing.label}")
            call.respond(HttpStatusCode.Accepted)
        }
    }
}
