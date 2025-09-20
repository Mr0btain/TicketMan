package org.mr0btain.ticketman.components.service

import com.auth0.jwt.interfaces.DecodedJWT
import org.mr0btain.ticketman.components.database.RefreshToken
import org.mr0btain.ticketman.components.database.TokenDatabase
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.database.UserDatabaseImpl
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import java.util.UUID

class UserService(
    private val database: UserDatabaseImpl,
    private val jwtService: JwtService,
    private val tokenDatabase: TokenDatabase
) {
    fun authenticate(loginRequest: LoginRequest): AuthResponse? {
        val foundUser = database.all()?.firstOrNull { it.username == loginRequest.username }

        // Check if user was found and process request
        return if (foundUser != null && foundUser.password == loginRequest.password) {
            val refreshToken = jwtService.createRefreshToken(foundUser.identifier.toString())
            val accessToken = jwtService.createAccessToken(foundUser.identifier.toString())

            // Check if we should update or add new token
            if (tokenDatabase.findById(foundUser.identifier.toString()) != null) {
                if (!tokenDatabase.update(RefreshToken(foundUser.identifier.toString(), refreshToken))) {
                    return null
                }
            } else {
                if (!tokenDatabase.add(RefreshToken(foundUser.identifier.toString(), refreshToken))) return null
            }

            // Respond with tokens
            AuthResponse(
                id = foundUser.identifier,
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        } else null
    }

    fun authenticateUser(loginRequest: LoginRequest): User? {
        return database.all()?.firstOrNull { it.username == loginRequest.username }
    }

    fun refreshToken(token: String): String? {
        val decodedRefreshToken = verifyRefreshToken(token)
        val refreshToken = tokenDatabase.findByToken(token)

        return if (decodedRefreshToken != null && refreshToken != null) {
            val foundUser = database.getById(UUID.fromString(refreshToken.identifier))
            val idFromRefreshToken = decodedRefreshToken.getClaim("identifier").asString()

            if (foundUser != null && idFromRefreshToken == foundUser.identifier.toString())
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