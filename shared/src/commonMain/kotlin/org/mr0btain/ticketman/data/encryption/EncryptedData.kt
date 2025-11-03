package org.mr0btain.ticketman.data.encryption

import kotlinx.serialization.Serializable

/**
 * Encrypted data
 * @param cipherText The encrypted data
 * @param nonce (short for "number used once") random value ensures encrypting the same plaintext doesn't produce the same ciphertext
 * @param signature The signature specific to the user
 */
@Serializable
data class EncryptedData(
    val cipherText: ByteArray,
    val nonce: ByteArray,
    val signature: ByteArray
)