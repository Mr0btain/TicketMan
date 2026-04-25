package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.encryption.PeerKeyData
import org.mr0btain.ticketman.components.service.EncryptionService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.data.encryption.EncryptedData
import org.mr0btain.ticketman.data.uuid.UUIDv7
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

// TODO: These let make it a bit hard to follow. Maybe expand them to if statements to make it easier to understand
// TODO: Add logging

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
fun Route.authRoute(
    userService: UserService,
    encryptionService: EncryptionService
) {
    post {
        // Receive encrypted data from the request body and parse it into an EncryptedMessage object
        val encryptedData = call.receive<EncryptedData>()
        // Get the identifier for the user
        encryptionService.findClientIdentifier(encryptedData)?.let { identifier ->
            // Attempt to decrypt the message using the client's identifier
            encryptionService.decryptData(identifier, encryptedData)?.let { decrypted ->

                // Try to parse the decrypted message into a LoginRequest and authenticate the user
                userService.authenticate(Json.decodeFromString<LoginRequest>(decrypted))?.let { authResponse ->
                    if (authResponse.id != null) {
                        // If authentication is successful, update the encryption key for this client
                        encryptionService.updateKeyIdentifier(identifier, authResponse.id!!)

                        // Encrypt the authentication response using the updated key
                        encryptionService.encryptData(authResponse.id!!, Json.encodeToString(authResponse))
                            ?.let { encryptedResponse ->
                                // Respond to the client with the encrypted authentication response
                                call.respond(HttpStatusCode.Accepted, encryptedResponse)
                            } ?: call.respond(HttpStatusCode.InternalServerError) // Handle encryption failure
                    }
                } ?: call.respond(HttpStatusCode.Unauthorized) // Handle failed authentication
            } ?: call.respond(HttpStatusCode.InternalServerError) // Handle decryption failure
        } ?: call.respond(HttpStatusCode.NotFound) // Handle user not doing
    }


    post("/user") {
        // Receive encrypted data from the request body and parse it into an EncryptedMessage object
        val encryptedData = call.receive<EncryptedData>()
        // Get the identifier for the user
        encryptionService.findClientIdentifier(encryptedData)?.let { identifier ->
            // Attempt to decrypt the message using the client's credentials
            encryptionService.decryptData(identifier, encryptedData)?.let { decrypted ->

                // Try to parse the decrypted message into a user object and authenticate
                userService.authenticateUser(Json.decodeFromString(decrypted))?.let { user ->

                    // If authentication is successful, update the encryption identifier with the user's identifier
                    encryptionService.updateKeyIdentifier(identifier, user.identifier)

                    // Respond with HTTP 202 Accepted if authentication and key update are successful
                    call.respond(HttpStatusCode.Accepted)

                } ?: call.respond(HttpStatusCode.Unauthorized) // Respond with 401 if authentication fails
            } ?: call.respond(HttpStatusCode.InternalServerError) // Respond with 500 if decryption fails
        } ?: call.respond(HttpStatusCode.NotFound) // Handle user not found
    }


    // Get Tokens
    post("/refresh") {
        // Receive encrypted data from the request body and parse it into an EncryptedMessage object
        val encryptedData = call.receive<EncryptedData>()
        // Get the identifier for the user
        encryptionService.findClientIdentifier(encryptedData)?.let { identifier ->
            // Attempt to decrypt the message using the client's identifier
            encryptionService.decryptData(identifier, encryptedData)?.let { decrypted ->
                // Deserialize the decrypted payload into a RefreshTokenRequest object
                val request = Json.decodeFromString<RefreshTokenRequest>(decrypted)

                // Try to refresh the token using the provided refresh token
                userService.refreshToken(request.token)?.let { token ->

                    // If the token was refreshed successfully, encrypt the new token into a response
                    encryptionService.encryptData(identifier, Json.encodeToString(RefreshTokenResponse(token)))
                        ?.let { encryptedData ->
                            // Respond with the encrypted refreshed token
                            call.respond(HttpStatusCode.Accepted, encryptedData)

                        } ?: call.respond(HttpStatusCode.InternalServerError) // Handle encryption failure
                } ?: call.respond(HttpStatusCode.Unauthorized) // Handle token refresh failure (e.g., invalid token)
            } ?: call.respond(HttpStatusCode.InternalServerError) // Handle decryption failure
        } ?: call.respond(HttpStatusCode.NotFound) // Handle client not found
    }


    // Register a public key for encryption
    post("/key/request") {
        // Receive key request
        val keyRequest = call.receive<KeyRequest>()
        // Assign random uuid here to store the key for now till we register it to a user on login
        val id = UUIDv7.randomUUID()

        // Add key to key storage
        encryptionService.registerKey(
            identifier = id,
            peerKeyData = PeerKeyData(
                publicKey = keyRequest.publicKey,
                signingKey = keyRequest.publicSignature
            )
        )

        call.respond(HttpStatusCode.Accepted, encryptionService.getPublicKeyResponse())
    }

    // Client can call this and check if keys are good
    post("/key/check") {
        try {
            // Receive encrypted data from the request body and parse it into an EncryptedMessage object
            val message = call.receive<EncryptedData>()

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