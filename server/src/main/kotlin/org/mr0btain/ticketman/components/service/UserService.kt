package org.mr0btain.ticketman.components.service

import com.auth0.jwt.interfaces.DecodedJWT
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.InviteDatabase
import org.mr0btain.ticketman.components.database.PinCheck
import org.mr0btain.ticketman.components.database.StationDatabase
import org.mr0btain.ticketman.components.database.StoredSession
import org.mr0btain.ticketman.components.database.TokenDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PinLoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.LoginStatus
import org.mr0btain.ticketman.server.response.SessionKind
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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

/** What came of a PIN login */
sealed interface PinLoginResult {
    data class Success(val response: AuthResponse) : PinLoginResult

    /** Station unknown or its secret is wrong */
    data object BadStation : PinLoginResult
    data object StationNotApproved : PinLoginResult

    /** Wrong PIN, no PIN set, or not a user that can sign in */
    data class Wrong(val attemptsLeft: Int?) : PinLoginResult
    data object Locked : PinLoginResult
}

/**
 * Logging in and out: password and invite code logins, PIN logins at a station, starting a session per device,
 * and swapping a refresh token for a new access token. Account requests, invites and setup are in AccountService
 */
class UserService(
    private val database: UserDatabase,
    private val jwtService: JwtService,
    private val tokenDatabase: TokenDatabase,
    private val inviteDatabase: InviteDatabase,
    private val stationDatabase: StationDatabase,
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
        // Only the last person to PIN in is signed in at a station, and not for long
        if (kind == SessionKind.PIN) tokenDatabase.removePinSessionsForDevice(deviceId)
        val lifetime = if (kind == SessionKind.PIN) PIN_SESSION_LIFETIME else 365.days

        val sessionId = Uuid.random().toString()
        val refreshToken = jwtService.createRefreshToken(user.identifier.toString(), sessionId, lifetime)
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
     * Switches to a user at a station. Only works on approved stations, and wrong PINs count towards a lock
     */
    fun pinLogin(request: PinLoginRequest): PinLoginResult {
        val station = stationDatabase.verify(request.station) ?: return PinLoginResult.BadStation
        if (station.status != StationStatus.APPROVED) return PinLoginResult.StationNotApproved

        val user = database.getById(request.userIdentifier)?.takeIf { it.canAccess(AccessLevel.RESTRICTED) }

        if (user == null) {
            PasswordHasher.verifyDummy(request.pin)
            return PinLoginResult.Wrong(null)
        }

        when (database.checkPin(user.identifier, request.pin)) {
            PinCheck.OK -> Unit
            PinCheck.LOCKED -> {
                auditLog.log(null, AuditAction.LOGIN_FAILED, "user", user.identifier.toString(), "PIN locked at ${station.name}", actorName = user.username)
                return PinLoginResult.Locked
            }
            PinCheck.NO_PIN -> return PinLoginResult.Wrong(null)
            PinCheck.WRONG -> {
                auditLog.log(null, AuditAction.LOGIN_FAILED, "user", user.identifier.toString(), "Wrong PIN at ${station.name}", actorName = user.username)
                val tries = database.getPinFailedAttempts(user.identifier)
                return PinLoginResult.Wrong(PinRules.MAX_ATTEMPTS - tries)
            }
        }

        val response = startSession(user, station.deviceId, station.name, SessionKind.PIN)
            ?: return PinLoginResult.Wrong(null)

        auditLog.log(user.identifier, AuditAction.LOGIN, "user", user.identifier.toString(), "PIN at ${station.name}")

        return PinLoginResult.Success(response)
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
        // Refresh tokens from before the type claim have none, they still match their stored session below
        if (decoded.getClaim(JwtService.TYPE_CLAIM).asString() == JwtService.ACCESS_TYPE) return null
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

    companion object {
        /** PIN sessions are for a shift at the counter, not for keeping a device logged in */
        val PIN_SESSION_LIFETIME = 12.hours
    }
}
