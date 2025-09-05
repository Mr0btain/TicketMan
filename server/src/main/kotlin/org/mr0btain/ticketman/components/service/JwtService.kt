package org.mr0btain.ticketman.components.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.database.UserDatabaseImpl
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class JwtService(
    private val application: Application,
    private val userDatabase: UserDatabaseImpl
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
     */

    @OptIn(ExperimentalTime::class)
    fun createJwtToken(identifier: String, expiresIn: Duration): String =
        JWT
            .create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("identifier", identifier)
            .withExpiresAt(Clock.System.now().plus(expiresIn).toJavaInstant())
            .sign(Algorithm.HMAC256(secret))

    /**
     * Generates an access token
     */

    fun createAccessToken(identifier: String): String =
        createJwtToken(identifier, 1.hours)

    /**
     * Generates a refresh token
     */

    fun createRefreshToken(identifier: String): String =
        createJwtToken(identifier,  365.days)

    /**
     * Verify credentials along with access level to verify if its legit and allowed
     */

    @OptIn(ExperimentalUuidApi::class)
    fun customValidator(credential: JWTCredential, minAccessLevel: AccessLevel): JWTPrincipal? {
        val id = extractId(credential)
        val foundUser = id?.let { userDatabase.getById(Uuid.parse(it)) }

        return foundUser?.let {
            if (audienceMatches(credential) && foundUser.accessLevel.confirmAccessLevel(minAccessLevel)) {
                JWTPrincipal(credential.payload)
            } else null
        }
    }

    // Checks if access level is high enough
    private fun AccessLevel.confirmAccessLevel(minAccessLevel: AccessLevel): Boolean =
        this.level >= minAccessLevel.level

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
}
