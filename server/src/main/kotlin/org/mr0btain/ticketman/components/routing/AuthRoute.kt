package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.encryption.PeerKeyData
import org.mr0btain.ticketman.components.service.EncryptionService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.data.encryption.EncryptedMessage
import org.mr0btain.ticketman.server.request.KeyRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.response.RefreshTokenResponse
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Authorization routes
 */

// TODO: Setup Encryption. We should exchange keys somehow before we login so login info is encrypted

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
fun Route.authRoute(
    userService: UserService,
    encryptionService: EncryptionService
) {
    post {
        val encryptedData = call.receive<EncryptedMessage>()
        val identifier = encryptionService.findClientIdentifier(encryptedData)

        encryptionService.decryptMessage(identifier, encryptedData)?.let { decrypted ->
            userService.authenticate(
                Json.decodeFromString(decrypted)
            )?.let { authResponse ->
                encryptionService.updateKey(identifier, authResponse.id)

                encryptionService.encryptMessage(authResponse.id, Json.encodeToString(authResponse))
                    ?.let { encryptedResponse ->
                        call.respond(HttpStatusCode.Accepted, encryptedResponse)
                    } ?: call.respond(HttpStatusCode.InternalServerError)
            } ?: call.respond(HttpStatusCode.Unauthorized)
        } ?: call.respond(HttpStatusCode.InternalServerError)
    }

    post("/user") {
        val encryptedData = call.receive<EncryptedMessage>()
        val identifier = encryptionService.findClientIdentifier(encryptedData)

        encryptionService.decryptMessage(identifier, encryptedData)?.let { decrypted ->
            userService.authenticateUser(Json.decodeFromString(decrypted))?.let { user ->
                encryptionService.updateKey(identifier, user.identifier)
                call.respond(HttpStatusCode.Accepted)
            } ?: call.respond(HttpStatusCode.Unauthorized)
        } ?: call.respond(HttpStatusCode.InternalServerError)
    }

    // Get Tokens
    post("/refresh") {
        val encryptedData = call.receive<EncryptedMessage>()
        val identifier = encryptionService.findClientIdentifier(encryptedData)

        encryptionService.decryptMessage(identifier, encryptedData)?.let { decrypted ->
            val request = Json.decodeFromString<RefreshTokenRequest>(decrypted)
            userService.refreshToken(request.token)?.let { token ->
                encryptionService.encryptMessage(
                    identifier,
                    Json.encodeToString(RefreshTokenResponse(token))
                )?.let { encryptedData ->
                    call.respond(HttpStatusCode.Accepted, encryptedData)
                } ?: call.respond(HttpStatusCode.InternalServerError)
            } ?: call.respond(HttpStatusCode.Unauthorized)
        } ?: call.respond(HttpStatusCode.InternalServerError)
    }

    // Register a public key for encryption
    post("/key/request") {
        val keyRequest = call.receive<KeyRequest>()
        // Assign random uuid here to store the key for now till we register it to a user
        val id = Uuid.random().toString()

        encryptionService.addKey(
            identifier = id,
            peerKeyData = PeerKeyData(
                publicKey = keyRequest.publicKey,
                signingKey = keyRequest.publicSignature
            )
        )

        call.respond(HttpStatusCode.Accepted, encryptionService.getKeyResponse())
    }

    // Client can call this and check if keys are good
    post("/key/check") {
        try {
            val message = call.receive<EncryptedMessage>()

            encryptionService.findAndDecrypt(message)?.let { decrypted ->
                if (decrypted == "KeyCheck") {
                    call.respond(HttpStatusCode.Accepted)
                }
            } ?: call.respond(HttpStatusCode.NotFound)
        } catch (ex: Exception) {
            ex.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}