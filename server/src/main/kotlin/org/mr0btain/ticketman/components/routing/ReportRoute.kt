package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.ReportDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.report.CommissionRate
import org.mr0btain.ticketman.data.report.ReportRules
import org.mr0btain.ticketman.server.ReportPaths
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Reports for admins: how the shop did over some days, and the commission rates the techs' numbers use
 */
fun Route.reportRoute(
    database: ReportDatabase,
    userDatabase: UserDatabase,
    auditLog: AuditLogDatabase
) {
    authenticate("admin") {
        get(ReportPaths.PERIOD) {
            val from = call.queryParameters["from"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val to = call.queryParameters["to"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

            if (from == null || to == null || to < from) {
                call.respondText("Give from and to as days like 2026-10-01, from first", status = HttpStatusCode.BadRequest)
                return@get
            }

            if (ChronoUnit.DAYS.between(from, to) >= ReportRules.MAX_DAYS) {
                call.respondText("A report covers at most ${ReportRules.MAX_DAYS} days", status = HttpStatusCode.BadRequest)
                return@get
            }

            val report = database.period(from, to)
            if (report != null) call.respond(HttpStatusCode.OK, report) else call.respond(HttpStatusCode.InternalServerError)
        }

        get(ReportPaths.COMMISSIONS) {
            val rates = database.commissions()
            if (rates != null) call.respond(HttpStatusCode.OK, rates) else call.respond(HttpStatusCode.InternalServerError)
        }

        post(ReportPaths.COMMISSIONS) {
            val rates = call.receive<List<CommissionRate>>()

            val problem = when {
                rates.any { it.basisPoints !in 0..CommissionRate.MAX_BASIS_POINTS } -> "Commission has to be from 0% to 100%"
                rates.map { it.userIdentifier }.toSet().size != rates.size -> "Each user can only have one rate"
                rates.any { userDatabase.getById(it.userIdentifier) == null } -> "That user doesn't exist"
                else -> null
            }

            if (problem != null) {
                call.respondText(problem, status = HttpStatusCode.BadRequest)
                return@post
            }

            if (database.setCommissions(rates)) {
                call.audit(auditLog, AuditAction.UPDATE, "commissions", details = "Commission rates set for ${rates.count { it.basisPoints > 0 }} users")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}
