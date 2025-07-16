package org.mr0btain.ticketman.encryption

import org.mr0btain.ticketman.generateJsonLikeMessage
import org.mr0btain.ticketman.generateRandomWordMessage
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.XECPrivateKey
import java.security.interfaces.XECPublicKey
import java.security.spec.NamedParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// TODO: Setup way to store these keys when we get them and look them up based on user id
// TODO: Setup server response error with one that can more accuratly describe error. EX: This one would need badKey error

class EncryptionHandler(val name: String) {
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

fun testEncryptionHandler() {
    val alice = EncryptionHandler("Alice")
    val bob = EncryptionHandler("Bob")

    // Exchange Ed25519 public keys for verification
    val aliceSigningPub = alice.getSigningPublicKey()
    val bobSigningPub = bob.getSigningPublicKey()

    for (i in 0..5) {
        if (i == 0) {
            val aliceMessage = "Hi Bob, it's Alice. This message is signed!"
            val bobMessage = "Hi Alice, I received your secure message. Here's my reply."

            // Alice sends encrypted + signed message to Bob
            val msgFromAlice = alice.encryptAndSignMessage(aliceMessage, bob.xPublicKey)
            println("Alice sent: ${Base64.getEncoder().encodeToString(msgFromAlice.ciphertext)}")

            val decryptedByBob = bob.verifyAndDecryptMessage(msgFromAlice, alice.xPublicKey, aliceSigningPub)

            if (decryptedByBob == aliceMessage) {
                println("Bob Decrypt SUCCESS")
                println("Bob received: $decryptedByBob")
            } else println("Bob Decrypt FAILED")

            // Bob replies
            val msgFromBob = bob.encryptAndSignMessage(bobMessage, alice.xPublicKey)
            println("Bob sent: ${Base64.getEncoder().encodeToString(msgFromBob.ciphertext)}")

            val decryptedByAlice = alice.verifyAndDecryptMessage(msgFromBob, bob.xPublicKey, bobSigningPub)

            if (decryptedByBob == aliceMessage) {
                println("Alice Decrypt SUCCESS")
                println("Alice received: $decryptedByAlice")
            } else println("Alice Decrypt FAILED")
        } else {
            val aliceMessage = generateRandomWordMessage()
            val bobMessage = generateJsonLikeMessage()

            // Alice sends encrypted + signed message to Bob
            val msgFromAlice = alice.encryptAndSignMessage(aliceMessage, bob.xPublicKey)
            println("Alice sent: ${Base64.getEncoder().encodeToString(msgFromAlice.ciphertext)}")

            val decryptedByBob = bob.verifyAndDecryptMessage(msgFromAlice, alice.xPublicKey, aliceSigningPub)

            if (decryptedByBob == aliceMessage) {
                println("Bob Decrypt SUCCESS")
                println("Bob received: $decryptedByBob")
            } else println("Bob Decrypt FAILED")

            // Bob replies
            val msgFromBob = bob.encryptAndSignMessage(bobMessage, alice.xPublicKey)
            println("Bob sent: ${Base64.getEncoder().encodeToString(msgFromBob.ciphertext)}")

            val decryptedByAlice = alice.verifyAndDecryptMessage(msgFromBob, bob.xPublicKey, bobSigningPub)

            if (decryptedByBob == aliceMessage) {
                println("Alice Decrypt SUCCESS")
                println("Alice received: $decryptedByAlice")
            } else println("Alice Decrypt FAILED")
        }
    }
}


