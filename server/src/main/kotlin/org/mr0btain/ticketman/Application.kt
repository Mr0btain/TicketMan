 package org.mr0btain.ticketman

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
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
import org.mr0btain.ticketman.components.plugins.configureHttps
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
import org.mr0btain.ticketman.components.tls.HttpMode
import org.mr0btain.ticketman.components.tls.ServerTls
import org.mr0btain.ticketman.components.tls.TlsSettings
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.response.ServerCertificateInfo
import org.mr0btain.ticketman.util.AppDir
import java.io.File
import java.security.SecureRandom
import java.util.Base64

// TODO: Should also setup some sort of timeout for session tokens. So after so much time we clear them?

/**
 * Main
 */

fun main(args: Array<String>) {
    createServer(args).start(wait = true)
}

/**
 * Netty's `EngineMain` set up, plus an HTTPS connector on `tls.port` when it's on. Done here rather than in
 * application.yaml's `ktor.security` because the certificate may have to be made first
 */
fun createServer(args: Array<String>): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
    val commandLine = CommandLineConfig(args)
    val config = commandLine.environment.config
    val settings = TlsSettings.from(config)
    val dataPath = resolveDataPath(config)

    val certificate = when {
        !settings.enabled -> null
        // Nowhere to keep a made certificate. module() says why there's no data folder
        settings.keyStorePath.isBlank() && dataPath.isBlank() -> null
        else -> ServerTls.load(settings, dataPath)
    }

    if (certificate != null) {
        AppLogger.m("SERVER:Https", "HTTPS on port ${settings.httpsPort}, plain HTTP is ${settings.httpMode.name.lowercase()}")
        AppLogger.m("SERVER:Https", "Certificate fingerprint (devices are asked to check this): ${certificate.fingerprint}")
    }

    return EmbeddedServer(commandLine.rootConfig, Netty) {
        takeFrom(commandLine.engineConfig)

        if (certificate != null) {
            val host = connectors.firstOrNull()?.host ?: "0.0.0.0"
            if (settings.httpMode == HttpMode.OFF) connectors.clear()

            sslConnector(
                keyStore = certificate.keyStore,
                keyAlias = certificate.alias,
                keyStorePassword = { certificate.keyStorePassword.toCharArray() },
                privateKeyPassword = { certificate.privateKeyPassword.toCharArray() }
            ) {
                this.host = host
                port = settings.httpsPort
            }
        }
    }
}

/** `DATA_PATH`, falling back to the platform folder only when it isn't set, so that isn't made for nothing */
fun resolveDataPath(config: ApplicationConfig): String {
    val path = config.propertyOrNull("env.dataPath")?.getString().orEmpty()
    return path.ifBlank { AppDir().path?.toString().orEmpty() }
}

fun Application.module() {
    val dataPath = resolveDataPath(environment.config)

    if (dataPath.isBlank()) {
        AppLogger.e("SERVER", "No DATA_PATH set and no app data folder found, so there's nowhere to keep data")
    } else {
        AppLogger.m("SERVER", "Path for Files: $dataPath")
        if (System.getenv("SECRET").isNullOrBlank()) {
            AppLogger.e("SERVER", "SECRET isn't set, so tokens are signed with the public default in application.yaml. Set it on a real server")
        }
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
        val tls = TlsSettings.from(environment.config)
        val adminService = AdminService(
            database = database,
            serverName = environment.config.propertyOrNull("env.name")?.getString() ?: "Server",
            dataPath = dataPath,
            developmentMode = developmentMode,
            certificate = certificateInfo(tls, dataPath)
        )

        configureSerialization()
        configureContentNegotiation()
        configureSecurity(jwtService)
        configureTransfers()
        configureHttps(tls)
        configureCors(environment.config.propertyOrNull("cors.hosts")?.getString().orEmpty())
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

/** The certificate createServer() started HTTPS with, for the admin screen. Read again since it's already made */
private fun certificateInfo(settings: TlsSettings, dataPath: String): ServerCertificateInfo? {
    if (!settings.enabled) return null

    return try {
        ServerTls.load(settings, dataPath).info(settings)
    } catch (ex: Exception) {
        AppLogger.e("SERVER:Https", "Couldn't read the HTTPS certificate", ex)
        null
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
