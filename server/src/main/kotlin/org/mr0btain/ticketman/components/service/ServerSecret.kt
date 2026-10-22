package org.mr0btain.ticketman.components.service

import org.mr0btain.ticketman.logging.AppLogger
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/**
 * The key tokens are signed with: `SECRET` when it's set, otherwise one the server makes once and keeps in
 * `<dataPath>/server/secret`. Kept because a new one would log every device out
 */
object ServerSecret {
    private const val TAG = "ServerSecret"
    private const val LOAD_TAG = "$TAG:Load"
    private const val FILE_NAME = "secret"
    private const val BYTES = 64

    fun file(dataPath: String) = File(dataPath, "server/$FILE_NAME")

    /**
     * @param configured `jwt.secret` from the config, blank when `SECRET` isn't set
     */
    fun resolve(configured: String, dataPath: String): String {
        if (configured.isNotBlank()) return configured

        val file = file(dataPath)
        if (file.isFile) {
            file.readText().trim().takeIf { it.isNotBlank() }?.let { return it }
        }

        val secret = ByteArray(BYTES).also { SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

        file.parentFile.mkdirs()
        file.writeText(secret)
        ownerOnly(file)

        AppLogger.m(LOAD_TAG, "SECRET isn't set, made one in ${file.absolutePath}")
        return secret
    }

    /** Only the account running the server can read or change [file] */
    private fun ownerOnly(file: File) {
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
    }
}
