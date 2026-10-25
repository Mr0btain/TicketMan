package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.TimeClockDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.StationPin
import org.mr0btain.ticketman.components.service.TimeClockService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.data.timeclock.ClockState
import org.mr0btain.ticketman.data.timeclock.TimeEntry
import org.mr0btain.ticketman.server.TimeClockPaths
import org.mr0btain.ticketman.server.request.PinLoginRequest
import java.time.LocalDate

/**
 * The time clock: clocking in and out at a station by PIN or from your own device, who's on now, your own time,
 * and timesheets and fixing times for admins
 */
fun Route.timeClockRoute(
    service: TimeClockService,
    database: TimeClockDatabase,
    userService: UserService,
    userDatabase: UserDatabase
) {
    // A station and a PIN instead of a login, so anyone can clock in on the counter without signing in
    post(TimeClockPaths.PUNCH) {
        when (val check = userService.checkStationPin(call.receive<PinLoginRequest>())) {
            is StationPin.Failed -> call.respondPinFailure(check.result)
            is StationPin.Passed -> call.respondAccount(service.punch(check.user, check.station.name), HttpStatusCode.OK)
        }
    }

    authenticate("restricted") {
        get(TimeClockPaths.ME) {
            val user = call.getIdentifierFromToken()

            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            call.respond(HttpStatusCode.OK, ClockState(database.open(user)))
        }

        post(TimeClockPaths.ME_PUNCH) {
            val user = call.getIdentifierFromToken()?.let { userDatabase.getById(it) }

            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            call.respondAccount(service.punch(user, ""), HttpStatusCode.OK)
        }

        get(TimeClockPaths.ME_TIMESHEET) {
            val user = call.getIdentifierFromToken()
            val (from, to) = call.days() ?: return@get

            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            call.respondAccount(service.timesheet(from, to, user), HttpStatusCode.OK)
        }

        get(TimeClockPaths.NOW) {
            val open = database.allOpen()
            if (open != null) call.respond(HttpStatusCode.OK, open) else call.respond(HttpStatusCode.InternalServerError)
        }
    }

    authenticate("admin") {
        get(TimeClockPaths.TIMESHEET) {
            val (from, to) = call.days() ?: return@get
            val user = call.queryParameters["user"]?.let { runCatching { kotlin.uuid.Uuid.parse(it) }.getOrNull() }

            if (call.queryParameters["user"] != null && user == null) {
                call.respondText("That isn't a user", status = HttpStatusCode.BadRequest)
                return@get
            }

            call.respondAccount(service.timesheet(from, to, user), HttpStatusCode.OK)
        }

        post(TimeClockPaths.ENTRY_ADD) {
            call.respondAccount(service.add(call.receive<TimeEntry>(), call.username(userDatabase), call.getIdentifierFromToken()), HttpStatusCode.Created)
        }

        post(TimeClockPaths.ENTRY_UPDATE) {
            call.respondAccount(service.update(call.receive<TimeEntry>(), call.username(userDatabase), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        delete(TimeClockPaths.ENTRY_REMOVE) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            call.respondAccount(service.remove(identifier, call.getIdentifierFromToken()), HttpStatusCode.Accepted)
        }
    }
}

// The from and to days, or null after answering 400
private suspend fun RoutingCall.days(): Pair<LocalDate, LocalDate>? {
    val from = queryParameters["from"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val to = queryParameters["to"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    if (from == null || to == null) {
        respondText("Give from and to as days like 2026-10-01, from first", status = HttpStatusCode.BadRequest)
        return null
    }

    return from to to
}
