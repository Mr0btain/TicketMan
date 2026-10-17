package org.mr0btain.ticketman.components.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import org.mr0btain.ticketman.components.database.TokenDatabase
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.user.UserStatus
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import kotlin.uuid.Uuid

class JwtService(
    private val application: Application,
    private val userDatabase: UserDatabaseImpl,
    private val tokenDatabase: TokenDatabase
) {
    private val secret = getConfigProperty("jwt.secret")
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
     */

    @OptIn(ExperimentalTime::class)
    fun createJwtToken(identifier: String, expiresIn: Duration, sessionId: String): String {
        val now = Clock.System.now()

        return JWT
            .create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("identifier", identifier)
            .withClaim(SESSION_CLAIM, sessionId)
            .withIssuedAt(now.toJavaInstant())
            .withExpiresAt(now.plus(expiresIn).toJavaInstant())
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
        createJwtToken(identifier, 1.hours, sessionId)

    /**
     * Generates a refresh token
     */

    fun createRefreshToken(identifier: String, sessionId: String, lifetime: Duration = 365.days): String =
        createJwtToken(identifier, lifetime, sessionId)

    /**
     * Verify credentials along with access level to verify if its legit and allowed.
     * The token also has to belong to the user's current session, so revoking a session or logging in
     * again cuts off older access tokens right away
     */

    fun customValidator(credential: JWTCredential, minAccessLevel: AccessLevel): JWTPrincipal? {
        val id = extractId(credential)
        val foundUser = id?.let { userDatabase.getById(Uuid.parse(it)) }

        return foundUser?.let {
            if (
                audienceMatches(credential) &&
                foundUser.canAccess(minAccessLevel) &&
                isFromCurrentSession(id, credential)
            ) {
                JWTPrincipal(credential.payload)
            } else null
        }
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
                foundUser.status == UserStatus.ACTIVE &&
                isFromCurrentSession(id, credential)
            ) {
                JWTPrincipal(credential.payload)
            } else null
        }
    }

    // Checks the token's session is still stored and belongs to this user
    private fun isFromCurrentSession(id: String, credential: JWTCredential): Boolean {
        val sessionId = credential.payload.getClaim(SESSION_CLAIM).asString() ?: return false
        val session = tokenDatabase.findBySessionId(sessionId) ?: return false

        return session.userIdentifier == id
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
        private const val PURPOSE_CLAIM = "purpose"
        private const val SETUP_PURPOSE = "invite-setup"
        private val SETUP_TOKEN_LIFETIME = 30.minutes
    }
}
