package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable

/**
 * The customer's signature accepting the repair terms at check in. Kept as pen strokes rather than an image so it
 * stays sharp at any size, on screen or on a printed slip
 * @param aspectRatio Width over height of the pad it was drawn on, points are scaled to 0..1 in both directions
 * @param terms The terms they agreed to, copied in so later changes to the shop's terms don't change what was signed
 * @param signedAt Epoch millis
 * @param takenBy Username of whoever checked the device in
 */
@Serializable
data class TicketSignature(
    val strokes: List<List<SignaturePoint>>,
    val aspectRatio: Float,
    val signedName: String,
    val terms: String,
    val signedAt: Long,
    val takenBy: String
)

/** A point on the pad, both 0..1 */
@Serializable
data class SignaturePoint(
    val x: Float,
    val y: Float
)

/** What the client sends, the server adds the terms, time and who took it */
@Serializable
data class SignatureInput(
    val strokes: List<List<SignaturePoint>>,
    val aspectRatio: Float,
    val signedName: String
) {
    /** Problem with the signature, or null if it's fine */
    fun problem(): String? = when {
        strokes.isEmpty() || strokes.all { it.size < 2 } -> "The signature is empty"
        strokes.sumOf { it.size } > MAX_POINTS -> "The signature is too detailed"
        strokes.any { stroke -> stroke.any { it.x !in 0f..1f || it.y !in 0f..1f } } -> "The signature has points off the pad"
        aspectRatio <= 0f -> "The signature pad size is wrong"
        signedName.isBlank() -> "Type the name of whoever signed"
        else -> null
    }

    companion object {
        const val MAX_POINTS = 5_000
    }
}
