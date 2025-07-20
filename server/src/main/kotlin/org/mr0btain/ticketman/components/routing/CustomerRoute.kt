package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.service.EncryptionService
import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.database.CustomerLocalDatabaseClientImpl
import org.mr0btain.ticketman.database.UserLocalDatabaseClientImpl
import org.mr0btain.ticketman.data.encryption.EncryptedMessage
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.SyncResponse

fun Route.customerRoute(
    database: CustomerLocalDatabaseClientImpl,
    userDatabase: UserLocalDatabaseClientImpl,
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
            val identifier = call.principal<JWTPrincipal>()?.payload?.getClaim("identifier")?.asString()
            val data = database.getAll() ?: emptyList()
            val encoded = Json.encodeToString(data)

            if (identifier != null) {
                userDatabase.getById(identifier)?.let { user ->
                    when (user.accessLevel) {
                        AccessLevel.ADMIN -> {
                            encryptionService.encryptMessage(
                                identifier = identifier,
                                message = encoded
                            )?.let { encryptedMessage ->
                                call.respond(HttpStatusCode.Accepted, encryptedMessage)
                            }
                        }

                        else -> {
                            encryptionService.encryptMessage(
                                identifier = identifier,
                                message = Json.encodeToString(
                                    database.getAll()?.map { item ->
                                        CustomerData(
                                            identifier = item.identifier,
                                            name = item.name,
                                            time = item.time,
                                            tickets = item.tickets,
                                            notes = item.notes
                                        )
                                    } ?: emptyList()
                                )
                            )?.let { encryptedMessage -> call.respond(HttpStatusCode.Accepted, encryptedMessage) }
                        }
                    }
                } ?: call.respond(HttpStatusCode.NotFound)
            } else call.respond(HttpStatusCode.BadRequest)
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
                    database.add(Json.decodeFromString<CustomerData>(message))

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
                    if (database.update(Json.decodeFromString<CustomerData>(message)))
                        call.respond(HttpStatusCode.Accepted) else call.respond(HttpStatusCode.BadRequest)
                }?: call.respond(HttpStatusCode.InternalServerError)
            }?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Bulk add mostly for syncing
    authenticate {
        post("/add/all") {
            call.handleDecryption<List<CustomerData>>(encryptionService)?.let { result ->
                if (database.addAll(result)) call.respond(HttpStatusCode.Accepted)
                else call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    // Remove customer
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