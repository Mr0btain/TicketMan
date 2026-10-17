package org.mr0btain.ticketman.components.service

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashes and verifies user passwords with PBKDF2. Stored hashes look like `pbkdf2$<iterations>$<salt>$<hash>`
 * so the iteration count can be raised later without breaking old hashes.
 */
object PasswordHasher {
    private const val PREFIX = "pbkdf2"
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 310_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    // Checked against when a user isn't found so a missing username takes as long as a wrong password
    private val dummyHash by lazy { hash("dummy-password") }

    fun hash(password: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { random.nextBytes(it) }
        val hash = derive(password, salt, ITERATIONS)

        return "$PREFIX$$ITERATIONS$${encoder.encodeToString(salt)}$${encoder.encodeToString(hash)}"
    }

    /** Hashes the password unless it's already a hash, so it's safe to call on data that may have come from the database */
    fun hashIfNeeded(password: String): String =
        if (isHashed(password)) password else hash(password)

    fun isHashed(stored: String): Boolean =
        stored.startsWith("$PREFIX$") && stored.split("$").size == 4

    /**
     * Checks a password against what's stored. Passwords saved before hashing was added are still plain text,
     * those are compared directly so the caller can re-hash them on a successful login.
     */
    fun verify(password: String, stored: String): Boolean {
        if (!isHashed(stored)) {
            return MessageDigest.isEqual(password.toByteArray(), stored.toByteArray())
        }

        val (_, iterations, salt, hash) = stored.split("$")
        val expected = decoder.decode(hash)
        val actual = derive(password, decoder.decode(salt), iterations.toInt())

        return MessageDigest.isEqual(expected, actual)
    }

    /** Burns the same time as a real check, used when there's no user to check against */
    fun verifyDummy(password: String) {
        verify(password, dummyHash)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)

        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
