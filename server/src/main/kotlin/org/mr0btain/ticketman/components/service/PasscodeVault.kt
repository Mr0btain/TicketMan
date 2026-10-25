package org.mr0btain.ticketman.components.service

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seals device passcodes with AES-GCM under a key made from the server's secret, so the database (and its backups)
 * never hold one readable. A server with a different secret can't open them
 * @param secret From [ServerSecret.resolve]
 */
class PasscodeVault(secret: String) {
    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest("ticketman-passcodes:$secret".toByteArray()), "AES")
    private val random = SecureRandom()

    /** The passcode sealed, as `iv:ciphertext` in base64 */
    fun seal(passcode: String): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        val sealed = cipher.doFinal(passcode.toByteArray())

        val encoder = Base64.getEncoder()
        return "${encoder.encodeToString(iv)}:${encoder.encodeToString(sealed)}"
    }

    /** The passcode back, or null when it was sealed under another secret or isn't one of ours */
    fun open(sealed: String): String? =
        try {
            val (iv, body) = sealed.split(':', limit = 2).map { Base64.getDecoder().decode(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
            String(cipher.doFinal(body))
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
