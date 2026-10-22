 package org.mr0btain.ticketman

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.database.configureDatabase
import org.mr0btain.ticketman.components.database.debug.seedTestData
import org.mr0btain.ticketman.components.plugins.configureContentNegotiation
import org.mr0btain.ticketman.components.plugins.configureCors
import org.mr0btain.ticketman.components.plugins.configureProxy
import org.mr0btain.ticketman.components.plugins.configureSecurity
import org.mr0btain.ticketman.components.plugins.configureTransfers
import org.mr0btain.ticketman.components.routing.configureRouting
import org.mr0btain.ticketman.components.service.AccountService
import org.mr0btain.ticketman.components.service.AdminService
import org.mr0btain.ticketman.components.service.BackupSchedule
import org.mr0btain.ticketman.components.service.BackupService
import org.mr0btain.ticketman.components.service.Housekeeping
import org.mr0btain.ticketman.components.service.RestoreService
import org.mr0btain.ticketman.components.service.CheckInService
import org.mr0btain.ticketman.components.service.ImageService
import org.mr0btain.ticketman.components.service.InvoiceService
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.components.service.LoginLimiter
import org.mr0btain.ticketman.components.routing.waitText
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.server.response.LoginLockKind
import org.mr0btain.ticketman.components.service.ServerRestart
import org.mr0btain.ticketman.components.service.ServerSecret
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.components.proxy.ProxySettings
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.util.AppDir
import java.io.File
import java.security.SecureRandom
import java.time.ZoneId
import java.util.Base64


/**
 * Main. Plain HTTP only, HTTPS is a reverse proxy's job
 */

fun main(args: Array<String>) {
    EngineMain.main(args)
}

/** `DATA_PATH`, falling back to the platform folder only when it isn't set, so that isn't made for nothing */
fun resolveDataPath(config: ApplicationConfig): String {
    val path = config.propertyOrNull("env.dataPath")?.getString().orEmpty()
    return path.ifBlank { AppDir().path?.toString().orEmpty() }
}

