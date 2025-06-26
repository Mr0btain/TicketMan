package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.ServerDatabase
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.database.LocalDatabase

fun Application.configureRouting(
    userService: UserService,
    serverDatabase: ServerDatabase
) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }

    routing {
        // Static plugin. Try to access `/static/index.html`
        staticResources("/static", "static")

        route("/api/auth") {
            authRoute(userService)
        }

        route("/api/user") {
            // TODO: Setup new database flag?
            userRoute(serverDatabase.userDatabase)
        }

        route("/api/data/customer") {
            customerRoute(serverDatabase.customerDatabase)
        }

        route("/api/data/ticket") {
            ticketRoute(serverDatabase.ticketDatabase)
        }

        route("/api/data/inventory") {
            inventoryRoute(serverDatabase.inventoryDatabase)
        }
    }
}
