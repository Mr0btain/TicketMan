package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.LocationDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.server.LocationPaths

/**
 * The shop's locations, under /api/location. Anyone logged in reads them, to pick where they work; admins add and
 * change them. One is never deleted, since its tickets and invoices still point at it: it's retired instead
 */
fun Route.locationRoute(
    database: LocationDatabase,
    auditLog: AuditLogDatabase
) {
    authenticate("restricted") {
        get {
            call.respond(HttpStatusCode.OK, database.all())
        }
    }

    authenticate("admin") {
        post(LocationPaths.ADD) {
            val location = call.receive<Location>().cleaned().copy(main = false)
            val all = database.all()

            if (all.any { it.identifier == location.identifier }) return@post call.respond(HttpStatusCode.Conflict, "That location already exists")
            location.problem(all)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
            if (!database.add(location)) return@post call.respond(HttpStatusCode.InternalServerError)

            call.audit(auditLog, AuditAction.CREATE, "location", location.identifier, "Added ${location.label}")
            call.respond(HttpStatusCode.Created, database.get(location.identifier)!!)
        }

        post(LocationPaths.UPDATE) {
            val received = call.receive<Location>()
            val all = database.all()
            val existing = all.firstOrNull { it.identifier == received.identifier } ?: return@post call.respond(HttpStatusCode.NotFound)
            // Whether it's the main one is the server's to say
            val location = received.cleaned().copy(main = existing.main)

            location.problem(all - existing)?.let { return@post call.respond(HttpStatusCode.BadRequest, it) }
            if (!database.update(location)) return@post call.respond(HttpStatusCode.NotFound)

            val details = when {
                location.retired && !existing.retired -> "Retired ${location.label}"
                !location.retired && existing.retired -> "Brought back ${location.label}"
                location.code != existing.code -> "${existing.label} numbers now start ${location.code.ifBlank { "with no code" }}"
                else -> "Updated ${location.label}"
            }
            call.audit(auditLog, AuditAction.UPDATE, "location", location.identifier, details)
            call.respond(HttpStatusCode.OK, database.get(location.identifier)!!)
        }
    }
}

// Codes are capitals, everything else is trimmed
private fun Location.cleaned(): Location =
    copy(
        code = code.trim().uppercase(),
        name = name.trim(),
        taxRate = taxRate?.trim()?.ifBlank { null },
        taxClassRates = taxClassRates.mapValues { it.value.trim() }.filterValues { it.isNotBlank() }
    )
