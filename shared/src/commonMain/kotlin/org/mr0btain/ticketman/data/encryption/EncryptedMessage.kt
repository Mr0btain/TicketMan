package org.mr0btain.ticketman.data.encryption

import kotlinx.serialization.Serializable

@Serializable
data class EncryptedMessage(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val signature: ByteArray
)