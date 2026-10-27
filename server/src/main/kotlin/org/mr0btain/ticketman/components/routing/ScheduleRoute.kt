package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import org.mr0btain.ticketman.components.database.ScheduleDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.ScheduleService
import org.mr0btain.ticketman.data.schedule.ScheduleEntry
import org.mr0btain.ticketman.data.schedule.ScheduleRequest
import org.mr0btain.ticketman.data.schedule.ScheduleScope
import org.mr0btain.ticketman.server.SchedulePaths
import kotlin.uuid.Uuid

/**
 * The staff schedule, under /api/schedule: anyone logged in sees it, admins write it
 */
fun Route.scheduleRoute(
    database: ScheduleDatabase,
    service: ScheduleService,
    userDatabase: UserDatabase
) {
    authenticate("restricted") {
        // Where the caller works, every location's for an admin looking at all of them
        get(SchedulePaths.RANGE) {
            val from = call.queryParameters["from"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val to = call.queryParameters["to"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val user = call.queryParameters["user"]?.let { Uuid.parseOrNull(it) ?: return@get call.respond(HttpStatusCode.BadRequest, "That isn't a user") }

            if (from == null || to == null) {
                call.respondText("Give from and to as days like 2026-10-01, from first", status = HttpStatusCode.BadRequest)
                return@get
            }

            call.respondAccount(service.between(from, to, call.locationScope().identifier, user), HttpStatusCode.OK)
        }

        get(SchedulePaths.GET) {
            val entry = call.uuidParameter("identifier")?.let { database.get(it) }
            if (entry != null) call.respond(HttpStatusCode.OK, entry) else call.respond(HttpStatusCode.NotFound)
        }
    }

    authenticate("admin") {
        post(SchedulePaths.ADD) {
            call.respondAccount(
                service.add(call.receive<ScheduleRequest>(), call.username(userDatabase), call.getIdentifierFromToken(), call.workingLocation().identifier),
                HttpStatusCode.Created
            )
        }

        post(SchedulePaths.UPDATE) {
            val scope = call.scheduleScope() ?: return@post call.respond(HttpStatusCode.BadRequest, "Scope is one of ${ScheduleScope.entries.joinToString()}")
            call.respondAccount(service.update(call.receive<ScheduleEntry>(), scope, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        delete(SchedulePaths.REMOVE) {
            val identifier = call.uuidParameter("identifier") ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val scope = call.scheduleScope() ?: return@delete call.respond(HttpStatusCode.BadRequest, "Scope is one of ${ScheduleScope.entries.joinToString()}")
            call.respondAccount(service.remove(identifier, scope, call.getIdentifierFromToken()), HttpStatusCode.Accepted)
        }
    }
}

// `?scope=`, ONE when it isn't given, null when it isn't one
private fun RoutingCall.scheduleScope(): ScheduleScope? =
    queryParameters["scope"]?.let { name -> ScheduleScope.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } } ?: ScheduleScope.ONE.takeIf { queryParameters["scope"] == null }
