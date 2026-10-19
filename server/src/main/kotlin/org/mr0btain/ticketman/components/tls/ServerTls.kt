package org.mr0btain.ticketman.components.tls

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import io.ktor.network.tls.extensions.SignatureAlgorithm
import io.ktor.server.config.*
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.response.ServerCertificateInfo
import org.mr0btain.ticketman.server.tls.formatFingerprint
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import javax.security.auth.x500.X500Principal

/** What the plain HTTP port does once HTTPS is on */
enum class HttpMode {
    /** Answers as usual. The default in development mode, for the web client's dev server and test scripts */
    ON,

    /** Sends everything to the HTTPS port. The default, clients follow it to the `https://` address */
    REDIRECT,

    /** Not opened at all */
    OFF;

    companion object {
        /** @param default What a blank or unknown value means */
        fun parse(value: String?, default: HttpMode = ON): HttpMode =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: default
    }
}

/**
 * The `tls` section of application.yaml
 * @param httpsPort 0 turns HTTPS off
 * @param keyStorePath A PKCS12 file to use instead of the generated certificate, blank to generate one
 * @param keyAlias Which entry in [keyStorePath] to use, blank for the first one with a private key
 * @param privateKeyPassword Blank when it's the same as [keyStorePassword]
 */
data class TlsSettings(
    val httpsPort: Int,
    val httpMode: HttpMode,
    val keyStorePath: String,
    val keyStorePassword: String,
    val keyAlias: String,
    val privateKeyPassword: String
) {
    val enabled: Boolean get() = httpsPort > 0

    companion object {
        /**
         * Tests don't load application.yaml, so a missing section means HTTPS is off
         * @param developmentMode Plain HTTP stays on by default in development mode, and redirects otherwise
         */
        fun from(config: ApplicationConfig, developmentMode: Boolean = false): TlsSettings {
            fun value(key: String) = config.propertyOrNull("tls.$key")?.getString()?.trim().orEmpty()

            return TlsSettings(
                httpsPort = value("port").toIntOrNull() ?: 0,
                httpMode = HttpMode.parse(value("httpMode"), if (developmentMode) HttpMode.ON else HttpMode.REDIRECT),
                keyStorePath = value("keyStore"),
                keyStorePassword = value("keyStorePassword"),
                keyAlias = value("keyAlias"),
                privateKeyPassword = value("privateKeyPassword")
            )
        }
    }
}

/**
 * A key and certificate ready for the HTTPS connector
 * @param generated Made by [ServerTls] rather than supplied
 */
class ServerCertificate(
    val keyStore: KeyStore,
    val alias: String,
    val keyStorePassword: String,
    val privateKeyPassword: String,
    val generated: Boolean
) {
    val certificate: X509Certificate = keyStore.getCertificate(alias) as X509Certificate

    val fingerprint: String = formatFingerprint(MessageDigest.getInstance("SHA-256").digest(certificate.encoded))

    fun info(settings: TlsSettings) = ServerCertificateInfo(
        fingerprint = fingerprint,
        subject = certificate.subjectX500Principal.name,
        expiresAt = certificate.notAfter.time,
        generated = generated,
        httpsPort = settings.httpsPort,
        httpMode = settings.httpMode.name.lowercase()
    )
}

/**
 * Finds the certificate the server answers HTTPS with: the one supplied through `SSL_KEYSTORE`, or one the server
 * made itself in `<dataPath>/server/tls/`. A made one lasts ten years and is kept, since every device that trusted
 * it would be asked again if it changed. Delete the folder to make a new one
 */
object ServerTls {
    private const val TAG = "ServerTls"
    private const val LOAD_TAG = "$TAG:Load"
    private const val GENERATE_TAG = "$TAG:Generate"

    const val ALIAS = "ticketman"
    private const val KEYSTORE_FILE = "server.p12"
    private const val PASSWORD_FILE = "server.p12.password"
    private const val DAYS_VALID = 3650L

