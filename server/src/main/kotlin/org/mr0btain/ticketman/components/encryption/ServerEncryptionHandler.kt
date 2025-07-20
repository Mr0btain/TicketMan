package org.mr0btain.ticketman.components.encryption

import org.mr0btain.ticketman.data.encryption.EncryptedData
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import java.security.*
import java.security.interfaces.XECPrivateKey
import java.security.interfaces.XECPublicKey
import java.security.spec.NamedParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// TODO: Setup way to store these keys when we get them and look them up based on user id
// TODO: Setup server response error with one that can more accurately describe error. EX: This one would need badKey error
// TODO: Setup logging

@Suppress("PrivatePropertyName")
open class ServerEncryptionHandler() {
    // X25519 keys for key exchange
    private val xPrivateKey: XECPrivateKey
    val xPublicKey: XECPublicKey

    // Ed25519 keys for signatures
    private val edKeyPair: KeyPair

    private val TAG = "ServerEncryptionHandler"
    private val DERIVE_SHARED_KEY_TAG = "$TAG:DeriveSharedKey"
    private val DERIVE_SHARED_SECRET_TAG= "$TAG:DeriveSharedSecret"
    private val ENCRYPT_DATA_TAG = "$TAG:EncryptData"
    private val VERIFY_SIGNATURE_TAG = "$TAG:VerifySignature"
    private val DECRYPT_DATA_TAG = "$TAG:DecryptData"

    init {
        // Generate X25519 key pair
        val kpg = KeyPairGenerator.getInstance("X25519")
        kpg.initialize(NamedParameterSpec("X25519"))
        val kp = kpg.generateKeyPair()

        // Set keys
        xPrivateKey = kp.private as XECPrivateKey
        xPublicKey = kp.public as XECPublicKey

        // Generate Ed25519 key pair for signatures
        edKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    }

    fun getSigningPublicKey(): PublicKey = edKeyPair.public

    private fun deriveSharedKey(peerPublicKey: XECPublicKey): SecretKeySpec? {
        return try {
            val sharedSecret = deriveSharedSecret(peerPublicKey)
            val hash = MessageDigest.getInstance("SHA-256").digest(sharedSecret)

            SecretKeySpec(hash.copyOf(32), "ChaCha20")
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(DERIVE_SHARED_KEY_TAG, SimpleError.FAIL)
            null
        }
    }

    private fun deriveSharedSecret(peerPublicKey: XECPublicKey): ByteArray? {
        return try {
            val keyAgreement = KeyAgreement.getInstance("X25519")
            keyAgreement.init(xPrivateKey)
            keyAgreement.doPhase(peerPublicKey, true)

            keyAgreement.generateSecret()
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(DERIVE_SHARED_SECRET_TAG, SimpleError.FAIL)
            null
        }

    }

    fun encryptAndSignData(
        data: String,
        peerPublicKey: XECPublicKey
    ): EncryptedData? {
        return try {
            val symmetricKey = deriveSharedKey(peerPublicKey)

            val plaintext = data.toByteArray(Charsets.UTF_8)
            val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }

            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.ENCRYPT_MODE, symmetricKey, IvParameterSpec(nonce))
            val ciphertext = cipher.doFinal(plaintext)

            val signature = Signature.getInstance("Ed25519")
            signature.initSign(edKeyPair.private)
            signature.update(ciphertext)
            val sigBytes = signature.sign()

            EncryptedData(ciphertext, nonce, sigBytes)
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(ENCRYPT_DATA_TAG, SimpleError.FAIL)
            null
        }
    }

    fun verifySignature(
        encryptedData: EncryptedData,
        signingKey: PublicKey
    ): Boolean {
        return try {
            // Verify signature
            val sig = Signature.getInstance("Ed25519")

            sig.initVerify(signingKey)
            sig.update(encryptedData.cipherText)

            val result = sig.verify(encryptedData.signature)

            if (!result) AppLogger.i(VERIFY_SIGNATURE_TAG, SimpleError.FAIL)

            result
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(VERIFY_SIGNATURE_TAG, SimpleError.FAIL)
            false
        }
    }

    fun decryptData(
        encryptedData: EncryptedData,
        publicKey: XECPublicKey
    ): String? {
        return try {
            val symmetricKey = deriveSharedKey(publicKey)

            // Decrypt
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")

            cipher.init(Cipher.DECRYPT_MODE, symmetricKey, IvParameterSpec(encryptedData.nonce))

            val plaintext = cipher.doFinal(encryptedData.cipherText)

            String(plaintext, Charsets.UTF_8)
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(DECRYPT_DATA_TAG, SimpleError.FAIL)
            null
        }
    }

    fun verifyAndDecryptData(
        encryptedData: EncryptedData,
        publicKey: XECPublicKey,
        senderSigningKey: PublicKey
    ): String? {
        return try {
            val symmetricKey = deriveSharedKey(publicKey)

            // Verify signature
            val sig = Signature.getInstance("Ed25519")

            sig.initVerify(senderSigningKey)
            sig.update(encryptedData.cipherText)

            if (!sig.verify(encryptedData.signature)) {
                throw SecurityException("Invalid signature from")
            }

            // Decrypt
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")

            cipher.init(Cipher.DECRYPT_MODE, symmetricKey, IvParameterSpec(encryptedData.nonce))

            val plaintext = cipher.doFinal(encryptedData.cipherText)

            String(plaintext, Charsets.UTF_8)
        } catch (ex: Exception) {
            ex.printStackTrace()
            AppLogger.i(DECRYPT_DATA_TAG, SimpleError.FAIL)
            null
        }
    }
}