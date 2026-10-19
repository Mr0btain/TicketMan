package org.mr0btain.ticketman.components.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import org.mr0btain.ticketman.components.database.TokenDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.UserStatus
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid

/**
 * Makes and checks the server's JWTs: access tokens (an hour), refresh tokens (a year, or 12 hours for a PIN
 * session) and invite setup tokens (30 minutes). Every login token carries the user's identifier, its session
 * id (`sid`) and its kind (`typ`), and is only good while that session is stored in TokenTable.
 * [checkAccess] and [setupValidator] are what the auth providers in Security.kt call
 */
class JwtService(
    private val application: Application,
    private val userDatabase: UserDatabase,
    private val tokenDatabase: TokenDatabase,
    /** From [ServerSecret.resolve] */
    private val secret: String
) {
    private val issuer = getConfigProperty("jwt.issuer")
    private val audience = getConfigProperty("jwt.audience")

    val realm = getConfigProperty("jwt.realm")

    /**
     * Verifier for tokens
     */
    val jwtVerifier: JWTVerifier =
        JWT
            .require(Algorithm.HMAC256(secret))
            .withAudience(audience)
            .withIssuer(issuer)
            .build()

    /**
     * Generates a token given a UUID and a duration
     * @param sessionId Shared by a login's refresh token and every access token made from it
     * @param type [ACCESS_TYPE] or [REFRESH_TYPE], so one can't be used as the other
     */

    @OptIn(ExperimentalTime::class)
    private fun createJwtToken(identifier: String, expiresIn: Duration, sessionId: String, type: String): String =
        createJwtToken(identifier, Clock.System.now().plus(expiresIn), sessionId, type)

    @OptIn(ExperimentalTime::class)
    private fun createJwtToken(identifier: String, expiresAt: Instant, sessionId: String, type: String): String {
        val now = Clock.System.now()

        return JWT
            .create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("identifier", identifier)
            .withClaim(SESSION_CLAIM, sessionId)
            .withClaim(TYPE_CLAIM, type)
            // Times only go to the second, so without this two tokens made in the same second would match, and
            // a refresh token swapped for itself would keep working
            .withJWTId(Uuid.random().toString())
            .withIssuedAt(now.toJavaInstant())
            .withExpiresAt(expiresAt.toJavaInstant())
            .sign(Algorithm.HMAC256(secret))
    }

    /**
     * Short lived token handed out when an invite code is used, it can only be spent on finishing the invite
     */
    @OptIn(ExperimentalTime::class)
    fun createSetupToken(identifier: String): String =
        JWT
            .create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("identifier", identifier)
            .withClaim(PURPOSE_CLAIM, SETUP_PURPOSE)
            .withExpiresAt(Clock.System.now().plus(SETUP_TOKEN_LIFETIME).toJavaInstant())
            .sign(Algorithm.HMAC256(secret))

    /** User the setup token was made for, null if it's not a valid, unexpired setup token */
    fun verifySetupToken(token: String): Uuid? = try {
        val decoded = jwtVerifier.verify(token)

        if (decoded.getClaim(PURPOSE_CLAIM).asString() == SETUP_PURPOSE) {
            decoded.getClaim("identifier").asString()?.let { Uuid.parseOrNull(it) }
        } else null
    } catch (ex: Exception) {
        null
    }

    /**
     * Generates an access token
     */

    fun createAccessToken(identifier: String, sessionId: String): String =
        createJwtToken(identifier, 1.hours, sessionId, ACCESS_TYPE)

    /**
     * Generates a refresh token
     */

    fun createRefreshToken(identifier: String, sessionId: String, lifetime: Duration = 365.days): String =
        createJwtToken(identifier, lifetime, sessionId, REFRESH_TYPE)

    /**
     * A refresh token replacing an older one in the same session. It runs out when the old one would have, so
     * refreshing doesn't stretch a session
     */
    fun createRefreshToken(identifier: String, sessionId: String, expiresAt: Instant): String =
        createJwtToken(identifier, expiresAt, sessionId, REFRESH_TYPE)

    /**
     * Checks a token for a route needing [minAccessLevel]. Only access tokens get in, a refresh token can only be
     * spent on /api/auth/refresh. The token also has to belong to the user's current session, so revoking a
     * session or logging in again cuts off older access tokens right away.
     * A good token whose user can't use the route is [AccessCheck.Denied] (a 403, logging in again won't help),
     * anything else turned away is [AccessCheck.Invalid] (a 401)
     */
    fun checkAccess(credential: JWTCredential, minAccessLevel: AccessLevel): AccessCheck {
        val id = extractId(credential) ?: return AccessCheck.Invalid
        val foundUser = userDatabase.getById(Uuid.parse(id)) ?: return AccessCheck.Invalid

        if (!audienceMatches(credential) || !isAccessToken(credential) || !isFromCurrentSession(id, credential)) {
            return AccessCheck.Invalid
        }

        return when {
            foundUser.canAccess(minAccessLevel) -> AccessCheck.Allowed(JWTPrincipal(credential.payload))
            // Deactivated users' sessions are ended, so one still here is a 401 like any dead session
            foundUser.status != UserStatus.ACTIVE -> AccessCheck.Invalid
            else -> AccessCheck.Denied
        }
    }

    /** What [checkAccess] made of a token */
    sealed interface AccessCheck {
        data class Allowed(val principal: JWTPrincipal) : AccessCheck
        data object Denied : AccessCheck
        data object Invalid : AccessCheck
    }

    /**
     * Lets in any active user, including ones that haven't finished first login setup. Only for the setup route
     * and the few routes they need around it
     */
    fun setupValidator(credential: JWTCredential): JWTPrincipal? {
        val id = extractId(credential)
        val foundUser = id?.let { userDatabase.getById(Uuid.parse(it)) }

        return foundUser?.let {
            if (
                audienceMatches(credential) &&
                isAccessToken(credential) &&
                foundUser.status == UserStatus.ACTIVE &&
                isFromCurrentSession(id, credential)
            ) {
                JWTPrincipal(credential.payload)
            } else null
        }
    }

    // Tokens from before the type claim have none and are refused, the client refreshes and gets a typed one
    private fun isAccessToken(credential: JWTCredential): Boolean =
        credential.payload.getClaim(TYPE_CLAIM).asString() == ACCESS_TYPE

    // Checks the token's session is still stored, live and belongs to this user, and counts it as used
    @OptIn(ExperimentalTime::class)
    private fun isFromCurrentSession(id: String, credential: JWTCredential): Boolean {
        val sessionId = credential.payload.getClaim(SESSION_CLAIM).asString() ?: return false
        val session = tokenDatabase.findBySessionId(sessionId) ?: return false
        val now = Clock.System.now().toEpochMilliseconds()

        if (session.userIdentifier != id || !session.isLive(now)) return false

        tokenDatabase.touchIfStale(session, now)
        return true
    }

    /**
     * Checks if audience matches
     */
    fun audienceMatches(audience: String): Boolean =
        this.audience == audience

    // Extracts id
    private fun extractId(credential: JWTCredential): String? =
        credential.payload.getClaim("identifier").asString()

    // Gets a config property from environment variable
    private fun getConfigProperty(path: String): String =
        application.environment.config.property(path).getString()


    // Checks if audience matches
    private fun audienceMatches(credential: JWTCredential): Boolean =
        credential.payload.audience.contains(audience)

    companion object {
        const val SESSION_CLAIM = "sid"
        const val TYPE_CLAIM = "typ"
        const val ACCESS_TYPE = "access"
        const val REFRESH_TYPE = "refresh"
        private const val PURPOSE_CLAIM = "purpose"
        private const val SETUP_PURPOSE = "invite-setup"
        private val SETUP_TOKEN_LIFETIME = 30.minutes
    }
}
