package org.mr0btain.ticketman.encryption

import kotlinx.serialization.Serializable

@Serializable
data class EncryptedMessage(
    //val senderIdentifier: String,
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val signature: ByteArray
)
