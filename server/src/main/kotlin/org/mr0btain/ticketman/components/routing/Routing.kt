package org.mr0btain.ticketman.components.routing

import org.mr0btain.ticketman.server.API_VERSION
import org.mr0btain.ticketman.server.API_VERSION_HEADER
import org.mr0btain.ticketman.server.ApiRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
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
import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH

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
        // A body that isn't valid JSON, has a field this server doesn't know, or is missing one. The details stay
        // in the log, the caller gets a plain reason
        exception<BadRequestException> { call, cause ->
            AppLogger.i(BAD_REQUEST_TAG, "${call.request.httpMethod.value} ${call.request.path()}: ${cause.rootMessage()}")
            call.respondText(text = BAD_REQUEST_MESSAGE, status = HttpStatusCode.BadRequest)
        }

        // A body over one of Ktor's limits. Always with a reason, since clients take a 413 without one as their
        // reverse proxy's
        exception<PayloadTooLargeException> { call, cause ->
            AppLogger.i(BAD_REQUEST_TAG, "${call.request.httpMethod.value} ${call.request.path()}: ${cause.message}")
            call.respondText(text = TOO_LARGE_MESSAGE, status = HttpStatusCode.PayloadTooLarge)
        }

        exception<Throwable> { call, cause ->
            AppLogger.e(SERVER_ERROR_TAG, "${call.request.httpMethod.value} ${call.request.path()}", cause)
            call.respondText(text = SERVER_ERROR_MESSAGE, status = HttpStatusCode.InternalServerError)
        }
    }

    routing {
        // Tells clients the server is up, and that it's this app
        // The header says which API this server speaks, so a client can warn when it's from another version
        get(SERVER_PING_PATH) {
            call.response.header(API_VERSION_HEADER, API_VERSION.toString())
            call.respondText(SERVER_PING_BODY)
        }

        // The web client when it's built in. Otherwise "/" is the up check clients used before /api/ping
        if (webClientBundled()) {
            webRoute()
        } else {
            route("/") {
                get {
                    call.respond(HttpStatusCode.OK)
                    AppLogger.m("ROUTE.CHECK", "Client hit")
                }
            }
        }

        route(ApiRoutes.AUTH) {
            authRoute(userService, accountService, localDatabase.stationDatabase, localDatabase.userDatabase, localDatabase.auditLogDatabase)
        }

        route(ApiRoutes.USER) {
            userRoute(
                database = localDatabase.userDatabase,
                tokenDatabase = localDatabase.tokenDatabase,
                accountService = accountService,
                auditLog = localDatabase.auditLogDatabase
            )
        }

        route(ApiRoutes.ADMIN) {
            adminRoute(
                database = localDatabase,
                adminService = adminService,
                backupService = backupService,
                restoreService = restoreService
            )
        }

        route(ApiRoutes.COUNTER) {
            counterRoute(
                database = localDatabase.counterDatabase,
                shopDatabase = localDatabase.shopDatabase,
                invoiceService = invoiceService,
                checkInService = checkInService,
                userDatabase = localDatabase.userDatabase
            )
        }

        route(ApiRoutes.INVOICE) {
            invoiceRoute(
                database = localDatabase.invoiceDatabase,
                service = invoiceService,
                userDatabase = localDatabase.userDatabase
            )
        }

        route(ApiRoutes.STATION) {
            stationRoute(
                database = localDatabase.stationDatabase,
                userDatabase = localDatabase.userDatabase,
                auditLog = localDatabase.auditLogDatabase
            )
        }

        route(ApiRoutes.SHOP) {
            shopRoute(
                database = localDatabase.shopDatabase,
                auditLog = localDatabase.auditLogDatabase
            )
        }

        route(ApiRoutes.IMAGE) {
            imageRoute(
                service = imageService,
                auditLog = localDatabase.auditLogDatabase
            )
        }

        route(ApiRoutes.CUSTOMER) {
            customerRoute(
                database = localDatabase.customerDatabase,
                ticketDatabase = localDatabase.ticketDatabase,
                counterDatabase = localDatabase.counterDatabase,
                userDatabase = localDatabase.userDatabase,
                auditLog = localDatabase.auditLogDatabase
            )
        }

        route(ApiRoutes.TICKET) {
            ticketRoute(
                database = localDatabase.ticketDatabase,
                userDatabase = localDatabase.userDatabase,
                auditLog = localDatabase.auditLogDatabase
            )
        }

        route(ApiRoutes.INVENTORY) {
            inventoryRoute(
                database = localDatabase.inventoryDatabase,
                userDatabase = localDatabase.userDatabase,
                auditLog = localDatabase.auditLogDatabase
            )
        }
    }
}

private const val BAD_REQUEST_TAG = "ROUTE:BadRequest"
private const val SERVER_ERROR_TAG = "ROUTE:ServerError"

/** Sent for a body the route couldn't read, shown to whoever made the request */
const val BAD_REQUEST_MESSAGE = "The server couldn't read that request"

/** Sent when a route throws. What went wrong is in the server's log, not the response */
const val SERVER_ERROR_MESSAGE = "Something went wrong on the server"

/** Sent for a body over one of Ktor's limits */
const val TOO_LARGE_MESSAGE = "That's bigger than the server takes"

// Ktor wraps the serializer's message ("Field 'password' is required") a couple of levels down
private fun Throwable.rootMessage(): String =
    generateSequence(this) { it.cause }.last().message ?: toString()