fun Application.module() {
    AppLogger.debug = developmentMode
    val dataPath = resolveDataPath(environment.config)

    if (dataPath.isBlank()) {
        AppLogger.e("SERVER", "No DATA_PATH set and no app data folder found, so there's nowhere to keep data")
    } else {
        AppLogger.m("SERVER", "Path for Files: $dataPath")
        val database = configureDatabase(dataPath)
        // Ktor stops on SIGTERM (docker stop) and on a restart, after the last requests are answered
        monitor.subscribe(ApplicationStopped) { database.close() }
        val secret = ServerSecret.resolve(environment.config.propertyOrNull("jwt.secret")?.getString().orEmpty(), dataPath)
        val jwtService = JwtService(this, database.userDatabase, database.tokenDatabase, secret)
        val userService = UserService(
            database = database.userDatabase,
            jwtService = jwtService,
            tokenDatabase = database.tokenDatabase,
            inviteDatabase = database.inviteDatabase,
            stationDatabase = database.stationDatabase,
            auditLog = database.auditLogDatabase
        )
        val accountService = AccountService(database.userDatabase, database.inviteDatabase, userService, jwtService, database.auditLogDatabase)
        val proxy = ProxySettings.from(environment.config)
        logStartupNotes(
            startupNotes(
                proxy = proxy,
                httpPort = environment.config.propertyOrNull("ktor.deployment.port")?.getString()?.toIntOrNull() ?: 8080,
                retired = RETIRED_SETTINGS.filter { !System.getenv(it).isNullOrBlank() },
                oldCertificateFolder = File(dataPath, "server/tls").isDirectory,
                zone = ZoneId.systemDefault(),
                tzSet = !System.getenv("TZ").isNullOrBlank()
            )
        )

        configureContentNegotiation()
        configureSecurity(jwtService)
        configureTransfers()
        configureProxy(proxy)
        configureCors(environment.config.propertyOrNull("cors.hosts")?.getString().orEmpty())
        val invoiceService = InvoiceService(
            invoices = database.invoiceDatabase,
            tickets = database.ticketDatabase,
            inventory = database.inventoryDatabase,
            shop = database.shopDatabase,
            auditLog = database.auditLogDatabase,
            work = database.workDatabase
        )

        val checkInService = CheckInService(
            tickets = database.ticketDatabase,
            customers = database.customerDatabase,
            users = database.userDatabase,
            invoices = database.invoiceDatabase,
            shop = database.shopDatabase,
            auditLog = database.auditLogDatabase
        )

        val imageService = ImageService(database.imageDatabase, database.imageStore)
        val backupService = BackupService(
            database = database,
            images = database.imageDatabase,
            imageStore = database.imageStore,
            backupsFolder = File(database.databaseDirectory.parentFile, "backups"),
            schedule = BackupSchedule.from(environment.config),
            auditLog = database.auditLogDatabase
        )

        val restoreService = RestoreService(database.databaseDirectory.parentFile)
        val adminService = AdminService(
            database = database,
            serverName = environment.config.propertyOrNull("env.name")?.getString() ?: "Server",
            dataPath = dataPath,
            developmentMode = developmentMode,
            backupService = backupService,
            restoreService = restoreService,
            proxy = proxy,
            restart = ServerRestart.from(environment.config)
        )
        val housekeeping = Housekeeping.from(environment.config)

        // Locking a name or address out is worth an admin knowing about, each wrong password is logged already
        val loginLimiter = LoginLimiter(
            onLock = { lock ->
                val what = if (lock.kind == LoginLockKind.USERNAME) "user" else "address"
                val wait = waitText(((lock.lockedUntil - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000)
                database.auditLogDatabase.log(
                    null, AuditAction.LOGIN_FAILED, what, lock.key, "Locked out for $wait after ${lock.failures} wrong passwords",
                    actorName = lock.key.takeIf { lock.kind == LoginLockKind.USERNAME }
                )
            },
            onSurge = { failures ->
                database.auditLogDatabase.log(
                    null, AuditAction.LOGIN_FAILED, "server", details = "$failures wrong passwords in ${LoginLimiter.WINDOW.inWholeMinutes} minutes, " +
                        "every address that guessed wrong now waits"
                )
            }
        )

        configureRouting(
            userService, accountService, adminService, invoiceService, checkInService, imageService, backupService,
            restoreService, database, loginLimiter
        )

        // Clears out photos deleted or never saved a day ago, backups whose link has run out, sessions that have
        // expired, old audit entries and old restore leftovers, and makes the daily backup. Stops with the application
        launch(Dispatchers.IO) {
            while (isActive) {
                imageService.sweep()
                backupService.sweep()
                database.tokenDatabase.removeExpired()
                housekeeping.auditCutoff()?.let { database.auditLogDatabase.removeOlderThan(it) }
                restoreService.sweepLeftovers(housekeeping.restoreLeftoverAge)
                backupService.runSchedule()
                delay(ImageService.SWEEP_INTERVAL)
            }
        }

        AppLogger.m("SERVER", "Server initialized!")

        ensureAdminExists(database, environment.config.propertyOrNull("env.adminPassword")?.getString().orEmpty())

        val generateTestData = false

        if (generateTestData) {
            seedTestData(
                database = database,
                request = SeedRequest(tickets = 1000, customers = 400, inventory = 100, users = 25)
            )
        }
    }
}

/**
 * A fresh database has no users, so nobody could log in to make one. Creates an "admin" user when there
 * are no admins, using [password] or a random one printed to the log.
 */
private fun ensureAdminExists(database: LocalDatabase, password: String) {
    val TAG = "SERVER:EnsureAdmin"

    if (database.userDatabase.getAdmin() != null) return

    val username = if (database.userDatabase.getByUsername(DEFAULT_ADMIN_USERNAME) == null) DEFAULT_ADMIN_USERNAME
        else "${DEFAULT_ADMIN_USERNAME}_${System.currentTimeMillis()}"
    val adminPassword = password.ifBlank {
        ByteArray(12).also { SecureRandom().nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    }

    val created = database.userDatabase.add(
        User(
            username = username,
            password = adminPassword,
            name = "admin",
            accessLevel = AccessLevel.ADMIN
        )
    )

    if (!created) {
        AppLogger.e(TAG, "No admin found and one couldn't be created")
        return
    }

    if (password.isBlank()) {
        AppLogger.m(TAG, "No admin found, created \"$username\" with password: $adminPassword  (change it from the admin screen)")
    } else {
        AppLogger.m(TAG, "No admin found, created \"$username\" with the ADMIN_PASSWORD password")
    }
}

private const val DEFAULT_ADMIN_USERNAME = "admin"
