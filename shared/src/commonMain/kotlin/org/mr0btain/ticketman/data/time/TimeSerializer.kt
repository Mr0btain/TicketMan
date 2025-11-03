package org.mr0btain.ticketman.data.time

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

object TimeSerializer: KSerializer<Time> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Time", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Time) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Time {
        return Time.fromString(decoder.decodeString())
    }
}