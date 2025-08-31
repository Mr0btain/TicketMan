package org.mr0btain.ticketman.components.routing

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.service.EncryptionService
import org.mr0btain.ticketman.data.encryption.EncryptedData
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun RoutingCall.getIdentifierFromToken(): Uuid? {
    val principal = principal<JWTPrincipal>()
    return principal?.getClaim("identifier", String::class)
        ?.let { Uuid.parse(it) }
}

// TODO: Add some response here
@OptIn(ExperimentalUuidApi::class)
suspend inline fun <reified T : Any> RoutingCall.handleDecryption(encryptionService: EncryptionService): T? {
    val encryptedData = receive<EncryptedData>()

    return getIdentifierFromToken()?.let { identifier ->
        encryptionService.decryptData(identifier, encryptedData)?.let { decryptedMessage ->
            Json.decodeFromString<T>(decryptedMessage)
        }
    }
}

// TODO: Add some response here
@OptIn(ExperimentalUuidApi::class)
fun RoutingCall.handleEncryption(encryptionService: EncryptionService, data: String): EncryptedData? {
    return getIdentifierFromToken()?.let { identifier ->
        encryptionService.encryptData(
            identifier = identifier,
            data = data
        )
    }
}

suspend inline fun <reified T : Any> RoutingCall.findAndDecrypt(encryptionService: EncryptionService): T? {
    return receive<EncryptedData>().let { encryptedMessage ->
        encryptionService.findAndDecrypt(encryptedMessage)?. let { decrypted ->
            Json.decodeFromString<T>(decrypted)
        }
    }
}


