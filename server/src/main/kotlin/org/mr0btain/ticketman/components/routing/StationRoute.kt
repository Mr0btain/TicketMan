package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.StationDatabase
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.server.request.StationRegistrationRequest
import org.mr0btain.ticketman.server.response.StationRegistrationResponse

/**
 * Registering a device as a station. Needs NORMAL access, then an admin approves it
 */
fun Route.stationRoute(
    database: StationDatabase,
    userDatabase: UserDatabaseImpl,
    auditLog: AuditLogDatabase
) {
    authenticate {
        post("/register") {
            val request = call.receive<StationRegistrationRequest>()

            if (request.deviceId.isBlank() || request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Device and name are required")
                return@post
            }

            val registered = database.register(request.deviceId.trim(), request.name.trim(), call.username(userDatabase))

            if (registered == null) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            val (station, credentials) = registered
            call.audit(auditLog, AuditAction.CREATE, "station", station.identifier, "Registered ${station.name}")
            call.respond(HttpStatusCode.Created, StationRegistrationResponse(station, credentials))
        }
    }
}
