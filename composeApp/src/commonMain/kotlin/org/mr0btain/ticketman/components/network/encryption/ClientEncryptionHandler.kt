package org.mr0btain.ticketman.components.network.encryption

import org.mr0btain.ticketman.data.encryption.EncryptedData
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.response.KeyResponse
import kotlin.uuid.ExperimentalUuidApi

// TODO: Add error handling
// TODO: Setup logging

//@Suppress("PrivatePropertyName")
/*
class ClientEncryptionHandler() {
    // X25519 keys for key exchange
    private val xPrivateKey: String = "" //XECPrivateKey
    val xPublicKey: String = "" //XECPublicKey

    // Ed25519 keys for signatures
    private val edKeyPair: String = "" //KeyPair

    private var sharedSecret: ByteArray? = null
    private var symmetricKey: String? = null //SecretKey? = null
    private var serverSignature: String? = null //PublicKey? = null

    private val TAG = "EncryptionHandler"
    private val DERIVE_SHARED_KEY_TAG = "$TAG:DeriveSharedKey"
    private val ENCRYPT_TAG = "$TAG:Encrypt"
    private val DECRYPT_TAG = "$TAG:Decrypt"

    init {
        /*
        // Generate X25519 key pair
        val kpg = KeyPairGenerator.getInstance("X25519")
        kpg.initialize(NamedParameterSpec("X25519"))
        val kp = kpg.generateKeyPair()
        xPrivateKey = kp.private as XECPrivateKey
        xPublicKey = kp.public as XECPublicKey

        // Generate Ed25519 key pair for signatures
        edKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
         */
    }

    // Returns the public key used for signing (Ed25519 key pair)
    fun getSigningPublicKey(): String? = null //PublicKey = edKeyPair.public

    // Derives a shared symmetric key using X25519 key agreement with the server's public key.
    // This key is later used for message encryption/decryption.
    fun deriveSharedKey(keyResponse: KeyResponse) {
        /*
        try {
            val keyAgreement = KeyAgreement.getInstance("X25519")
            keyAgreement.init(xPrivateKey) // Initialize with client's private X25519 key
            keyAgreement.doPhase(keyResponse.publicKey, true) // Use server's public key to derive shared secret
            sharedSecret = keyAgreement.generateSecret()

            serverSignature = keyResponse.publicSignature // Store server's Ed25519 public key for signature verification

            // Derive symmetric key from shared secret using SHA-256 hash
            val hash = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
            symmetricKey = SecretKeySpec(hash.copyOf(32), "ChaCha20")

            AppLogger.m(DERIVE_SHARED_KEY_TAG, SimpleResult.SUCCESS)
        } catch (ex: Exception) {
            AppLogger.e(DERIVE_SHARED_KEY_TAG, SimpleResult.FAIL, ex)
        }
         */

    }

    // Encrypts the message using ChaCha20-Poly1305 and signs the ciphertext using Ed25519
    fun encryptData(encodedItem: String): EncryptedData? {
        /*
        return try {
            //requireNotNull(symmetricKey) { "Shared key must be derived first." }
            if (symmetricKey == null) return null

            // Encode to byte array
            val itemByteArray = encodedItem.toByteArray(Charsets.UTF_8)

            // Generate a 12-byte random nonce for ChaCha20
            val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }

            // Encrypt plaintext using ChaCha20-Poly1305 with the derived symmetric key and nonce
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.ENCRYPT_MODE, symmetricKey, IvParameterSpec(nonce))
            val ciphertext = cipher.doFinal(itemByteArray)

            // Sign the ciphertext using the Ed25519 private signing key
            val signature = Signature.getInstance("Ed25519")
            signature.initSign(edKeyPair.private)
            signature.update(ciphertext)
            val sigBytes = signature.sign()

            AppLogger.m(ENCRYPT_TAG, SimpleResult.SUCCESS)

            EncryptedData(ciphertext, nonce, sigBytes)
        } catch (ex: Exception) {
            AppLogger.e(ENCRYPT_TAG, SimpleResult.FAIL, ex)
            null
        }
         */
        return null
    }

    // Verifies the signature of a received message and decrypts it
    fun decryptData(encryptedData: EncryptedData): String? {
       /*
        return try {
            requireNotNull(symmetricKey) { "Shared key must be derived first." }
            requireNotNull(serverSignature) { "Server Signature must be received first." }

            // Verify the message signature using the server's Ed25519 public key
            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(serverSignature)
            sig.update(encryptedData.cipherText)
            if (!sig.verify(encryptedData.signature)) {
                throw SecurityException("Invalid signature from $encryptedData")
            }

            // Decrypt the message using ChaCha20-Poly1305 with the nonce and shared symmetric key
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.DECRYPT_MODE, symmetricKey, IvParameterSpec(encryptedData.nonce))
            val plaintext = cipher.doFinal(encryptedData.cipherText)

            AppLogger.m(DECRYPT_TAG, SimpleResult.SUCCESS)

            String(plaintext, Charsets.UTF_8) // Return the original plaintext string
        } catch (ex: Exception) {
            AppLogger.e(DECRYPT_TAG, SimpleResult.FAIL, ex)
            null
        }

        */
        return null
    }

}
 */