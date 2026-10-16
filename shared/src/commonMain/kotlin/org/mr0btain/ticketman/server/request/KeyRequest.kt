package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
//import org.mr0btain.ticketman.util.PublicKeySerializer
//import org.mr0btain.ticketman.util.XECSerializer

@Serializable
data class KeyRequest(
    //@Serializable(XECSerializer::class)
    val publicKey: String,
    //@Serializable(PublicKeySerializer::class)
    val publicSignature: String
)