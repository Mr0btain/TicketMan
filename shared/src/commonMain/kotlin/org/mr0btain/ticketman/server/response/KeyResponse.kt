package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.util.PublicKeySerializer
import org.mr0btain.ticketman.util.XECSerializer
import java.security.PublicKey
import java.security.interfaces.XECPublicKey

@Serializable
data class KeyResponse(
    @Serializable(XECSerializer::class)
    val publicKey: XECPublicKey,
    @Serializable(PublicKeySerializer::class)
    val publicSignature: PublicKey
)
