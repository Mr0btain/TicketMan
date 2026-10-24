package org.mr0btain.ticketman.components.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.tls.formatFingerprint
import org.mr0btain.ticketman.server.tls.sameFingerprint
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreFoundation.CFArrayAppendValue
import platform.CoreFoundation.CFArrayCreateMutable
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFTypeArrayCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.create
import platform.Foundation.credentialForTrust
import platform.Security.SecCertificateCopyData
import platform.Security.SecCertificateCopySubjectSummary
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecPolicyCreateBasicX509
import platform.Security.SecPolicyCreateSSL
import platform.Security.SecTrustCreateWithCertificates
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustGetCertificateAtIndex
import platform.Security.SecTrustGetCertificateCount
import platform.Security.SecTrustRef
import platform.Security.SecTrustRefVar
import platform.Security.SecTrustSetAnchorCertificates
import platform.Security.SecTrustSetAnchorCertificatesOnly
import platform.Security.SecTrustSetPolicies
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TAG = "DarwinTrust"

/** Biggest CA certificate file read, a real one is a couple of KB */
private const val AUTHORITY_MAX_BYTES = 64 * 1024

private const val AUTHORITY_TIMEOUT_MILLIS = 3_000L

/**
 * iOS's side of [ServerTrust], doing what `PinningTrustManager` and `PinningHostnameVerifier` do on the JVM: accepts
 * what the system trusts, plus certificates from the authority trusted for that address (or that very certificate,
 * for one a server made itself). Anything else is refused and handed to [ServerTrust.refuse], or to
 * [ServerTrust.refuseName] when it's trusted but for other names
 */
@OptIn(ExperimentalForeignApi::class)
internal class DarwinTrust(private val trust: ServerTrust) {
    // Working out what to offer may ask the proxy for its CA, which isn't done on the session's delegate queue
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun handle(
        challenge: NSURLAuthenticationChallenge,
        completion: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
    ) {
        val space = challenge.protectionSpace
        val serverTrust = space.serverTrust
        if (space.authenticationMethod != NSURLAuthenticationMethodServerTrust || serverTrust == null) {
            completion(NSURLSessionAuthChallengePerformDefaultHandling, null)
            return
        }

        val accept = { completion(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(serverTrust)) }
        val refuse = { completion(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null) }

        val host = space.host
        val address = ServerTrust.addressOf(host, space.port.toInt())
        val chain = chainOf(serverTrust)
        val leaf = chain.firstOrNull() ?: return refuse()
        val authority = trust.trustedAuthority(address)

        if (authority != null) {
            // The very certificate trusted, whatever names it's for: its fingerprint already pins it to this address
            if (sameFingerprint(authority.fingerprint, fingerprintOf(leaf))) return accept()

            certificateOf(authority.certificate)?.let { authorityCertificate ->
                if (evaluate(serverTrust, host, authorityCertificate)) return accept()
                if (evaluate(serverTrust, null, authorityCertificate)) return refuseName(address, refuse)
            }
        }

        if (evaluate(serverTrust, host, null)) return accept()
        if (evaluate(serverTrust, null, null)) return refuseName(address, refuse)

        scope.launch {
            try {
                val anchor = anchorOf(chain, host, space.port.toInt())
                val fingerprint = fingerprintOf(anchor)

                // From the authority already trusted, so it failed for another reason (like being out of date), which
                // trusting it again wouldn't fix
                if (authority == null || !sameFingerprint(authority.fingerprint, fingerprint)) {
                    trust.refuse(
                        UntrustedCertificate(
                            address = address,
                            fingerprint = fingerprint,
                            subject = subjectOf(anchor),
                            certificate = base64Of(anchor),
                            isAuthority = !sameFingerprint(fingerprint, fingerprintOf(leaf)),
                            trustedBefore = authority?.fingerprint
                        )
                    )
                }
            } catch (ex: Exception) {
                AppLogger.e(TAG, "Couldn't work out which certificate to offer", ex)
            } finally {
                refuse()
            }
        }
    }

    // iOS doesn't hand apps a certificate's names, so the message says it isn't for the address used instead
    private fun refuseName(address: String, refuse: () -> Unit) {
        trust.refuseName(WrongServerName(address, emptyList()))
        refuse()
    }

    /**
     * What to offer to trust for [chain]: the top certificate when it signed itself (a self-made one, or a proxy that
     * sends its root), else the proxy's CA when it hands one out that signed the top, else the top certificate
     */
    private suspend fun anchorOf(chain: List<SecCertificateRef>, host: String, port: Int): SecCertificateRef {
        val top = chain.last()
        if (isIssuedBy(listOf(top), top)) return top

        val authority = try {
            fetchProxyAuthority(host, port)
        } catch (ex: Exception) {
            null
        }

        return authority?.takeIf { isIssuedBy(listOf(it), it) && isIssuedBy(chain, it) } ?: top
    }
}

// ------------------------------------------ Security framework helpers ------------------------------------------

/**
 * Checks [serverTrust] for [host] (or any name when null), trusting [anchor] as well as the system's authorities
 * when it's given. Leaves the trust set up that way, so the credential made from it after matches what was checked
 */
