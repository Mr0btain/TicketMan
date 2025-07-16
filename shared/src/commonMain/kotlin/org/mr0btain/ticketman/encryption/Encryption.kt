package org.mr0btain.ticketman.encryption

import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.XECPrivateKey
import java.security.interfaces.XECPublicKey
import java.security.spec.NamedParameterSpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.security.*
import javax.crypto.spec.IvParameterSpec

// TODO: Use authentication to discern who it is and their public key
// TODO: Store public keys somehow. probably just in ram
// TODO: Implement into routes

class SecureClient(val name: String) {
    // X25519 keys for key exchange
    private val xPrivateKey: XECPrivateKey
    val xPublicKey: XECPublicKey

    // Ed25519 keys for signatures
    private val edKeyPair: KeyPair

    private var sharedSecret: ByteArray? = null
    private var symmetricKey: SecretKey? = null

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

    fun deriveSharedKey(peerPublicKey: XECPublicKey) {
        val keyAgreement = KeyAgreement.getInstance("X25519")
        keyAgreement.init(xPrivateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        sharedSecret = keyAgreement.generateSecret()

        val hash = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        symmetricKey = SecretKeySpec(hash.copyOf(32), "ChaCha20")
    }

    fun encryptAndSignMessage(message: String): EncryptedMessage {
        requireNotNull(symmetricKey) { "Shared key must be derived first." }

        val plaintext = message.toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.ENCRYPT_MODE, symmetricKey, IvParameterSpec(nonce))
        val ciphertext = cipher.doFinal(plaintext)

        val signature = Signature.getInstance("Ed25519")
        signature.initSign(edKeyPair.private)
        signature.update(ciphertext)
        val sigBytes = signature.sign()

        return EncryptedMessage(name, ciphertext, nonce, sigBytes)
    }

    fun verifyAndDecryptMessage(message: EncryptedMessage, senderSigningKey: PublicKey): String {
        requireNotNull(symmetricKey) { "Shared key must be derived first." }

        // Verify signature
        val sig = Signature.getInstance("Ed25519")
        sig.initVerify(senderSigningKey)
        sig.update(message.ciphertext)
        if (!sig.verify(message.signature)) {
            throw SecurityException("Invalid signature from ${message.senderIdentifier}")
        }

        // Decrypt
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.DECRYPT_MODE, symmetricKey, IvParameterSpec(message.nonce))
        val plaintext = cipher.doFinal(message.ciphertext)

        return String(plaintext, Charsets.UTF_8)
    }
}


fun testSecureClient() {
    val alice = SecureClient("Alice")
    val bob = SecureClient("Bob")

    // Exchange public keys and derive shared keys
    alice.deriveSharedKey(bob.xPublicKey)
    bob.deriveSharedKey(alice.xPublicKey)

    // Exchange Ed25519 public keys for verification
    val aliceSigningPub = alice.getSigningPublicKey()
    val bobSigningPub = bob.getSigningPublicKey()

    // Alice sends encrypted + signed message to Bob
    val msgFromAlice = alice.encryptAndSignMessage("Hi Bob, it's Alice. This message is signed!")
    println("Alice sent: ${Base64.getEncoder().encodeToString(msgFromAlice.ciphertext)}")

    val decryptedByBob = bob.verifyAndDecryptMessage(msgFromAlice, aliceSigningPub)
    println("Bob received: $decryptedByBob")

    // Bob replies
    val msgFromBob = bob.encryptAndSignMessage("Hi Alice, I received your secure message. Here's my reply.")
    println("Bob sent: ${Base64.getEncoder().encodeToString(msgFromBob.ciphertext)}")

    val decryptedByAlice = alice.verifyAndDecryptMessage(msgFromBob, bobSigningPub)
    println("Alice received: $decryptedByAlice")
}

class SecureClient2(val name: String) {
    private val privateKey: XECPrivateKey
    val publicKey: XECPublicKey

    private var sharedSecret: ByteArray? = null
    private var symmetricKey: SecretKey? = null

    init {
        val kpg = KeyPairGenerator.getInstance("X25519")
        kpg.initialize(NamedParameterSpec("X25519"))
        val kp = kpg.generateKeyPair()
        privateKey = kp.private as XECPrivateKey
        publicKey = kp.public as XECPublicKey
    }

    fun deriveSharedKey(peerPublicKey: XECPublicKey) {
        val keyAgreement = KeyAgreement.getInstance("X25519")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        sharedSecret = keyAgreement.generateSecret()

        // Derive 256-bit key from shared secret using SHA-256
        val hash = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        symmetricKey = SecretKeySpec(hash.copyOf(32), "ChaCha20")
    }

    fun encryptMessage(message: String): Pair<ByteArray, ByteArray> {
        requireNotNull(symmetricKey) { "Shared key must be derived first." }

        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.ENCRYPT_MODE, symmetricKey, IvParameterSpec(nonce))
        val ciphertext = cipher.doFinal(message.toByteArray(Charsets.UTF_8))

        return nonce to ciphertext
    }

    fun decryptMessage(nonce: ByteArray, ciphertext: ByteArray): String {
        requireNotNull(symmetricKey) { "Shared key must be derived first." }

        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(Cipher.DECRYPT_MODE, symmetricKey, IvParameterSpec(nonce))
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }
}

fun testSecureClient2() {
    // Create clients
    val alice = SecureClient2("Alice")
    val bob = SecureClient2("Bob")

    // Exchange public keys and derive shared keys
    alice.deriveSharedKey(bob.publicKey)
    bob.deriveSharedKey(alice.publicKey)

    // Alice sends a message to Bob
    val messageFromAlice = "Hi Bob! This is a secure message from Alice."
    val (nonce, ciphertext) = alice.encryptMessage(messageFromAlice)

    println("Encrypted by Alice (Base64): ${Base64.getEncoder().encodeToString(ciphertext)}")
    println("Nonce (Base64):              ${Base64.getEncoder().encodeToString(nonce)}")

    // Bob decrypts
    val decryptedByBob = bob.decryptMessage(nonce, ciphertext)
    println("Decrypted by Bob: $decryptedByBob")
}
