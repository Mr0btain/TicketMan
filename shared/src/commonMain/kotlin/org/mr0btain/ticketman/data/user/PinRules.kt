package org.mr0btain.ticketman.data.user

/**
 * What a PIN can be, checked by both the client and the server
 */
object PinRules {
    const val LENGTH = 6

    /** Wrong tries before the PIN is locked until an admin unlocks it */
    const val MAX_ATTEMPTS = 5

    /** Problem with the PIN, or null if it's fine */
    fun problem(pin: String): String? = when {
        pin.length != LENGTH || !pin.all { it.isDigit() } -> "PIN has to be $LENGTH digits"
        pin.toSet().size == 1 -> "PIN can't be the same digit over and over"
        isRun(pin) -> "PIN can't be a straight run like 123456"
        else -> null
    }

    // 123456 or 654321 and the like
    private fun isRun(pin: String): Boolean {
        val steps = pin.zipWithNext { a, b -> b - a }.toSet()
        return steps == setOf(1) || steps == setOf(-1)
    }
}
