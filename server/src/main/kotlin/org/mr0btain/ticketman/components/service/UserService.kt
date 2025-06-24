package org.mr0btain.ticketman.components.service

import com.auth0.jwt.interfaces.DecodedJWT
import org.mr0btain.ticketman.components.repository.RefreshTokenRepository
import org.mr0btain.ticketman.components.repository.UserRepository
import org.mr0btain.ticketman.server.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import java.util.UUID

class UserService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val refreshTokenRepository: RefreshTokenRepository
) {
    fun findAll(): List<User> =
        userRepository.findAll()

    fun findById(id: String): User? =
        userRepository.findById(UUID.fromString(id))

    fun save(user: User): User? {
        val foundUser = userRepository.findById(user.identifier)

        return if (foundUser == null) {
            userRepository.save(user)
            user
        } else null
    }
    fun authenticate(loginRequest: LoginRequest): AuthResponse? {
        val id = loginRequest.identifier
        val foundUser = userRepository.findById(id)

        return if (foundUser != null && foundUser.password == loginRequest.password) {
            val accessToken = jwtService.createAccessToken(id)
            val refreshToken = jwtService.createRefreshToken(id)

            refreshTokenRepository.save(refreshToken, id)

            AuthResponse(
                id = foundUser.identifier,
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        } else null
    }

    fun refreshToken(token: String): String? {
        val decodedRefreshToken = verifyRefreshToken(token)
        val persistedId = refreshTokenRepository.findIdByToken(token)

        return if (decodedRefreshToken != null && persistedId != null) {
            val foundUser = userRepository.findById(persistedId)
            val idFromRefreshToken = UUID.fromString(decodedRefreshToken.getClaim("identifier").asString())

            if (foundUser != null && idFromRefreshToken == foundUser.identifier) {
                jwtService.createAccessToken(persistedId)
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