package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import java.security.PublicKey
import java.security.interfaces.XECPublicKey

@Serializable
data class KeyResponse(
    val publicKey: XECPublicKey,
    val publicSignature: PublicKey
)
