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
import org.mr0btain.ticketman.data.station.KioskSettings
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationStatus
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

        // Log out one device. Its access tokens stop working with it
        delete("/sessions/{sessionId}") {
            val session = call.parameters["sessionId"]?.let { database.tokenDatabase.findBySessionId(it) }

            if (session == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            database.tokenDatabase.remove(session.sessionId)
            call.audit(auditLog, AuditAction.SESSION_REVOKE, "user", session.userIdentifier, "Logged out ${session.deviceName.ifBlank { "a device" }}")
            call.respond(HttpStatusCode.Accepted)
            AppLogger.m(SESSIONS_TAG, "${session.sessionId} session revoked")
        }

        // Log a user out of every device
        delete("/sessions/user/{identifier}") {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null || database.userDatabase.getById(identifier) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            database.tokenDatabase.removeAllForUser(identifier.toString())
            call.audit(auditLog, AuditAction.SESSION_REVOKE, "user", identifier, "Logged out everywhere")
            call.respond(HttpStatusCode.Accepted)
        }

        // ------------------------------------------ Stations ------------------------------------------

        get("/stations") {
            call.respond(HttpStatusCode.OK, database.stationDatabase.getAll())
        }

        post("/stations/{identifier}/approve") {
            val identifier = call.uuidParameter("identifier")

            if (identifier != null && database.stationDatabase.approve(identifier, call.username(database.userDatabase))) {
                call.audit(auditLog, AuditAction.UPDATE, "station", identifier, "Approved ${database.stationDatabase.getById(identifier)?.name}")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // Kiosk mode only makes sense on a station that's been approved
        post("/stations/{identifier}/kiosk") {
            val station = call.uuidParameter("identifier")?.let { database.stationDatabase.getById(it) }
            val settings = call.receive<KioskSettings>()

            when {
                station == null -> call.respond(HttpStatusCode.NotFound)

                station.status != StationStatus.APPROVED -> call.respond(HttpStatusCode.Conflict, "Approve the station first")

                settings.idleTimeoutSeconds !in Station.IDLE_TIMEOUT_RANGE -> call.respond(
                    HttpStatusCode.BadRequest,
                    "Idle timeout has to be ${Station.IDLE_TIMEOUT_RANGE.first} to ${Station.IDLE_TIMEOUT_RANGE.last} seconds"
                )

                else -> {
                    database.stationDatabase.setKiosk(station.identifier, settings)
                    call.audit(
                        auditLog, AuditAction.SETTINGS_UPDATE, "station", station.identifier,
                        if (settings.enabled) "Kiosk on for ${station.name}, locks after ${settings.idleTimeoutSeconds}s" else "Kiosk off for ${station.name}"
                    )
                    call.respond(HttpStatusCode.Accepted)
                }
            }
        }

        // Removing a station also signs out whoever is PINned in there
        delete("/stations/{identifier}") {
            val station = call.uuidParameter("identifier")?.let { database.stationDatabase.getById(it) }

            if (station == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            database.stationDatabase.remove(station.identifier)
            database.tokenDatabase.removePinSessionsForDevice(station.deviceId)
            call.audit(auditLog, AuditAction.DELETE, "station", station.identifier, "Removed ${station.name}")
            call.respond(HttpStatusCode.Accepted)
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
