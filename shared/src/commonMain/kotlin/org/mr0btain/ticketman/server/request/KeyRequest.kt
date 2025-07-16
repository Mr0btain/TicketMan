package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import java.security.PublicKey
import java.security.interfaces.XECPublicKey

@Serializable
data class KeyRequest(
    val identifier: String,
    val publicKey: XECPublicKey,
    val publicSignature: PublicKey
)
