package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.database.debug.seedTestData
import org.mr0btain.ticketman.components.service.AdminService
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.SeedRequest
import kotlin.time.Clock

/**
 * Admin only routes for the admin screen
 */
fun Route.adminRoute(
    database: LocalDatabase,
    adminService: AdminService
) {
    val TAG = "AdminRoute"
    val STATS_TAG = "$TAG:Stats"
    val LOW_STOCK_TAG = "$TAG:LowStock"
    val SESSIONS_TAG = "$TAG:Sessions"
    val BACKUP_TAG = "$TAG:Backup"
    val SEED_TAG = "$TAG:Seed"
    val AUDIT_TAG = "$TAG:Audit"

    val auditLog = database.auditLogDatabase

    authenticate("admin") {
        // Overview numbers
        get("/stats") {
            val lowStockThreshold = call.queryParameters["lowStock"]?.toIntOrNull() ?: DEFAULT_LOW_STOCK_THRESHOLD
            val recentLimit = (call.queryParameters["recent"]?.toIntOrNull() ?: DEFAULT_RECENT_LIMIT).coerceIn(0, MAX_LIMIT)

            val result = database.statsDatabase.getAdminStats(
                lowStockThreshold = lowStockThreshold,
                recentLimit = recentLimit,
                activeSessions = database.tokenDatabase.count()
            )

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(STATS_TAG, "Couldn't build stats")
            }
        }

        get("/inventory/low-stock") {
            val threshold = call.queryParameters["threshold"]?.toIntOrNull() ?: DEFAULT_LOW_STOCK_THRESHOLD
            val limit = (call.queryParameters["limit"]?.toIntOrNull() ?: MAX_LIMIT).coerceIn(1, MAX_LIMIT)

            val result = database.statsDatabase.getLowStock(threshold, limit)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(LOW_STOCK_TAG, "Couldn't get low stock")
            }
        }

        // Logged in users
        get("/sessions") {
            call.respond(HttpStatusCode.OK, adminService.getSessions())
        }

        // Log a user out by dropping their refresh token, their access tokens stop working with it
        delete("/sessions/{identifier}") {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            if (database.tokenDatabase.findById(identifier.toString()) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            database.tokenDatabase.remove(identifier.toString())
            call.audit(auditLog, AuditAction.SESSION_REVOKE, "user", identifier)
            call.respond(HttpStatusCode.Accepted)
            AppLogger.m(SESSIONS_TAG, "$identifier session revoked")
        }

        get("/server") {
            call.respond(HttpStatusCode.OK, adminService.getServerInfo())
        }

        // Zip of the database
        get("/backup") {
            val bytes = adminService.createBackup()

            if (bytes == null) {
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(BACKUP_TAG, "Backup failed")
                return@get
            }

            val fileName = "ticketman-backup-${Clock.System.now().toEpochMilliseconds()}.zip"

            call.audit(auditLog, AuditAction.BACKUP, "database", details = "${bytes.size} bytes")
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, fileName).toString()
            )
            call.respondBytes(bytes, ContentType.Application.Zip)
        }

        // Mock data, only on servers started in development mode
        post("/seed") {
            if (!call.application.developmentMode) {
                call.respond(HttpStatusCode.Forbidden, "Seeding is only allowed in development mode")
                return@post
            }

            val request = call.receive<SeedRequest>()

            if (listOf(request.tickets, request.customers, request.inventory, request.users).any { it !in 0..MAX_SEED }) {
                call.respond(HttpStatusCode.BadRequest, "Counts must be between 0 and $MAX_SEED")
                return@post
            }

            seedTestData(database, request)

            call.audit(auditLog, AuditAction.SEED, "database", details = request.toString())
            call.respond(HttpStatusCode.Accepted)
            AppLogger.m(SEED_TAG, "Seeded $request")
        }

        // Audit log, newest first
        post("/audit/fetch") {
            val request = call.receive<PagedRequest>()

            val result = auditLog.getPagedItems(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
                AppLogger.i(AUDIT_TAG, "Couldn't get audit log")
            }
        }
    }
}

private const val DEFAULT_LOW_STOCK_THRESHOLD = 5
private const val DEFAULT_RECENT_LIMIT = 10
private const val MAX_LIMIT = 200
private const val MAX_SEED = 5000
