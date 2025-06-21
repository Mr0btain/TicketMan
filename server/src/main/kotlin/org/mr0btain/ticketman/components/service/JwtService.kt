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

    fun createJwtToken(loginRequest: LoginRequest): String? {
        val foundUser = userService.findByUsername(loginRequest.username)

        return if (foundUser != null && foundUser.password == loginRequest.password) {
            JWT
                .create()
                .withAudience(audience)
                .withIssuer(issuer)
                .withClaim("username", foundUser.username)
                .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
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

    private fun getConfigProperty(path: String): String? {
        println("Path: $path")
        println("Prop: ${application.environment.config.propertyOrNull(path)?.getString() ?: "NOT_FOUND"}")
        println("Prop2: ${applicationEnvironment().config.propertyOrNull(path)?.getString() ?: "NOT_FOUND"}")
        return application.environment.config.propertyOrNull(path)?.getString()
    }


    private fun audienceMatches(credential: JWTCredential): Boolean =
        credential.payload.audience.contains(audience)
}
