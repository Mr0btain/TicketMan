package org.mr0btain.ticketman.components.network.encryption

import org.mr0btain.ticketman.data.encryption.EncryptedMessage
import org.mr0btain.ticketman.server.response.KeyResponse
import java.security.*
import java.security.interfaces.XECPrivateKey
import java.security.interfaces.XECPublicKey
import java.security.spec.NamedParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.uuid.ExperimentalUuidApi

// TODO: Add error handling
// TODO: Setup logging

@OptIn(ExperimentalUuidApi::class)
class ClientEncryptionHandler() {
    // X25519 keys for key exchange
    private val xPrivateKey: XECPrivateKey
    val xPublicKey: XECPublicKey

    // Ed25519 keys for signatures
    private val edKeyPair: KeyPair

    private var sharedSecret: ByteArray? = null
    private var symmetricKey: SecretKey? = null
    private var serverSignature: PublicKey? = null

    init {
        // Generate X25519 key pair
        val kpg = KeyPairGenerator.getInstance("X25519")
        kpg.initialize(NamedParameterSpec("X25519"))
        val kp = kpg.generateKeyPair()
        xPrivateKey = kp.private as XECPrivateKey
        xPublicKey = kp.public as XECPublicKey

        // Generate Ed25519 key pair for signatures
        edKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    }

    // Returns the public key used for signing (Ed25519 key pair)
    fun getSigningPublicKey(): PublicKey = edKeyPair.public

    // Derives a shared symmetric key using X25519 key agreement with the server's public key.
// This key is later used for message encryption/decryption.
    fun deriveSharedKey(keyResponse: KeyResponse) {
        val keyAgreement = KeyAgreement.getInstance("X25519")
        keyAgreement.init(xPrivateKey) // Initialize with client's private X25519 key
        keyAgreement.doPhase(keyResponse.publicKey, true) // Use server's public key to derive shared secret
        sharedSecret = keyAgreement.generateSecret()

        serverSignature = keyResponse.publicSignature // Store server's Ed25519 public key for signature verification

        // Derive symmetric key from shared secret using SHA-256 hash
        val hash = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        symmetricKey = SecretKeySpec(hash.copyOf(32), "ChaCha20")
    }

    // Encrypts the message using ChaCha20-Poly1305 and signs the ciphertext using Ed25519
    fun encryptAndSignMessage(encodedItem: String): EncryptedMessage? {
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

            EncryptedMessage(ciphertext, nonce, sigBytes)
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    // Verifies the signature of a received message and decrypts it
    fun verifyAndDecryptMessage(encryptedData: EncryptedMessage): String? {
        return try {
            requireNotNull(symmetricKey) { "Shared key must be derived first." }
            requireNotNull(serverSignature) { "Server Signature must be received first." }

            // Verify the message signature using the server's Ed25519 public key
            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(serverSignature)
            sig.update(encryptedData.ciphertext)
            if (!sig.verify(encryptedData.signature)) {
                throw SecurityException("Invalid signature from $encryptedData")
            }

            // Decrypt the message using ChaCha20-Poly1305 with the nonce and shared symmetric key
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.DECRYPT_MODE, symmetricKey, IvParameterSpec(encryptedData.nonce))
            val plaintext = cipher.doFinal(encryptedData.ciphertext)

            String(plaintext, Charsets.UTF_8) // Return the original plaintext string
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }

    }

}