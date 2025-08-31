package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.components.encryption.PeerKeyData
import org.mr0btain.ticketman.components.encryption.ServerEncryptionHandler
import org.mr0btain.ticketman.data.encryption.EncryptedData
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.response.KeyResponse
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Service used to handle encryption and decryption of data sent to and from server
 * Anything decrypted will be returned as a string that can be serialized to its class
 */

@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class EncryptionService() {
    private val handler = ServerEncryptionHandler()
    private val keyStorage = mutableMapOf<Uuid, PeerKeyData>()

    private val TAG = "EncryptionService"
    private val REGISTER_KEYS_TAG = "$TAG:RegisterKey"
    private val UPDATE_KEY_IDENTIFIER = "$TAG:UpdateKeyIdentifier"

    /** Inserts a key into storage */
    fun registerKey(identifier: Uuid, peerKeyData: PeerKeyData) {
        if (keyStorage.containsKey(identifier)) {
            keyStorage.replace(identifier, peerKeyData)
            AppLogger.m(REGISTER_KEYS_TAG, "Key Found, Updating Identifier")
        } else {
            keyStorage[identifier] = peerKeyData
            AppLogger.m(REGISTER_KEYS_TAG, "Registering new key")
        }
    }

    /** Updates the id for a key in key storage */
    fun updateKeyIdentifier(oldId: Uuid, newId: Uuid) {
        keyStorage[oldId]?.let { peerKeyData ->
            keyStorage.remove(oldId)
            keyStorage[newId] = peerKeyData
            AppLogger.m(UPDATE_KEY_IDENTIFIER, SimpleError.SUCCESS)
        } ?: AppLogger.i(UPDATE_KEY_IDENTIFIER, "Identifier Not Found")
    }

    /** Encrypt data to a specific client */
    fun encryptData(identifier: Uuid, data: String): EncryptedData? {
        return keyStorage[identifier]?.let { peerKey ->
            handler.encryptAndSignData(data, peerKey.publicKey)
        }
    }

    /** Try to decrypt data with all keys in database */
    fun findAndDecrypt(data: EncryptedData): String? {
        return findClientIdentifier(data)?.let { identifier ->
            decryptData(identifier, data)
        }
    }

    /** Finds an identifier for the encrypted data if one exists */
    fun findClientIdentifier(data: EncryptedData): Uuid? {
        // Try and find a match and update identifier if we do find one
        var identifier: Uuid? = null

        keyStorage.forEach { key ->
            if (handler.verifySignature(data, key.value.signingKey)) {
                identifier = key.key
                return@forEach
            }
        }

        return identifier
    }

    /** Decrypt data from a client */
    fun decryptData(identifier: Uuid, data: EncryptedData): String? {
        val storedKeys = keyStorage[identifier]

        return if (storedKeys != null) {
            handler.verifyAndDecryptData(data, storedKeys.publicKey, storedKeys.signingKey)
        } else {
            // Try and find a match and update identifier if we do find one
            var oldIdentifier: Uuid? = null

            keyStorage.forEach { key ->
                if (handler.verifySignature(data, key.value.signingKey)) {
                    oldIdentifier = key.key
                    return@forEach
                }
            }

            // If we can pull it from storage replace it and move on
            keyStorage[oldIdentifier]?.let { keys ->
                keyStorage.remove(oldIdentifier)
                keyStorage[identifier] = keys

                handler.decryptData(data, keys.publicKey)
            }
        }
    }

    /** Get keys needed to send messages to server */
    fun getPublicKeyResponse(): KeyResponse {
        return KeyResponse(
            publicKey = handler.xPublicKey,
            publicSignature = handler.getSigningPublicKey()
        )
    }
}