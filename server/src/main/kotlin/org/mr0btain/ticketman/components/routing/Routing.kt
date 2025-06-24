package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.database.LocalAppDatabase

fun Application.configureRouting(
    userService: UserService,
    localAppDatabase: LocalAppDatabase
) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }

    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        // Static plugin. Try to access `/static/index.html`
        staticResources("/static", "static")

        route("/api/auth") {
            authRoute(userService)
        }

        route("/api/user") {
            userRoute(userService, localAppDatabase)
        }

        route("/api/data/customer") {
            authenticate {
                customerRoute(localAppDatabase)
            }
        }

        route("/api/data/ticket") {
            authenticate {
                ticketRoute(localAppDatabase)
            }
        }

        route("/api/data/inventory") {
            authenticate {
                inventoryRoute(localAppDatabase)
            }
        }
    }
}
