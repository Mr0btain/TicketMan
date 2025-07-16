package org.mr0btain.ticketman.components.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.mr0btain.ticketman.components.service.EncryptionService
import org.mr0btain.ticketman.components.service.UserService
import org.mr0btain.ticketman.encryption.PeerKeyData
import org.mr0btain.ticketman.server.request.KeyRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.RefreshTokenRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.RefreshTokenResponse
import kotlin.time.ExperimentalTime

/**
 * Authorization routes
 */

@OptIn(ExperimentalTime::class)
fun Route.authRoute(
    userService: UserService,
    encryptionService: EncryptionService
) {
    post {
        val loginRequest = call.receive<LoginRequest>()
        val authResponse: AuthResponse? = userService.authenticate(loginRequest)

        authResponse?.let {
            call.respond(HttpStatusCode.Accepted, authResponse)
        } ?: call.respond(HttpStatusCode.Unauthorized)
    }

    post("/user") {
        val request = call.receive<LoginRequest>()
        val authenticated = userService.authenticateUser(request)

        if (authenticated) call.respond(HttpStatusCode.Accepted) else call.respond(HttpStatusCode.Unauthorized)
    }

    // Refresh
    post("/refresh") {
        val request = call.receive<RefreshTokenRequest>()

        val newAccessToken: String? = userService.refreshToken(request.token)

        newAccessToken?.let {
            call.respond(HttpStatusCode.Created, RefreshTokenResponse(newAccessToken))
        } ?: call.respond(HttpStatusCode.Unauthorized)
    }

    // Register a public key for encryption
    post("/key/request") {
        val keyRequest = call.receive<KeyRequest>()

        encryptionService.addKey(
            identifier = keyRequest.identifier,
            peerKeyData = PeerKeyData(
                publicKey = keyRequest.publicKey,
                signingKey = keyRequest.publicSignature
            )
        )

        call.respond(HttpStatusCode.Accepted, encryptionService.getKeyResponse())
    }
}