    fun folder(dataPath: String) = File(dataPath, "server/tls")

    /**
     * Throws when a supplied keystore can't be used, so a typo stops the server rather than quietly serving a
     * different certificate
     */
    fun load(settings: TlsSettings, dataPath: String): ServerCertificate {
        return if (settings.keyStorePath.isNotBlank()) loadSupplied(settings) else loadOrGenerate(folder(dataPath))
    }

    private fun loadSupplied(settings: TlsSettings): ServerCertificate {
        val file = File(settings.keyStorePath)
        check(file.isFile) { "SSL_KEYSTORE ${file.absolutePath} doesn't exist" }

        val keyStore = readKeyStore(file, settings.keyStorePassword)
        val alias = settings.keyAlias.ifBlank { keyStore.aliases().toList().firstOrNull { keyStore.isKeyEntry(it) }.orEmpty() }
        check(alias.isNotBlank() && keyStore.isKeyEntry(alias)) {
            if (settings.keyAlias.isBlank()) "SSL_KEYSTORE has no private key in it" else "SSL_KEYSTORE has no key called $alias"
        }

        AppLogger.m(LOAD_TAG, "Using the certificate in ${file.absolutePath}")
        return ServerCertificate(
            keyStore = keyStore,
            alias = alias,
            keyStorePassword = settings.keyStorePassword,
            privateKeyPassword = settings.privateKeyPassword.ifBlank { settings.keyStorePassword },
            generated = false
        )
    }

    /** Reads the certificate made on an earlier start, or makes one */
    fun loadOrGenerate(folder: File): ServerCertificate {
        val keyStoreFile = File(folder, KEYSTORE_FILE)
        val passwordFile = File(folder, PASSWORD_FILE)

        if (keyStoreFile.isFile && passwordFile.isFile) {
            // Not regenerated when it can't be read, a new one would make every device ask to trust it again
            val password = passwordFile.readText().trim()
            return ServerCertificate(readKeyStore(keyStoreFile, password), ALIAS, password, password, generated = true)
        }

        val password = ByteArray(24).also { SecureRandom().nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val hostName = localHostName()

        val keyStore = buildKeyStore {
            certificate(ALIAS) {
                this.password = password
                hash = HashAlgorithm.SHA256
                sign = SignatureAlgorithm.RSA
                keySizeInBits = 2048
                daysValid = DAYS_VALID
                subject = X500Principal("CN=TicketMan server ${hostName.ifBlank { "localhost" }}")
                domains = listOfNotNull("localhost", hostName.takeIf { it.isNotBlank() && it != "localhost" })
                ipAddresses = localAddresses()
            }
        }

        folder.mkdirs()
        keyStoreFile.outputStream().use { keyStore.store(it, password.toCharArray()) }
        passwordFile.writeText(password)
        listOf(keyStoreFile, passwordFile).forEach { ownerOnly(it) }

        AppLogger.m(GENERATE_TAG, "Made a certificate for HTTPS in ${folder.absolutePath}")
        return ServerCertificate(keyStore, ALIAS, password, password, generated = true)
    }

    private fun readKeyStore(file: File, password: String): KeyStore = KeyStore.getInstance("PKCS12").apply {
        file.inputStream().use { load(it, password.toCharArray()) }
    }

    private fun localHostName(): String = try {
        InetAddress.getLocalHost().hostName.orEmpty()
    } catch (ex: Exception) {
        ""
    }

    // So browsers on the shop network see an address they used. Devices that pin the certificate don't need it
    private fun localAddresses(): List<InetAddress> {
        val found = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
        } catch (ex: Exception) {
            AppLogger.i(GENERATE_TAG, "Couldn't list this machine's addresses: $ex")
            emptyList()
        }

        return (listOf(InetAddress.getByName("127.0.0.1")) + found).distinct()
    }

    // The keystore password sits next to it, so only the server's user should read either
    /** Only the account running the server can read or change [file] */
    internal fun ownerOnly(file: File) {
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
    }
}
