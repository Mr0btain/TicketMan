package org.mr0btain.ticketman.components.service

import com.auth0.jwt.interfaces.DecodedJWT
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.RefreshToken
import org.mr0btain.ticketman.components.database.TokenDatabase
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.uuid.Uuid

class UserService(
    private val database: UserDatabaseImpl,
    private val jwtService: JwtService,
    private val tokenDatabase: TokenDatabase,
    private val auditLog: AuditLogDatabase
) {
    fun authenticate(loginRequest: LoginRequest): AuthResponse? {
        val foundUser = verifyCredentials(loginRequest)

        if (foundUser == null) {
            auditLog.log(null, AuditAction.LOGIN_FAILED, "user", details = "Failed login", actorName = loginRequest.username)
            return null
        }

        // New session on every login, so tokens from an older login stop working
        val sessionId = Uuid.random().toString()
        val refreshToken = jwtService.createRefreshToken(foundUser.identifier.toString(), sessionId)
        val accessToken = jwtService.createAccessToken(foundUser.identifier.toString(), sessionId)

        // Check if we should update or add new token
        if (tokenDatabase.findById(foundUser.identifier.toString()) != null) {
            if (!tokenDatabase.update(RefreshToken(foundUser.identifier.toString(), refreshToken))) {
                return null
            }
        } else {
            if (!tokenDatabase.add(RefreshToken(foundUser.identifier.toString(), refreshToken))) return null
        }

        auditLog.log(foundUser.identifier, AuditAction.LOGIN, "user", foundUser.identifier.toString())

        // Respond with tokens
        return AuthResponse(
            id = foundUser.identifier,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    /**
     * Checks credentials without starting a session
     */
    fun authenticateUser(loginRequest: LoginRequest): User? =
        verifyCredentials(loginRequest)

    /**
     * Finds the user and checks their password. Deactivated users (NONE access) can't log in.
     * Passwords stored before hashing was added get hashed on their first good login.
     */
    private fun verifyCredentials(loginRequest: LoginRequest): User? {
        val foundUser = database.getByUsername(loginRequest.username)

        if (foundUser == null) {
            PasswordHasher.verifyDummy(loginRequest.password)
            return null
        }

        if (!PasswordHasher.verify(loginRequest.password, foundUser.password)) return null
        if (foundUser.accessLevel == AccessLevel.NONE) return null

        if (!PasswordHasher.isHashed(foundUser.password)) {
            database.updatePassword(foundUser.identifier, loginRequest.password)
        }

        return foundUser
    }

    fun refreshToken(token: String): String? {
        val decodedRefreshToken = verifyRefreshToken(token)
        val refreshToken = tokenDatabase.findByToken(token)

        return if (decodedRefreshToken != null && refreshToken != null) {
            val foundUser = database.getById(Uuid.parse(refreshToken.identifier))
            val idFromRefreshToken = decodedRefreshToken.getClaim("identifier").asString()

            if (
                foundUser != null &&
                foundUser.accessLevel != AccessLevel.NONE &&
                idFromRefreshToken == foundUser.identifier.toString()
            )
                jwtService.createAccessToken(
                    refreshToken.identifier,
                    decodedRefreshToken.getClaim(JwtService.SESSION_CLAIM).asString() ?: Uuid.random().toString()
                )
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
