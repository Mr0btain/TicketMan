package org.mr0btain.ticketman.encryption

import java.security.PublicKey
import java.security.interfaces.XECPublicKey

data class PeerKeyData(
    val publicKey: XECPublicKey,
    val signingKey: PublicKey
)
