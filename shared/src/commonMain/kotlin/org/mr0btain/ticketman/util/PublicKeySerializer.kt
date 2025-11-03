package org.mr0btain.ticketman.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object PublicKeySerializer: KSerializer<PublicKey> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("PublicKey", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PublicKey) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value.encoded))
    }

    override fun deserialize(decoder: Decoder): PublicKey {
        val keyBytes = Base64.getDecoder().decode(decoder.decodeString())
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("Ed25519") // e.g., "XEC", "RSA", "EC", "DSA"
        return keyFactory.generatePublic(keySpec)
    }
}


