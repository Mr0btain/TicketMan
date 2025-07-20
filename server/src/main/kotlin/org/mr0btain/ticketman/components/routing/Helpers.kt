package org.mr0btain.ticketman.components.routing

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.service.EncryptionService
import org.mr0btain.ticketman.data.encryption.EncryptedMessage

fun RoutingCall.getIdentifierFromToken(): String? {
    val principal = principal<JWTPrincipal>()
    return principal?.getClaim("identifier", String::class)
}

// TODO: Add some response here
suspend inline fun <reified T : Any> RoutingCall.handleDecryption(encryptionService: EncryptionService): T? {
    val encryptedMessage = receive<EncryptedMessage>()

    return getIdentifierFromToken()?.let { identifier ->
        encryptionService.decryptMessage(identifier, encryptedMessage)?.let { decryptedMessage ->
            Json.decodeFromString<T>(decryptedMessage)
        }
    }
}

// TODO: Add some response here
fun RoutingCall.handleEncryption(encryptionService: EncryptionService, data: String): EncryptedMessage? {
    return getIdentifierFromToken()?.let { identifier ->
        encryptionService.encryptMessage(
            identifier = identifier,
            message = data
        )
    }
}

suspend inline fun <reified T : Any> RoutingCall.findAndDecrypt(encryptionService: EncryptionService): T? {
    return receive<EncryptedMessage>().let { encryptedMessage ->
        encryptionService.findAndDecrypt(encryptedMessage)?. let { decrypted ->
            Json.decodeFromString<T>(decrypted)
        }
    }
}


