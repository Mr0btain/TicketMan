package org.mr0btain.ticketman.components.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.engine.applicationEnvironment
import org.mr0btain.ticketman.server.request.LoginRequest
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class JwtService(
    private val application: Application,
    private val userService: UserService
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
    fun createJwtToken(
        loginRequest: LoginRequest,
        expiresAt: Instant
    ): String? {
        val foundUser = userService.findByUsername(loginRequest.username)

        return if (foundUser != null && foundUser.password == loginRequest.password) {
            JWT
                .create()
                .withAudience(audience)
                .withIssuer(issuer)
                .withClaim("username", foundUser.username)
                .withExpiresAt(expiresAt.toJavaInstant())
                .sign(Algorithm.HMAC256(secret))
        } else null
    }

    fun customValidator(credential: JWTCredential): JWTPrincipal? {
        val username = extractUsername(credential)
        val foundUser = username?.let(userService::findByUsername)

        return foundUser?.let {
            if (audienceMatches(credential)) {
                JWTPrincipal(credential.payload)
            } else null
        }
    }

    private fun extractUsername(credential: JWTCredential): String? =
        credential.payload.getClaim("username").asString()

    private fun getConfigProperty(path: String): String =
        application.environment.config.property(path).getString()


    private fun audienceMatches(credential: JWTCredential): Boolean =
        credential.payload.audience.contains(audience)
}
