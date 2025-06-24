package org.mr0btain.ticketman.components.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import org.mr0btain.ticketman.components.repository.UserRepository
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

class JwtService(
    private val application: Application,
    private val userRepository: UserRepository
) {
    private val secret = getConfigProperty("jwt.secret")
    private val issuer = getConfigProperty("jwt.issuer")
    private val audience = getConfigProperty("jwt.audience")

    val realm = getConfigProperty("jwt.realm")

    val jwtVerifier: JWTVerifier =
        JWT
            .require(Algorithm.HMAC256(secret))
            .withAudience(audience)
            .withIssuer(issuer)
            .build()

    @OptIn(ExperimentalTime::class)
    fun createJwtToken(id: UUID, expiresIn: Duration): String =
        JWT
            .create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("identifier", id.toString())
            .withExpiresAt(Clock.System.now().plus(expiresIn).toJavaInstant())
            .sign(Algorithm.HMAC256(secret))

    fun createAccessToken(id: UUID): String =
        createJwtToken(id, 1.hours)

    fun createRefreshToken(id: UUID): String =
        createJwtToken(id, 365.days)


    fun customValidator(credential: JWTCredential): JWTPrincipal? {
        val id = UUID.fromString(extractId(credential))
        val foundUser = id?.let(userRepository::findById)

        return foundUser?.let {
            if (audienceMatches(credential)) {
                JWTPrincipal(credential.payload)
            } else null
        }
    }

    fun audienceMatches(audience: String): Boolean =
        this.audience == audience

    private fun extractId(credential: JWTCredential): String? =
        credential.payload.getClaim("identifier").asString()

    private fun getConfigProperty(path: String): String =
        application.environment.config.property(path).getString()


    private fun audienceMatches(credential: JWTCredential): Boolean =
        credential.payload.audience.contains(audience)
}
