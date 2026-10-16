package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.CustomerDatabaseImpl
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.Uuid

// TODO: Add more comments to help understand whats going on

fun Route.customerRoute(
    database: CustomerDatabaseImpl
) {
    val TAG = "CustomerRoute"
    val GET_TAG = "$TAG:Get"
    val FETCH_TAG = "$TAG:Fetch"
    val DELETE_TAG = "$TAG:Delete"
    val UPDATE_TAG = "$TAG:Update"
    val SEARCH_TAG = "$TAG:Search"

    // Adding new
    authenticate {
        post("/add") {
            val received = call.receive<Customer>()

            database.add(received)

            call.respond(HttpStatusCode.Accepted)
        }
    }

    authenticate {
        post("/add/images/{identifier}") {
            // TODO()
        }
    }

    authenticate {
        post("/add/note/{identifier}") {
            // TODO()
        }
    }

    authenticate {
        post("/fetch") {
            val request = call.receive<PagedRequest>()

            val result = database.getPagedItems(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(FETCH_TAG, "$request not found")
            }
        }
    }

    authenticate {
        post("/get") {
            val request = call.receive<Uuid>()

            val result = database.getById(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(GET_TAG, "$request not found")
            }
        }
    }

    authenticate {
        post("/get/notes/{identifier}") {
            // TODO()
        }
    }

    // Remove customer
    authenticate {
        delete("/remove/{identifier}") {
            val identifier = call.parameters["identifier"]

            if (identifier == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                database.remove(Uuid.parse(identifier))
                call.respond(HttpStatusCode.Accepted)
            }
        }
    }

    authenticate {
        delete("/remove/note/{identifier}") {
            // TODO()
        }
    }

    authenticate {
        delete("/remove/image/{identifier}") {
            // TODO()
        }
    }

    authenticate {
        post("/search") {
            val request = call.receive<String>()

            val result = database.search(request)

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(SEARCH_TAG, "$request not found")
            }
        }
    }

    // Update
    authenticate {
        post("/update") {
            val received = call.receive<Customer>()
            database.update(received)
            call.respond(HttpStatusCode.Accepted)
        }
    }
}