@OptIn(ExperimentalForeignApi::class)
private fun evaluate(serverTrust: SecTrustRef, host: String?, anchor: SecCertificateRef?): Boolean {
    val hostName = host?.let { CFBridgingRetain(it) as CFStringRef? }
    val policy = SecPolicyCreateSSL(true, hostName)
    hostName?.let { CFRelease(it) }

    SecTrustSetPolicies(serverTrust, policy)
    policy?.let { CFRelease(it) }

    if (anchor != null) {
        val anchors = arrayOf(listOf(anchor))
        SecTrustSetAnchorCertificates(serverTrust, anchors)
        // With the system's authorities as well, not only this one
        SecTrustSetAnchorCertificatesOnly(serverTrust, false)
        CFRelease(anchors)
    } else {
        // Back to the system's authorities alone
        SecTrustSetAnchorCertificates(serverTrust, null)
    }

    return SecTrustEvaluateWithError(serverTrust, null)
}

/** Whether [issuer] (and only it) vouches for [chain], checked as plain X.509 without any names or the system's authorities */
@OptIn(ExperimentalForeignApi::class)
private fun isIssuedBy(chain: List<SecCertificateRef>, issuer: SecCertificateRef): Boolean = memScoped {
    val certificates = arrayOf(chain)
    val anchors = arrayOf(listOf(issuer))
    val policy = SecPolicyCreateBasicX509()
    val trustRef = alloc<SecTrustRefVar>()

    try {
        if (SecTrustCreateWithCertificates(certificates, policy, trustRef.ptr) != 0) return@memScoped false
        val created = trustRef.value ?: return@memScoped false

        SecTrustSetAnchorCertificates(created, anchors)
        SecTrustSetAnchorCertificatesOnly(created, true)
        val issued = SecTrustEvaluateWithError(created, null)
        CFRelease(created)
        issued
    } finally {
        CFRelease(certificates)
        CFRelease(anchors)
        policy?.let { CFRelease(it) }
    }
}

/** A CFArray holding [certificates], released by the caller */
@OptIn(ExperimentalForeignApi::class)
private fun arrayOf(certificates: List<SecCertificateRef>): CFArrayRef {
    val array = CFArrayCreateMutable(null, certificates.size.convert(), kCFTypeArrayCallBacks.ptr)!!
    certificates.forEach { CFArrayAppendValue(array, it) }
    return array
}

/** The certificates the server sent, its own first */
@Suppress("DEPRECATION") // SecTrustCopyCertificateChain replaces these from iOS 15, but they still work and read simpler
@OptIn(ExperimentalForeignApi::class)
private fun chainOf(serverTrust: SecTrustRef): List<SecCertificateRef> =
    (0 until SecTrustGetCertificateCount(serverTrust)).mapNotNull { SecTrustGetCertificateAtIndex(serverTrust, it) }

@OptIn(ExperimentalForeignApi::class)
private fun derOf(certificate: SecCertificateRef): ByteArray {
    val data = CFBridgingRelease(SecCertificateCopyData(certificate)) as NSData
    return data.bytes?.readBytes(data.length.toInt()) ?: ByteArray(0)
}

@OptIn(ExperimentalForeignApi::class)
private fun fingerprintOf(certificate: SecCertificateRef): String {
    val der = derOf(certificate)
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)

    der.usePinned { input ->
        digest.usePinned { output -> CC_SHA256(input.addressOf(0), der.size.convert(), output.addressOf(0)) }
    }

    return formatFingerprint(digest.toByteArray())
}

@OptIn(ExperimentalForeignApi::class)
private fun subjectOf(certificate: SecCertificateRef): String =
    SecCertificateCopySubjectSummary(certificate)?.let { CFBridgingRelease(it) as? String }.orEmpty()

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
private fun base64Of(certificate: SecCertificateRef): String = Base64.Default.encode(derOf(certificate))

/** A certificate from base64 DER, null if it isn't one */
@OptIn(ExperimentalEncodingApi::class)
private fun certificateOf(base64: String): SecCertificateRef? = try {
    certificateFromDer(Base64.Default.decode(base64))
} catch (ex: Exception) {
    null
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun certificateFromDer(der: ByteArray): SecCertificateRef? {
    if (der.isEmpty()) return null

    val data = der.usePinned { NSData.create(bytes = it.addressOf(0), length = der.size.convert()) }
    val cfData = CFBridgingRetain(data) as CFDataRef? ?: return null

    return SecCertificateCreateWithData(null, cfData).also { CFRelease(cfData) }
}

/**
 * The private CA's certificate a proxy hands out at [ServerTrust.AUTHORITY_PATH], or null when it doesn't. Read
 * without checking the connection's certificate, since that's the question being answered: whatever comes back is
 * only offered after it's checked to have signed the server's chain, and someone still has to match its fingerprint
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
private suspend fun fetchProxyAuthority(host: String, port: Int): SecCertificateRef? {
    val anything = HttpClient(Darwin) {
        engine {
            handleChallenge { _, _, challenge, completionHandler ->
                val serverTrust = challenge.protectionSpace.serverTrust
                if (serverTrust != null) {
                    completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(serverTrust))
                } else {
                    completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
                }
            }
        }
        install(HttpTimeout) { requestTimeoutMillis = AUTHORITY_TIMEOUT_MILLIS }
    }

    return anything.use { client ->
        val uriHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
        val response = client.get("https://$uriHost:$port${ServerTrust.AUTHORITY_PATH}")
        if (!response.status.isSuccess()) return@use null

        val bytes = response.bodyAsBytes().takeIf { it.size <= AUTHORITY_MAX_BYTES } ?: return@use null
        val text = bytes.decodeToString()

        // PEM is base64 between the BEGIN and END lines, anything else is taken as DER
        val der = if ("-----BEGIN" in text) {
            Base64.Default.decode(text.lines().filterNot { it.startsWith("-----") }.joinToString("").trim())
        } else {
            bytes
        }
        certificateFromDer(der)
    }
}
