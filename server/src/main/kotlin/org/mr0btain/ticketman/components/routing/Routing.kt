package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.service.AccountService
import org.mr0btain.ticketman.components.service.AdminService
import org.mr0btain.ticketman.components.service.BackupService
import org.mr0btain.ticketman.components.service.RestoreService
import org.mr0btain.ticketman.components.service.CheckInService
import org.mr0btain.ticketman.components.service.ImageService
import org.mr0btain.ticketman.components.service.InvoiceService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.logging.AppLogger

fun Application.configureRouting(
    userService: UserService,
    accountService: AccountService,
    adminService: AdminService,
    invoiceService: InvoiceService,
    checkInService: CheckInService,
    imageService: ImageService,
    backupService: BackupService,
    restoreService: RestoreService,
    localDatabase: LocalDatabase
) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }

    routing {
        // Static plugin. Try to access `/static/index.html`
        staticResources("/static", "static")

        // Just a callback to tell client the server is up
        route("/") {
            get {
                call.respond(HttpStatusCode.OK)
                AppLogger.m("ROUTE.CHECK", "Client hit")
            }
        }

        route("/api/auth") {
            authRoute(userService, accountService, localDatabase.stationDatabase, localDatabase.userDatabase)
        }

        route("/api/user") {
            userRoute(
                database = localDatabase.userDatabase,
                tokenDatabase = localDatabase.tokenDatabase,
                accountService = accountService,
                auditLog = localDatabase.auditLogDatabase
            )
        }

        route("/api/admin") {
            adminRoute(
                database = localDatabase,
                adminService = adminService,
                backupService = backupService,
                restoreService = restoreService
            )
        }

        route("/api/counter") {
            counterRoute(
                database = localDatabase.counterDatabase,
                shopDatabase = localDatabase.shopDatabase,
                invoiceService = invoiceService,
                checkInService = checkInService,
                userDatabase = localDatabase.userDatabase
            )
        }

        route("/api/invoice") {
            invoiceRoute(
                database = localDatabase.invoiceDatabase,
                service = invoiceService,
                userDatabase = localDatabase.userDatabase
            )
        }

        route("/api/station") {
            stationRoute(
                database = localDatabase.stationDatabase,
                userDatabase = localDatabase.userDatabase,
                auditLog = localDatabase.auditLogDatabase
            )
        }

        route("/api/shop") {
            shopRoute(
                database = localDatabase.shopDatabase,
                auditLog = localDatabase.auditLogDatabase
            )
        }

        route("/api/image") {
            imageRoute(
                service = imageService,
                auditLog = localDatabase.auditLogDatabase
            )
        }

        route("/api/data/customer") {
            customerRoute(
                database = localDatabase.customerDatabase,
                ticketDatabase = localDatabase.ticketDatabase,
                counterDatabase = localDatabase.counterDatabase,
                userDatabase = localDatabase.userDatabase,
                auditLog = localDatabase.auditLogDatabase
            )
        }

        route("/api/data/ticket") {
            ticketRoute(
                database = localDatabase.ticketDatabase,
                userDatabase = localDatabase.userDatabase,
                auditLog = localDatabase.auditLogDatabase
            )
        }

        route("/api/data/inventory") {
            inventoryRoute(
                database = localDatabase.inventoryDatabase,
                userDatabase = localDatabase.userDatabase,
                auditLog = localDatabase.auditLogDatabase
            )
        }
    }
}
