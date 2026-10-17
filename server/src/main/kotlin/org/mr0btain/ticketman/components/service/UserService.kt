package org.mr0btain.ticketman.components.service

import com.auth0.jwt.interfaces.DecodedJWT
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.InviteDatabase
import org.mr0btain.ticketman.components.database.StoredSession
import org.mr0btain.ticketman.components.database.TokenDatabase
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.LoginStatus
import org.mr0btain.ticketman.server.response.SessionKind
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** What came of a login attempt */
sealed interface LoginResult {
    data class Success(val response: AuthResponse) : LoginResult
    data object Failed : LoginResult

    /** Right password, but the account request hasn't been approved */
    data object PendingApproval : LoginResult

    /** Used an invite code, [response] carries the setup token */
    data class InviteSetup(val response: AuthResponse) : LoginResult
}

class UserService(
    private val database: UserDatabaseImpl,
    private val jwtService: JwtService,
    private val tokenDatabase: TokenDatabase,
    private val inviteDatabase: InviteDatabase,
    private val auditLog: AuditLogDatabase
) {
    /**
     * Logs in with a password, or with an invite's setup code in place of the password
     */
    fun authenticate(loginRequest: LoginRequest): LoginResult {
        val pending = database.getByUsername(loginRequest.username)?.takeIf { it.status == UserStatus.PENDING }

        if (pending != null) return pendingLogin(pending, loginRequest)

        val foundUser = verifyCredentials(loginRequest)

        if (foundUser == null) {
            auditLog.log(null, AuditAction.LOGIN_FAILED, "user", details = "Failed login", actorName = loginRequest.username)
            return LoginResult.Failed
        }

        val response = startSession(foundUser, loginRequest.deviceId, loginRequest.deviceName, SessionKind.PASSWORD)
            ?: return LoginResult.Failed

        auditLog.log(foundUser.identifier, AuditAction.LOGIN, "user", foundUser.identifier.toString(), loginRequest.deviceName)

        return LoginResult.Success(
            if (foundUser.needsSetup) response.copy(status = LoginStatus.NEEDS_SETUP) else response
        )
    }

    // Invited users log in with their code, requested accounts with the password they asked with
    private fun pendingLogin(user: User, loginRequest: LoginRequest): LoginResult {
        if (inviteDatabase.exists(user.identifier)) {
            if (!inviteDatabase.check(user.identifier, loginRequest.password)) {
                auditLog.log(null, AuditAction.LOGIN_FAILED, "user", details = "Bad or expired setup code", actorName = user.username)
                return LoginResult.Failed
            }

            return LoginResult.InviteSetup(
                AuthResponse(
                    id = user.identifier,
                    accessToken = null,
                    refreshToken = null,
                    status = LoginStatus.INVITE_SETUP,
                    setupToken = jwtService.createSetupToken(user.identifier.toString())
                )
            )
        }

        return if (PasswordHasher.verify(loginRequest.password, user.password)) LoginResult.PendingApproval
        else LoginResult.Failed
    }

    /**
     * Makes a new session for a user on a device and hands back its tokens. A device logging in again replaces
     * its old session, other devices are left alone
     */
    fun startSession(user: User, deviceId: String, deviceName: String, kind: SessionKind): AuthResponse? {
        val sessionId = Uuid.random().toString()
        val refreshToken = jwtService.createRefreshToken(user.identifier.toString(), sessionId)
        val accessToken = jwtService.createAccessToken(user.identifier.toString(), sessionId)
        val now = Clock.System.now().toEpochMilliseconds()

        val saved = tokenDatabase.add(
            StoredSession(
                sessionId = sessionId,
                userIdentifier = user.identifier.toString(),
                token = refreshToken,
                deviceId = deviceId.trim(),
                deviceName = deviceName.trim(),
                kind = kind,
                createdAt = now,
                lastUsedAt = now
            )
        )

        if (!saved) return null

        return AuthResponse(
            id = user.identifier,
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
     * Finds the user and checks their password. Only active users can log in.
     * Passwords stored before hashing was added get hashed on their first good login.
     */
    private fun verifyCredentials(loginRequest: LoginRequest): User? {
        val foundUser = database.getByUsername(loginRequest.username)

        if (foundUser == null) {
            PasswordHasher.verifyDummy(loginRequest.password)
            return null
        }

        if (!PasswordHasher.verify(loginRequest.password, foundUser.password)) return null
        if (foundUser.status != UserStatus.ACTIVE) return null

        if (!PasswordHasher.isHashed(foundUser.password)) {
            database.updatePassword(foundUser.identifier, loginRequest.password)
        }

        return foundUser
    }

    fun refreshToken(token: String): String? {
        val decoded = decodeJWT(token) ?: return null
        val session = tokenDatabase.findByToken(token) ?: return null

        if (!jwtService.audienceMatches(decoded.audience.first())) return null

        val foundUser = database.getById(Uuid.parse(session.userIdentifier)) ?: return null
        val idFromToken = decoded.getClaim("identifier").asString()

        if (foundUser.status != UserStatus.ACTIVE || idFromToken != session.userIdentifier) return null

        tokenDatabase.touch(session.sessionId)

        return jwtService.createAccessToken(session.userIdentifier, session.sessionId)
    }

    /** Ends a session, used when logging out */
    fun logout(sessionId: String): Boolean =
        tokenDatabase.remove(sessionId)

    private fun decodeJWT(token: String): DecodedJWT? = try {
        jwtService.jwtVerifier.verify(token)
    } catch (ex: Exception) {
        null
    }
}
