package org.mr0btain.ticketman.components.service

import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.util.collections.getValue
import org.mr0btain.ticketman.components.database.RefreshToken
import org.mr0btain.ticketman.components.database.TokenDatabase
import org.mr0btain.ticketman.database.UserDatabase
import org.mr0btain.ticketman.server.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import java.util.UUID

class UserService(
    private val database: UserDatabase,
    private val jwtService: JwtService,
    private val tokenDatabase: TokenDatabase
) {
    fun authenticate(loginRequest: LoginRequest): AuthResponse? {
        val foundUser = database.getByUsername(loginRequest.username)

        // Check if user was found and process request
        return if (foundUser != null && foundUser.password == loginRequest.password) {
            val refreshToken = jwtService.createRefreshToken(foundUser.identifier)
            val accessToken = jwtService.createAccessToken(foundUser.identifier)

            // Check if we should update or add new token
            if (tokenDatabase.findById(foundUser.identifier) != null) {
                if (!tokenDatabase.update(RefreshToken(foundUser.identifier, refreshToken))) {
                    return null
                }
            } else {
                if (!tokenDatabase.add(RefreshToken(foundUser.identifier, refreshToken))) return null
            }

            // Respond with tokens
            AuthResponse(
                id = foundUser.identifier,
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        } else null
    }

    fun refreshToken(token: String): String? {
        val decodedRefreshToken = verifyRefreshToken(token)
        val refreshToken = tokenDatabase.findByToken(token)

        return if (decodedRefreshToken != null && refreshToken != null) {
            val foundUser = database.getById(refreshToken.identifier)
            val idFromRefreshToken = UUID.fromString(decodedRefreshToken.getClaim("identifier").asString())

            if (foundUser != null && idFromRefreshToken == foundUser.identifier)
                jwtService.createAccessToken(refreshToken.identifier)
            else null
        } else null
    }

    private fun verifyRefreshToken(token: String): DecodedJWT? {
        val decodedJWT = decodeJWT(token)
        val foundToken = tokenDatabase.findByToken(token)

        // Check if token exists in database
        return if (foundToken != null) {
            // Not sure if we even need to verify audience since we've already confirmed it exists in database
            decodedJWT?.let {
                val audienceMatches = jwtService.audienceMatches(it.audience.first())

                if (audienceMatches) decodedJWT else null
            }
        } else null
    }

    private fun decodeJWT(token: String) = try {
        jwtService.jwtVerifier.verify(token)
    } catch (ex: Exception) {
        null
    }
}