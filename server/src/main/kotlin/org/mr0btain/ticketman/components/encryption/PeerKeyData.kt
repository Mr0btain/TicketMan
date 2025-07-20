package org.mr0btain.ticketman.components.encryption

import java.security.PublicKey
import java.security.interfaces.XECPublicKey

data class PeerKeyData(
    val publicKey: XECPublicKey,
    val signingKey: PublicKey
)
