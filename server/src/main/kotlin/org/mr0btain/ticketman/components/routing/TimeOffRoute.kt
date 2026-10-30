package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.TimeOffDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.TimeOffService
import org.mr0btain.ticketman.data.schedule.TimeOffAllowance
import org.mr0btain.ticketman.data.schedule.TimeOffDecision
import org.mr0btain.ticketman.data.schedule.TimeOffRequest
import org.mr0btain.ticketman.data.schedule.TimeOffStatus
import org.mr0btain.ticketman.server.TimeOffPaths

/**
 * Time off, under /api/timeoff: anyone logged in asks for their own and sees their balance, admins answer
 * requests and set allowances
 */
fun Route.timeOffRoute(
    database: TimeOffDatabase,
    service: TimeOffService,
    userDatabase: UserDatabase
) {
    authenticate("restricted") {
        get(TimeOffPaths.ME) {
            val user = call.getIdentifierFromToken()?.let { userDatabase.getById(it) } ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val year = call.year(service.thisYear()) ?: return@get call.respond(HttpStatusCode.BadRequest, "Year is a number like 2026")
            call.respond(HttpStatusCode.OK, service.mine(user, year))
        }

        post(TimeOffPaths.REQUEST) {
            val user = call.getIdentifierFromToken()?.let { userDatabase.getById(it) } ?: return@post call.respond(HttpStatusCode.Unauthorized)
            call.respondAccount(service.request(user, call.receive<TimeOffRequest>()), HttpStatusCode.Created)
        }

        post(TimeOffPaths.WITHDRAW) {
            val user = call.getIdentifierFromToken()?.let { userDatabase.getById(it) } ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.withdraw(user, identifier), HttpStatusCode.OK)
        }
    }

    authenticate("admin") {
        get(TimeOffPaths.REQUESTS) {
            val status = call.queryParameters["status"]?.let { name -> TimeOffStatus.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return@get call.respond(HttpStatusCode.BadRequest, "Status is one of ${TimeOffStatus.entries.joinToString()}") }
            database.list(status)?.let { call.respond(HttpStatusCode.OK, it) } ?: call.respond(HttpStatusCode.InternalServerError)
        }

        post(TimeOffPaths.APPROVE) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.approve(identifier, call.username(userDatabase), call.getIdentifierFromToken(), call.workingLocation().identifier), HttpStatusCode.OK)
        }

        post(TimeOffPaths.DECLINE) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.decline(identifier, call.receive<TimeOffDecision>().reason, call.username(userDatabase), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        get(TimeOffPaths.BALANCES) {
            val year = call.year(service.thisYear()) ?: return@get call.respond(HttpStatusCode.BadRequest, "Year is a number like 2026")
            call.respond(HttpStatusCode.OK, service.balances(year))
        }

        post(TimeOffPaths.ALLOWANCE) {
            val identifier = call.uuidParameter("identifier") ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respondAccount(service.setAllowance(identifier, call.receive<TimeOffAllowance>(), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }
    }
}

// `?year=`, [default] when it isn't given, null when it isn't a year
private fun RoutingCall.year(default: Int): Int? {
    val text = queryParameters["year"] ?: return default
    return text.toIntOrNull()?.takeIf { it in 2000..2200 }
}
