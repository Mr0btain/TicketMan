package org.mr0btain.ticketman.server.tls

/**
 * How a certificate's SHA-256 fingerprint is written everywhere it's shown: the server log, the admin screen and
 * the client asking whether to trust a server. Uppercase hex pairs split by colons, like `AB:CD:01:...`
 */
fun formatFingerprint(sha256: ByteArray): String = sha256.joinToString(":") { byte ->
    (byte.toInt() and 0xff).toString(16).padStart(2, '0').uppercase()
}

/** Fingerprints compare the same however they were typed or spaced */
fun sameFingerprint(a: String, b: String): Boolean = a.normalizedFingerprint() == b.normalizedFingerprint()

private fun String.normalizedFingerprint(): String = filter { it.isLetterOrDigit() }.uppercase()
