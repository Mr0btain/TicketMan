@file:OptIn(ExperimentalUuidApi::class)

package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.service.EncryptionService
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.database.CustomerLocalDatabaseImpl
import org.mr0btain.ticketman.database.UserLocalDatabaseImpl
import org.mr0btain.ticketman.data.encryption.EncryptedData
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.response.SyncResponse
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Add more comments to help understand whats going on

fun Route.customerRoute(
    database: CustomerLocalDatabaseImpl,
    userDatabase: UserLocalDatabaseImpl,
    encryptionService: EncryptionService
) {
    // Sync between client and server
    authenticate {
        post("/sync") {
            call.getIdentifierFromToken()?.let { identifier ->
                val encryptedData = call.receive<EncryptedData>()

                encryptionService.decryptData(identifier, encryptedData)?.let {
                    Json.decodeFromString<SyncRequest>(it)
                }?.let { syncRequest ->
                    database.getAll()?.let { data ->
                        // Filter items by ones the client does not contain
                        val responseData = data.filter { !syncRequest.clientData.contains(it.identifier) }

                        // Map all the ids in the local database
                        val dataIdentifiers = data.map { it.identifier }

                        // Filter response data so we can send back a response to request those that are not in our database
                        val requested = syncRequest.clientData.filter { !dataIdentifiers.contains(it) }

                        encryptionService.encryptData(
                            identifier = identifier,
                            data = Json.encodeToString(SyncResponse(responseData, requested))
                        )?.let { response ->
                            call.respond(HttpStatusCode.OK, response)
                        } ?: call.respond(HttpStatusCode.InternalServerError)
                    } ?: call.respond(HttpStatusCode.NotFound)
                }?: call.respond(HttpStatusCode.InternalServerError)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Adding new
    authenticate {
        post("/add") {
            call.getIdentifierFromToken()?.let { identifier ->
                encryptionService.decryptData(
                    identifier = identifier,
                    data = call.receive<EncryptedData>()
                )?.let { message ->
                    database.add(Json.decodeFromString<Customer>(message))

                    call.respond(HttpStatusCode.Accepted)
                }?: call.respond(HttpStatusCode.InternalServerError)
            }?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Update
    authenticate {
        post("/update") {
            call.getIdentifierFromToken()?.let { identifier ->
                encryptionService.decryptData(
                    identifier = identifier,
                    data = call.receive<EncryptedData>()
                )?.let { message ->
                    if (database.update(Json.decodeFromString<Customer>(message)))
                        call.respond(HttpStatusCode.Accepted) else call.respond(HttpStatusCode.BadRequest)
                }?: call.respond(HttpStatusCode.InternalServerError)
            }?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // Bulk add mostly for syncing
    authenticate {
        post("/add/all") {
            call.handleDecryption<List<Customer>>(encryptionService)?.let { result ->
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

            if (database.remove(Uuid.parse(identifier))) call.respond(HttpStatusCode.Accepted)
            else call.respond(HttpStatusCode.BadRequest)
        }
    }
}