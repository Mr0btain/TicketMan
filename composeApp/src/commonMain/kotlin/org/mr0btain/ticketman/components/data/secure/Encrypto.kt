package org.mr0btain.ticketman.components.data.secure

import kotlin.random.Random

/**
 * Stuff for encryption not sure hwo its gonna be yet
 */

class Encrypto {

    /**
     * Returns a random salt to be used to hash a password.
     *
     * @return a 16 bytes random salt
     */
    fun getNextSalt(): String {
        val salt = ByteArray(16)
        Random.nextBytes(salt)
        return salt.toString()
    }
}