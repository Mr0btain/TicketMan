package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.LocalDatabase
import org.mr0btain.ticketman.components.service.EncryptionService
import org.mr0btain.ticketman.components.service.UserService

fun Application.configureRouting(
    userService: UserService,
    localDatabase: LocalDatabase,
    encryptionService: EncryptionService
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
            get { call.respond(HttpStatusCode.OK) }
        }

        route("/api/auth") {
            authRoute(userService, encryptionService)
        }

        route("/api/user") {
            userRoute(
                database = localDatabase.userDatabase,
                encryptionService = encryptionService
            )
        }

        route("/api/data/customer") {
            customerRoute(
                database = localDatabase.customerDatabase,
                userDatabase = localDatabase.userDatabase,
                encryptionService = encryptionService
                )
        }

        route("/api/data/ticket") {
            ticketRoute(
                database = localDatabase.ticketDatabase,
                encryptionService = encryptionService
            )
        }

        route("/api/data/inventory") {
            inventoryRoute(
                database = localDatabase.inventoryDatabase,
                encryptionService = encryptionService
            )
        }
    }
}
