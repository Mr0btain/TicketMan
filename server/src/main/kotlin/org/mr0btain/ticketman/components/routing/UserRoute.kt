@file:OptIn(ExperimentalUuidApi::class)

package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.service.EncryptionService
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.data.encryption.EncryptedData
import org.mr0btain.ticketman.data.hashPassword
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.database.UserDatabaseImpl
import org.mr0btain.ticketman.server.request.SyncRequest
import org.mr0btain.ticketman.server.request.UserRequest
import org.mr0btain.ticketman.server.response.BooleanResponse
import org.mr0btain.ticketman.server.response.SyncResponse
import org.mr0btain.ticketman.server.response.UserResponse
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * User routes
 */

// TODO: Not sure if we should let this handle filtering restricted data, or the database itself.
// TODO: Handle encryption in the rest of these routes
fun Route.userRoute(
    database: UserDatabaseImpl,
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
                    database.all()?.let { data ->
                        val user = data.firstOrNull { it.identifier == identifier }

                        if (user != null) {
                            // Filter items by ones the client does not contain
                            val responseData = data.filter { !syncRequest.clientData.contains(it.identifier) }.map { it.copy(password = "") }

                            // Map all the ids in the local database
                            val dataIdentifiers = data.map { it.identifier }

                            // Filter response data so we can send back a response to request those that are not in our database
                            val requested = syncRequest.clientData.filter { !dataIdentifiers.contains(it) }

                            when (user.accessLevel) {
                                AccessLevel.ADMIN -> {
                                    encryptionService.encryptData(
                                        identifier = identifier,
                                        data = Json.encodeToString(SyncResponse(responseData, requested))
                                    )?.let { response ->
                                        call.respond(HttpStatusCode.OK, response)
                                    } ?: call.respond(HttpStatusCode.InternalServerError)
                                }

                                else -> {
                                    val restrictedData = responseData.map { user ->
                                        User(
                                            identifier = user.identifier,
                                            name = user.name
                                        )
                                    }

                                    encryptionService.encryptData(
                                        identifier = identifier,
                                        data = Json.encodeToString(SyncResponse(restrictedData, requested))
                                    )?.let { response ->
                                        call.respond(HttpStatusCode.OK, response)
                                    } ?: call.respond(HttpStatusCode.InternalServerError)
                                }
                            }
                        } else call.respond(HttpStatusCode.NotFound)
                    } ?: call.respond(HttpStatusCode.NotFound)
                }?: call.respond(HttpStatusCode.InternalServerError)
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }

    // TODO: Not sure if this is the right way to go about this
    get("/isnewdatabase") {
        if (database.all()?.isEmpty() ?: true) call.respond(BooleanResponse(true))
        else call.respond(BooleanResponse(false))
    }

    post("/register/first") {
        val identifier = Uuid.random()

        if (database.all()?.isEmpty() ?: true) {
            val userRequest = call.receive<UserRequest>()

            // TODO: Figure this out
            // First user is always admin?
            val user = userRequest.toModel(identifier).copy(accessLevel = AccessLevel.ADMIN)

            if (database.add(user)) call.respond(HttpStatusCode.Created, user.toResponse())
            else call.respond(HttpStatusCode.BadRequest)
        } else call.respond(HttpStatusCode.Unauthorized)
    }

    authenticate("admin") {
        post("/register") {
            val userRequest = call.receive<UserRequest>()
            val identifier = Uuid.random()


            val user = userRequest.toModel(identifier)
            val userExists = database.all()?.map { it.username }?.contains(user.username)

            if (userExists != null && !userExists) {
                if (database.add(user)) call.respond(HttpStatusCode.Created, user.toResponse())
                else call.respond(HttpStatusCode.BadRequest)
            } else call.respond(HttpStatusCode.Conflict)
        }
    }

    // Update
    authenticate("admin") {
        post("/update") {
            try {
                val user = call.receive<User>()

                if (database.update(user)) call.respond(HttpStatusCode.Accepted)
                else call.respond(HttpStatusCode.BadRequest)
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    // Bulk add mostly for syncing
    authenticate("admin") {
        post("/add/all") {
            try {
                val customers = call.receive<List<User>>()

                if (database.addAll(customers)) call.respond(HttpStatusCode.Accepted)
                else call.respond(HttpStatusCode.BadRequest)
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }

    // Delete
    authenticate("admin") {
        delete("/delete/{userIdentifier}") {
            try {
                val identifier = call.parameters["userIdentifier"]

                if (identifier == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@delete
                }

                if (database.remove(Uuid.parse(identifier))) call.respond(HttpStatusCode.Accepted)
                else call.respond(HttpStatusCode.BadRequest)
            } catch (ex: Exception) {
                ex.printStackTrace()
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}

private fun UserRequest.toModel(id: Uuid): User =
    User(
        identifier = id,
        username = username,
        password = hashPassword(password),
        name = name,
        contactInfo = contactInfo,
        accessLevel = accessLevel
    )

private fun User.toResponse(): UserResponse =
    UserResponse(
        identifier = identifier,
        accessLevel = accessLevel
    )