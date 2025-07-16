package org.mr0btain.ticketman.components.service

import io.ktor.server.application.Application
import org.mr0btain.ticketman.encryption.EncryptedMessage
import org.mr0btain.ticketman.encryption.EncryptionHandler
import org.mr0btain.ticketman.encryption.PeerKeyData
import org.mr0btain.ticketman.server.response.KeyResponse

class EncryptionService(
    application: Application,
) {
    private val name = application.environment.config.property("env.name").getString()
    private val handler = EncryptionHandler(name)
    private val keyStorage = mutableMapOf<String, PeerKeyData>()

    /** Add or update a key to the key storage */
    fun addKey(identifier: String, peerKeyData: PeerKeyData) {
        if (keyStorage.containsKey(identifier)) {
            keyStorage.replace(identifier, peerKeyData)
        } else {
            keyStorage.put(identifier, peerKeyData)
        }
    }

    fun encryptMessage(identifier: String, message: String): EncryptedMessage? {
        return keyStorage[identifier]?.let { peerKey ->
            handler.encryptAndSignMessage(message, peerKey.publicKey)
        }
    }

    fun decryptMessage(identifier: String, message: EncryptedMessage): String? {
        return keyStorage[identifier]?.let { peerKey ->
            handler.verifyAndDecryptMessage(message, peerKey.publicKey, peerKey.signingKey)
        }
    }

    fun getKeyResponse(): KeyResponse {
        return KeyResponse(
            publicKey = handler.xPublicKey,
            publicSignature = handler.getSigningPublicKey()
        )
    }
}