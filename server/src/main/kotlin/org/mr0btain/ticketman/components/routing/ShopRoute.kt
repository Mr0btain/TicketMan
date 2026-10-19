package org.mr0btain.ticketman.components.routing

import org.mr0btain.ticketman.server.ShopPaths
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.ShopDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.money.Money
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
        post(ShopPaths.UPDATE) {
            val profile = call.receive<ShopProfile>()

            if (profile.repeatWindowDays !in 1..3650) {
                call.respond(HttpStatusCode.BadRequest, "Repeat warning must be between 1 and 3650 days")
                return@post
            }

            val symbol = profile.currencySymbol.trim()
            if (symbol.isEmpty() || symbol.length > Money.MAX_SYMBOL_LENGTH || symbol.any { it.isDigit() }) {
                call.respond(HttpStatusCode.BadRequest, "Currency symbol must be 1 to ${Money.MAX_SYMBOL_LENGTH} characters, no digits")
                return@post
            }

            if (database.update(profile.copy(currencySymbol = symbol))) {
                call.audit(auditLog, AuditAction.SETTINGS_UPDATE, "shop", details = "Updated shop profile")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(UPDATE_TAG, "Couldn't save shop profile")
            }
        }
    }
}
