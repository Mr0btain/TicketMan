package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.service.EncryptionService
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.database.TicketLocalDatabaseClientImpl
import org.mr0btain.ticketman.data.encryption.EncryptedMessage
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.SyncResponse

fun Route.ticketRoute(
    database: TicketLocalDatabaseClientImpl,
    encryptionService: EncryptionService
) {
    // Sync between client and server
    authenticate {
        post("/sync") {
            call.getIdentifierFromToken()?.let { identifier ->
                val encryptedMessage = call.receive<EncryptedMessage>()

                encryptionService.decryptMessage(identifier, encryptedMessage)?.let {
                    Json.decodeFromString<SyncRequest>(it)
                }?.let { syncRequest ->
                    database.getAll()?.let { data ->
                        // Filter items by ones the client does not contain
                        val responseData = data.filter { !syncRequest.clientData.contains(it.identifier) }

                        // Map all the ids in the local database
                        val dataIdentifiers = data.map { it.identifier }

                        // Filter response data so we can send back a response to request those that are not in our database
                        val requested = syncRequest.clientData.filter { !dataIdentifiers.contains(it) }

                        encryptionService.encryptMessage(
                            identifier = identifier,
                            message = Json.encodeToString(SyncResponse(responseData, requested))
                        )?.let { response ->
                            call.respond(HttpStatusCode.OK, response)
                        } ?: call.respond(HttpStatusCode.InternalServerError)
                    } ?: call.respond(HttpStatusCode.NotFound)
                }?: call.respond(HttpStatusCode.InternalServerError)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Get all
    authenticate("restricted") {
        get("/all") {
            database.getAll()?.let { data ->
                val string = Json.encodeToString(data)
                println(string)
                call.handleEncryption(encryptionService, string)?.let { message ->
                    call.respond(HttpStatusCode.Accepted, message)
                } ?: call.respond(HttpStatusCode.InternalServerError)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Adding new
    authenticate {
        post("/add") {
            call.getIdentifierFromToken()?.let { identifier ->
                encryptionService.decryptMessage(
                    identifier = identifier,
                    message = call.receive<EncryptedMessage>()
                )?.let { message ->
                    database.add(Json.decodeFromString<TicketInfo>(message))

                    call.respond(HttpStatusCode.Accepted)
                }?: call.respond(HttpStatusCode.InternalServerError)
            }?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Update
    authenticate {
        post("/update") {
            call.getIdentifierFromToken()?.let { identifier ->
                encryptionService.decryptMessage(
                    identifier = identifier,
                    message = call.receive<EncryptedMessage>()
                )?.let { message ->
                    if (database.update(Json.decodeFromString<TicketInfo>(message)))
                        call.respond(HttpStatusCode.Accepted) else call.respond(HttpStatusCode.BadRequest)
                }?: call.respond(HttpStatusCode.InternalServerError)
            }?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Bulk add mostly for syncing
    authenticate {
        post("/add/all") {
            call.handleDecryption<List<TicketInfo>>(encryptionService)?.let { result ->
                if (database.addAll(result)) call.respond(HttpStatusCode.Accepted)
                else call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    // Remove
    authenticate {
        delete("/remove/{identifier}") {
            val identifier = call.parameters["identifier"]

            if (identifier == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            if (database.remove(identifier)) call.respond(HttpStatusCode.Accepted)
            else call.respond(HttpStatusCode.BadRequest)
        }
    }
}