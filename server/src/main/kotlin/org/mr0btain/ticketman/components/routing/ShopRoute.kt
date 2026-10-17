package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.logging.AppLogger

/**
 * Shop profile routes. Everyone can read it for invoices, only admins can change it
 */
fun Route.shopRoute(
    database: ShopDatabase,
    auditLog: AuditLogDatabase
) {
    val TAG = "ShopRoute"
    val UPDATE_TAG = "$TAG:Update"

    authenticate("restricted") {
        get {
            call.respond(HttpStatusCode.OK, database.get())
        }
    }

    authenticate("admin") {
        post("/update") {
            val profile = call.receive<ShopProfile>()

            if (database.update(profile)) {
                call.audit(auditLog, AuditAction.SETTINGS_UPDATE, "shop", details = "Updated shop profile")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(UPDATE_TAG, "Couldn't save shop profile")
            }
        }
    }
}
