package org.mr0btain.ticketman.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/*
object XECSerializer: KSerializer<XECPublicKey> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("XECPublicKey", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: XECPublicKey) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value.encoded))
    }

    override fun deserialize(decoder: Decoder): XECPublicKey {
        val keyBytes = Base64.getDecoder().decode(decoder.decodeString())
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("X25519")
        return keyFactory.generatePublic(keySpec) as XECPublicKey
    }
}
 */