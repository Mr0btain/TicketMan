package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.encryption.PeerKeyData
import org.mr0btain.ticketman.components.encryption.ServerEncryptionHandler
import org.mr0btain.ticketman.data.encryption.EncryptedMessage
import org.mr0btain.ticketman.server.response.KeyResponse

class EncryptionService() {
    private val handler = ServerEncryptionHandler()
    private val keyStorage = mutableMapOf<String, PeerKeyData>()

    /** Add a key to the key storage */
    fun addKey(identifier: String, peerKeyData: PeerKeyData) {
        if (keyStorage.containsKey(identifier)) {
            keyStorage.replace(identifier, peerKeyData)
        } else {
            keyStorage.put(identifier, peerKeyData)
        }
    }

    /** Updates the id for a key in key storage */
    fun updateKey(oldId: String, newId: String) {
        keyStorage[oldId]?.let { peerKeyData ->
            keyStorage.remove(oldId)
            keyStorage.put(newId, peerKeyData)
        }
    }

    /** Encrypt a message to a specific client */
    fun encryptMessage(identifier: String, message: String): EncryptedMessage? {
        return keyStorage[identifier]?.let { peerKey ->
            handler.encryptAndSignMessage(message, peerKey.publicKey)
        }
    }

    fun findAndDecrypt(message: EncryptedMessage): String? {
        val identifier = findClientIdentifier(message)
        return decryptMessage(identifier, message)
    }

    fun findClientIdentifier(message: EncryptedMessage): String {
        // Try and find a match and update identifier if we do find one
        var identifier = ""

        keyStorage.forEach { key ->
            if (handler.verifySignature(message, key.value.signingKey)) {
                identifier = key.key
                return@forEach
            }
        }

        return identifier
    }

    /** Decrypt a message from a client */
    fun decryptMessage(identifier: String, message: EncryptedMessage): String? {
        val storedKeys = keyStorage[identifier]

        return if (storedKeys != null) {
            handler.verifyAndDecryptMessage(message, storedKeys.publicKey, storedKeys.signingKey)
        } else {
            // Try and find a match and update identifier if we do find one
            var oldIdentifier = ""

            keyStorage.forEach { key ->
                if (handler.verifySignature(message, key.value.signingKey)) {
                    oldIdentifier = key.key
                    return@forEach
                }
            }

            // If we can pull it from storage replace it and move on
            keyStorage[oldIdentifier]?.let { keys ->
                keyStorage.remove(oldIdentifier)
                keyStorage.put(identifier, keys)

                handler.decryptMessage(message, keys.publicKey)
            }
        }
    }

    /** Get keys needed to send messages to server */
    fun getKeyResponse(): KeyResponse {
        return KeyResponse(
            publicKey = handler.xPublicKey,
            publicSignature = handler.getSigningPublicKey()
        )
    }
}