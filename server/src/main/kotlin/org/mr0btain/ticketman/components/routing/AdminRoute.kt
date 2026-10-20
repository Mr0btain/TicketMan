package org.mr0btain.ticketman.components.routing

import org.mr0btain.ticketman.server.AdminPaths
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.*
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.plugins.ForwardedByProxy
import org.mr0btain.ticketman.components.database.debug.seedTestData
import org.mr0btain.ticketman.components.service.AdminService
import org.mr0btain.ticketman.components.service.BackupService
import org.mr0btain.ticketman.components.service.RestoreService
import org.mr0btain.ticketman.components.service.ImportService
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.components.service.copyToFile
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.station.KioskSettings
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.BackupRequest
import org.mr0btain.ticketman.server.request.SeedRequest
import java.time.LocalDate
import kotlin.time.Clock

/**
 * Admin only routes for the admin screen
 */
fun Route.adminRoute(
    database: LocalDatabase,
    adminService: AdminService,
    backupService: BackupService,
    restoreService: RestoreService
) {
    val TAG = "AdminRoute"
    val STATS_TAG = "$TAG:Stats"
    val LOW_STOCK_TAG = "$TAG:LowStock"
    val SESSIONS_TAG = "$TAG:Sessions"
    val BACKUP_TAG = "$TAG:Backup"
    val RESTORE_TAG = "$TAG:Restore"
    val SEED_TAG = "$TAG:Seed"
    val AUDIT_TAG = "$TAG:Audit"

    val auditLog = database.auditLogDatabase
    val importService = ImportService(database.customerDatabase, database.inventoryDatabase, auditLog)

    // The token in the link is the login here, so a browser can download it directly. Range requests work, so
    // a dropped download can carry on
    get(AdminPaths.BACKUP_DOWNLOAD) {
        val found = call.parameters["id"]?.let { backupService.fileFor(it, call.request.queryParameters["token"]) }

        if (found == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val (file, fileName) = found
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, fileName).toString()
        )
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondFile(file)
    }

    authenticate("admin") {
        // Overview numbers
        get(AdminPaths.STATS) {
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

        // End of day numbers, ?date=yyyy-mm-dd or today
        get(AdminPaths.DAILY_REPORT) {
            val date = call.queryParameters["date"]?.let {
                try {
                    LocalDate.parse(it)
                } catch (ex: Exception) {
                    null
                }
            }

            if (call.queryParameters["date"] != null && date == null) {
                call.respond(HttpStatusCode.BadRequest, "Date has to look like 2026-10-17")
                return@get
            }

            val report = database.statsDatabase.dailyReport(date ?: LocalDate.now())

            if (report != null) call.respond(HttpStatusCode.OK, report) else call.respond(HttpStatusCode.InternalServerError)
        }

        get(AdminPaths.LOW_STOCK) {
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
        get(AdminPaths.SESSIONS) {
            call.respond(HttpStatusCode.OK, adminService.getSessions())
        }

        // Log out one device. Its access tokens stop working with it
        delete(AdminPaths.SESSION) {
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
        delete(AdminPaths.USER_SESSIONS) {
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

        get(AdminPaths.STATIONS) {
            call.respond(HttpStatusCode.OK, database.stationDatabase.getAll())
        }

        post(AdminPaths.STATION_APPROVE) {
            val identifier = call.uuidParameter("identifier")

            if (identifier != null && database.stationDatabase.approve(identifier, call.username(database.userDatabase))) {
                call.audit(auditLog, AuditAction.UPDATE, "station", identifier, "Approved ${database.stationDatabase.getById(identifier)?.name}")
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // Kiosk mode only makes sense on a station that's been approved
        post(AdminPaths.STATION_KIOSK) {
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
        delete(AdminPaths.STATION) {
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

        get(AdminPaths.SERVER) {
            val info = adminService.getServerInfo(
                clientAddress = call.request.origin.remoteHost,
                forwarded = call.attributes.getOrNull(ForwardedByProxy) == true
            )
            call.respond(HttpStatusCode.OK, info)
        }

        // Makes a new HTTPS certificate, used from the next start. Only for one the server made itself
        post(AdminPaths.CERTIFICATE_RENEW) {
            if (!adminService.canRenewCertificate) {
                call.respond(HttpStatusCode.Conflict, "This server uses a supplied certificate, replace SSL_KEYSTORE instead")
                return@post
            }

            val info = adminService.renewCertificate()

            if (info == null) {
                call.respond(HttpStatusCode.InternalServerError)
                return@post
            }

            call.audit(auditLog, AuditAction.SETTINGS_UPDATE, "certificate", details = "New certificate for the next start, ${info.pendingFingerprint}")
            call.respond(HttpStatusCode.Created, info)
        }

        delete(AdminPaths.CERTIFICATE_RENEW) {
            if (adminService.cancelCertificateRenewal()) {
                call.audit(auditLog, AuditAction.SETTINGS_UPDATE, "certificate", details = "Waiting certificate cancelled")
            }
            call.respond(HttpStatusCode.Accepted)
        }

        // Builds a backup archive on the server and returns a link to download it from. Can take a while, longer
        // than a proxy waits, so clients use BACKUP_START. Kept for older clients
        post(AdminPaths.BACKUP) {
            val request = call.receive<BackupRequest>()
            val ticket = backupService.prepare(request.includeImages)

            if (ticket == null) {
                call.respondText("The backup couldn't be made, check the server log", status = HttpStatusCode.InternalServerError)
                AppLogger.i(BACKUP_TAG, "Backup failed")
                return@post
            }

            val contents = if (ticket.includesImages) "database and photos" else "database only"
            call.audit(auditLog, AuditAction.BACKUP, "database", details = "$contents, ${ticket.sizeBytes} bytes")
            call.respond(HttpStatusCode.Created, ticket)
        }

        // Starts building a backup and answers straight away; the client asks after it with BACKUP_STATUS. A proxy
        // would give up on POST BACKUP while a big one is being built
        post(AdminPaths.BACKUP_START) {
            val request = call.receive<BackupRequest>()
            val actor = call.getIdentifierFromToken()

            val job = backupService.start(request.includeImages) { ticket ->
                if (ticket == null) {
                    AppLogger.i(BACKUP_TAG, "Backup failed")
                    return@start
                }
                val contents = if (ticket.includesImages) "database and photos" else "database only"
                auditLog.log(actor, AuditAction.BACKUP, "database", null, "$contents, ${ticket.sizeBytes} bytes")
            }

            call.respond(HttpStatusCode.Accepted, job)
        }

        get(AdminPaths.BACKUP_STATUS) {
            val id = call.parameters["id"].orEmpty()
            val job = backupService.job(id)

            if (job == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, job)
        }

        // Upload a backup archive (the raw zip as the body) to restore from on the next start
        post(AdminPaths.RESTORE) {
            // Room for the upload and for it unpacked
            val space = restoreService.usableSpace()
            val length = call.request.contentLength()

            if (length != null && length * 2 > space) {
                call.respondText("Not enough disk space on the server for that backup", status = HttpStatusCode.InsufficientStorage)
                return@post
            }

            val upload = restoreService.newUploadFile()
            val received = try {
                call.receiveChannel().copyToFile(upload, space / 2)
            } catch (ex: Exception) {
                AppLogger.i(RESTORE_TAG, "Restore upload cut short: ${ex.message}")
                upload.delete()
                call.respondText("The upload was cut off", status = HttpStatusCode.BadRequest)
                return@post
            }

            if (received == null) {
                upload.delete()
                call.respondText("Not enough disk space on the server for that backup", status = HttpStatusCode.InsufficientStorage)
                return@post
            }

            restoreService.stage(upload)
                .onSuccess { pending ->
                    call.audit(auditLog, AuditAction.BACKUP, "restore", details = "Restore from ${pending.manifest.createdAt} staged, ${pending.sizeBytes} bytes")
                    call.respond(HttpStatusCode.Accepted, pending)
                }
                .onFailure { call.respondText(it.message ?: "That backup can't be restored", status = HttpStatusCode.BadRequest) }
        }

        // The restore waiting for a restart, 204 when there isn't one
        get(AdminPaths.RESTORE) {
            val pending = restoreService.pending()

            if (pending == null) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.OK, pending)
        }

        delete(AdminPaths.RESTORE) {
            if (restoreService.cancel()) call.audit(auditLog, AuditAction.BACKUP, "restore", details = "Waiting restore cancelled")
            call.respond(HttpStatusCode.Accepted)
        }

        // Exits so whatever runs the server starts it again, applying a waiting restore or certificate. Answers first.
        // Only when RESTART_ON_EXIT says something will, or the server would just stay down
        post(AdminPaths.RESTART) {
            if (!adminService.restart.enabled) {
                call.respondText(
                    "This server can't restart itself. Restart it by hand, or set RESTART_ON_EXIT=true where something starts it again",
                    status = HttpStatusCode.Conflict
                )
                return@post
            }

            // A second ask while one is on its way is answered the same, without logging it again
            if (adminService.restart.request()) {
                val details = if (restoreService.pending() != null) "Restart to restore a backup" else "Restart"
                call.audit(auditLog, AuditAction.SETTINGS_UPDATE, "server", details = details)
            }
            call.respond(HttpStatusCode.Accepted)
        }

        // Customers or inventory from another system, read from a CSV by the client. Ones already here are skipped.
        // ?dryRun=true answers what would happen without adding anything, for the preview
        post(AdminPaths.IMPORT_CUSTOMERS) {
            val list = call.receive<List<Customer>>()
            if (list.size > ImportService.MAX_ROWS) return@post call.respondText("At most ${ImportService.MAX_ROWS} at once", status = HttpStatusCode.BadRequest)

            importService.customers(list, call.queryParameters["dryRun"] == "true", call.getIdentifierFromToken())
                ?.let { call.respond(HttpStatusCode.OK, it) }
                ?: call.respond(HttpStatusCode.InternalServerError)
        }

        post(AdminPaths.IMPORT_INVENTORY) {
            val list = call.receive<List<Inventory>>()
            if (list.size > ImportService.MAX_ROWS) return@post call.respondText("At most ${ImportService.MAX_ROWS} at once", status = HttpStatusCode.BadRequest)

            importService.inventory(list, call.queryParameters["dryRun"] == "true", call.getIdentifierFromToken())
                ?.let { call.respond(HttpStatusCode.OK, it) }
                ?: call.respond(HttpStatusCode.InternalServerError)
        }

        // Mock data, only on servers started in development mode
        post(AdminPaths.SEED) {
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
        get(AdminPaths.AUDIT) {
            val request = call.pagedRequest() ?: return@get call.respondBadQuery()

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
