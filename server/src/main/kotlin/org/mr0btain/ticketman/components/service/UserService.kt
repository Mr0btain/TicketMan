package org.mr0btain.ticketman.components.service

import com.auth0.jwt.interfaces.DecodedJWT
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

        return if (foundUser != null && foundUser.password == loginRequest.password) {
            val accessToken = jwtService.createAccessToken(foundUser.identifier)
            val refreshToken = jwtService.createRefreshToken(foundUser.identifier)

            tokenDatabase.add(RefreshToken(foundUser.identifier, refreshToken))

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

            if (foundUser != null && idFromRefreshToken == foundUser.identifier) {
                jwtService.createAccessToken(refreshToken.identifier)
            } else null
        } else null
    }

    private fun verifyRefreshToken(token: String): DecodedJWT? {
        val decodedJWT = decodeJWT(token)

        return decodedJWT?.let {
            val audienceMatches = jwtService.audienceMatches(it.audience.first())

            if (audienceMatches) {
                decodedJWT
            } else null
        }
    }

    private fun decodeJWT(token: String) = try {
        jwtService.jwtVerifier.verify(token)
    } catch (ex: Exception) {
        null
    }
}