package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.PurchaseDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.PurchaseService
import org.mr0btain.ticketman.data.purchase.PurchaseStatus
import org.mr0btain.ticketman.data.purchase.Supplier
import org.mr0btain.ticketman.server.PurchasePaths
import org.mr0btain.ticketman.server.request.OrderPartRequest
import org.mr0btain.ticketman.server.request.PurchaseOrderRequest
import org.mr0btain.ticketman.server.request.ReceiveRequest

/**
 * Suppliers and purchase orders. Anyone logged in can look, Normal access orders and receives
 */
fun Route.purchaseRoute(
    database: PurchaseDatabase,
    service: PurchaseService,
    userDatabase: UserDatabase
) {
    authenticate("restricted") {
        get(PurchasePaths.SUPPLIERS) {
            val suppliers = database.suppliers()
            if (suppliers != null) call.respond(HttpStatusCode.OK, suppliers) else call.respond(HttpStatusCode.InternalServerError)
        }

        // `?status=ORDERED,PARTLY_RECEIVED` for only those
        get(PurchasePaths.ORDERS) {
            // Orders for where the caller works
            val request = call.pagedRequest()?.at(call.locationScope()) ?: return@get call.respondBadQuery()
            val statuses = call.queryParameters["status"]?.takeIf { it.isNotBlank() }?.split(',')?.map { name ->
                PurchaseStatus.entries.firstOrNull { it.name == name.trim().uppercase() } ?: return@get call.respondBadQuery()
            }

            val orders = database.orders(request, statuses)
            if (orders != null) call.respond(HttpStatusCode.OK, orders) else call.respond(HttpStatusCode.InternalServerError)
        }

        get(PurchasePaths.ORDER) {
            val order = call.uuidParameter("identifier")?.let { database.order(it) }
            if (order != null) call.respond(HttpStatusCode.OK, order) else call.respond(HttpStatusCode.NotFound)
        }

        get(PurchasePaths.TICKET) {
            val ticket = call.uuidParameter("ticketIdentifier") ?: return@get call.respond(HttpStatusCode.NotFound)
            val orders = database.forTicket(ticket)
            if (orders != null) call.respond(HttpStatusCode.OK, orders) else call.respond(HttpStatusCode.InternalServerError)
        }
    }

    authenticate {
        post(PurchasePaths.SUPPLIER_ADD) {
            call.respondAccount(service.addSupplier(call.receive<Supplier>(), call.getIdentifierFromToken()), HttpStatusCode.Created)
        }

        post(PurchasePaths.SUPPLIER_UPDATE) {
            call.respondAccount(service.updateSupplier(call.receive<Supplier>(), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        delete(PurchasePaths.SUPPLIER) {
            val identifier = call.uuidParameter("identifier") ?: return@delete call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.removeSupplier(identifier, call.getIdentifierFromToken()), HttpStatusCode.Accepted)
        }

        post(PurchasePaths.ADD) {
            call.respondAccount(
                service.create(call.receive<PurchaseOrderRequest>(), call.username(userDatabase), call.getIdentifierFromToken(), call.workingLocation().identifier),
                HttpStatusCode.Created
            )
        }

        post(PurchasePaths.PART) {
            call.respondAccount(
                service.orderPart(call.receive<OrderPartRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.OK
            )
        }

        post(PurchasePaths.UPDATE) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.update(identifier, call.receive<PurchaseOrderRequest>(), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        post(PurchasePaths.PLACE) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.place(identifier, call.username(userDatabase), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        post(PurchasePaths.RECEIVE) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(
                service.receive(identifier, call.receive<ReceiveRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.OK
            )
        }

        post(PurchasePaths.CANCEL) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.cancel(identifier, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        delete(PurchasePaths.ORDER) {
            val identifier = call.uuidParameter("identifier") ?: return@delete call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.remove(identifier, call.getIdentifierFromToken()), HttpStatusCode.Accepted)
        }
    }
}
