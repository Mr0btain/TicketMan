package org.mr0btain.ticketman.components.encryption

import org.mr0btain.ticketman.data.encryption.EncryptedMessage
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

open class ServerEncryptionHandler() {
    // X25519 keys for key exchange
    private val xPrivateKey: XECPrivateKey
    val xPublicKey: XECPublicKey

    // Ed25519 keys for signatures
    private val edKeyPair: KeyPair

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

    private fun deriveSharedKey(peerPublicKey: XECPublicKey): SecretKeySpec {
        val sharedSecret = deriveSharedSecret(peerPublicKey)
        val hash = MessageDigest.getInstance("SHA-256").digest(sharedSecret)

        return SecretKeySpec(hash.copyOf(32), "ChaCha20")
    }

    private fun deriveSharedSecret(peerPublicKey: XECPublicKey): ByteArray? {
        val keyAgreement = KeyAgreement.getInstance("X25519")
        keyAgreement.init(xPrivateKey)
        keyAgreement.doPhase(peerPublicKey, true)

        return keyAgreement.generateSecret()
    }

    fun encryptAndSignMessage(message: String, peerPublicKey: XECPublicKey): EncryptedMessage {
        val symmetricKey = deriveSharedKey(peerPublicKey)

        val plaintext = message.toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.ENCRYPT_MODE, symmetricKey, IvParameterSpec(nonce))
        val ciphertext = cipher.doFinal(plaintext)

        val signature = Signature.getInstance("Ed25519")
        signature.initSign(edKeyPair.private)
        signature.update(ciphertext)
        val sigBytes = signature.sign()

        return EncryptedMessage(ciphertext, nonce, sigBytes)
    }

    fun verifySignature(
        message: EncryptedMessage,
        signingKey: PublicKey
    ): Boolean {
        // Verify signature
        val sig = Signature.getInstance("Ed25519")

        sig.initVerify(signingKey)
        sig.update(message.ciphertext)

        return sig.verify(message.signature)
    }

    fun decryptMessage(
        message: EncryptedMessage,
        publicKey: XECPublicKey
    ): String? {
        val symmetricKey = deriveSharedKey(publicKey)

        // Decrypt
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")

        cipher.init(Cipher.DECRYPT_MODE, symmetricKey, IvParameterSpec(message.nonce))

        val plaintext = cipher.doFinal(message.ciphertext)

        return String(plaintext, Charsets.UTF_8)
    }

    fun verifyAndDecryptMessage(
        message: EncryptedMessage,
        publicKey: XECPublicKey,
        senderSigningKey: PublicKey
    ): String {
        val symmetricKey = deriveSharedKey(publicKey)

        // Verify signature
        val sig = Signature.getInstance("Ed25519")

        sig.initVerify(senderSigningKey)
        sig.update(message.ciphertext)

        if (!sig.verify(message.signature)) {
            throw SecurityException("Invalid signature from")
        }

        // Decrypt
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")

        cipher.init(Cipher.DECRYPT_MODE, symmetricKey, IvParameterSpec(message.nonce))

        val plaintext = cipher.doFinal(message.ciphertext)

        return String(plaintext, Charsets.UTF_8)
    }
}