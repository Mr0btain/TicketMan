package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AppointmentDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.AppointmentService
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.server.AppointmentPaths
import org.mr0btain.ticketman.server.request.AppointmentStatusRequest
import java.time.LocalDate

/**
 * The appointment calendar: anyone logged in can look, Normal access books and changes
 */
fun Route.appointmentRoute(
    database: AppointmentDatabase,
    service: AppointmentService,
    userDatabase: UserDatabase
) {
    authenticate("restricted") {
        get(AppointmentPaths.RANGE) {
            val from = call.queryParameters["from"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val to = call.queryParameters["to"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

            if (from == null || to == null) {
                call.respondText("Give from and to as days like 2026-10-01, from first", status = HttpStatusCode.BadRequest)
                return@get
            }

            call.respondAccount(service.between(from, to), HttpStatusCode.OK)
        }

        get(AppointmentPaths.GET) {
            val appointment = call.uuidParameter("identifier")?.let { database.get(it) }

            if (appointment != null) call.respond(HttpStatusCode.OK, appointment) else call.respond(HttpStatusCode.NotFound)
        }
    }

    authenticate {
        post(AppointmentPaths.ADD) {
            call.respondAccount(service.add(call.receive<Appointment>(), call.username(userDatabase), call.getIdentifierFromToken()), HttpStatusCode.Created)
        }

        post(AppointmentPaths.UPDATE) {
            call.respondAccount(service.update(call.receive<Appointment>(), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        post(AppointmentPaths.STATUS) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(service.setStatus(identifier, call.receive<AppointmentStatusRequest>().status, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        delete(AppointmentPaths.REMOVE) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            call.respondAccount(service.remove(identifier, call.getIdentifierFromToken()), HttpStatusCode.Accepted)
        }
    }
}
