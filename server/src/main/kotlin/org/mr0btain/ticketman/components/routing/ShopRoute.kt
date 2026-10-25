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
import org.mr0btain.ticketman.data.invoice.TaxClasses
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.CustomStatusRules
import org.mr0btain.ticketman.data.ticket.selectable
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

            if (profile.laborRateCents < 0) {
                call.respond(HttpStatusCode.BadRequest, "Labor rate can't be negative")
                return@post
            }

            val symbol = profile.currencySymbol.trim()
            if (symbol.isEmpty() || symbol.length > Money.MAX_SYMBOL_LENGTH || symbol.any { it.isDigit() }) {
                call.respond(HttpStatusCode.BadRequest, "Currency symbol must be 1 to ${Money.MAX_SYMBOL_LENGTH} characters, no digits")
                return@post
            }

            ShopProfile.publicUrlProblem(profile.publicUrl)?.let { problem ->
                call.respond(HttpStatusCode.BadRequest, problem)
                return@post
            }

            TaxClasses.problem(profile.taxClasses)?.let { problem ->
                call.respond(HttpStatusCode.BadRequest, problem)
                return@post
            }

            // Statuses left out are kept as retired, since tickets may still be in them
            val previous = database.get().statuses
            val statuses = CustomStatusRules.keepingRemoved(previous, profile.statuses)
            (CustomStatusRules.changeProblem(previous, profile.statuses) ?: CustomStatusRules.problem(statuses))?.let { problem ->
                call.respond(HttpStatusCode.BadRequest, problem)
                return@post
            }

            // The statuses estimates move tickets to have to be ones staff could pick
            val pickable = statuses.selectable()
            listOfNotNull(profile.estimateSentStatus, profile.estimateApprovedStatus, profile.estimateDeclinedStatus)
                .firstOrNull { it !in pickable }
                ?.let {
                    call.respond(HttpStatusCode.BadRequest, "An estimate can only move a ticket to a status staff can pick")
                    return@post
                }
            listOfNotNull(profile.partsOrderedStatus, profile.partsArrivedStatus)
                .firstOrNull { it !in pickable }
                ?.let {
                    call.respond(HttpStatusCode.BadRequest, "Ordering parts can only move a ticket to a status staff can pick")
                    return@post
                }

            val saved = profile.copy(
                currencySymbol = symbol,
                publicUrl = profile.publicUrl.trim().trimEnd('/'),
                statuses = statuses,
                taxClasses = profile.taxClasses.map { it.copy(name = it.name.trim(), rate = it.rate.trim()) },
                // One list per kind, the last sent winning, with blank and repeated checks dropped
                checklists = profile.checklists.associateBy { it.kind }.values.map { list ->
                    list.copy(items = list.items.map { it.trim().take(MAX_CHECK) }.filter { it.isNotEmpty() }.distinct().take(MAX_CHECKS))
                }
            )
            if (database.update(saved)) {
                call.audit(auditLog, AuditAction.SETTINGS_UPDATE, "shop", details = "Updated shop profile")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(UPDATE_TAG, "Couldn't save shop profile")
            }
        }
    }
}

private const val MAX_CHECK = 60
private const val MAX_CHECKS = 30
