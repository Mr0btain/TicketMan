 package org.mr0btain.ticketman

import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.database.configureDatabase
import org.mr0btain.ticketman.components.database.debug.seedTestData
import org.mr0btain.ticketman.components.plugins.configureContentNegotiation
import org.mr0btain.ticketman.components.plugins.configureSecurity
import org.mr0btain.ticketman.components.plugins.configureSerialization
import org.mr0btain.ticketman.components.plugins.configureTransfers
import org.mr0btain.ticketman.components.routing.configureRouting
import org.mr0btain.ticketman.components.service.AccountService
import org.mr0btain.ticketman.components.service.AdminService
import org.mr0btain.ticketman.components.service.BackupService
import org.mr0btain.ticketman.components.service.RestoreService
import org.mr0btain.ticketman.components.service.CheckInService
import org.mr0btain.ticketman.components.service.ImageService
import org.mr0btain.ticketman.components.service.InvoiceService
import org.mr0btain.ticketman.components.service.JwtService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.util.AppDir
import java.io.File
import java.security.SecureRandom
import java.util.Base64

// TODO: Should also setup some sort of timeout for session tokens. So after so much time we clear them?

/**
 * Main
 */

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val path = this.environment.config.property("env.dataPath").getString()
    val appDir = AppDir().path

    AppLogger.m("SERVER", "Path for Files: $appDir")

    // Check path dir
    if (path.isNotBlank() || appDir != null) {
        val dataPath = path.ifBlank { appDir.toString() }
        val database = configureDatabase(dataPath)
        val jwtService = JwtService(this, database.userDatabase, database.tokenDatabase)
        val userService = UserService(
            database = database.userDatabase,
            jwtService = jwtService,
            tokenDatabase = database.tokenDatabase,
            inviteDatabase = database.inviteDatabase,
            stationDatabase = database.stationDatabase,
            auditLog = database.auditLogDatabase
        )
        val accountService = AccountService(database.userDatabase, database.inviteDatabase, userService, jwtService, database.auditLogDatabase)
        val adminService = AdminService(
            database = database,
            serverName = environment.config.propertyOrNull("env.name")?.getString() ?: "Server",
            dataPath = dataPath,
            developmentMode = developmentMode
        )

        configureSerialization()
        configureContentNegotiation()
        configureSecurity(jwtService)
        configureTransfers()
        val invoiceService = InvoiceService(
            invoices = database.invoiceDatabase,
            tickets = database.ticketDatabase,
            inventory = database.inventoryDatabase,
            shop = database.shopDatabase,
            auditLog = database.auditLogDatabase
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
            backupsFolder = File(database.databaseDirectory.parentFile, "backups")
        )

        val restoreService = RestoreService(database.databaseDirectory.parentFile)

        configureRouting(
            userService, accountService, adminService, invoiceService, checkInService, imageService, backupService,
            restoreService, database
        )

        // Clears out photos deleted or never saved a day ago, and backups whose link has run out. Stops with
        // the application
        launch(Dispatchers.IO) {
            while (isActive) {
                imageService.sweep()
                backupService.sweep()
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
