package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.NotifyDatabase
import org.mr0btain.ticketman.components.database.PaymentDatabase
import org.mr0btain.ticketman.components.database.TokenTable
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.database.tables.notify.NotifySettingsTable
import org.mr0btain.ticketman.components.database.tables.payment.PaymentSettingsTable
import org.mr0btain.ticketman.components.service.PasscodeVault
import org.mr0btain.ticketman.components.service.SealedSecrets
import org.mr0btain.ticketman.components.service.sweepStep
import org.mr0btain.ticketman.data.notify.NotificationSettings
import org.mr0btain.ticketman.data.notify.SmsSettings
import org.mr0btain.ticketman.data.notify.SmtpSettings
import org.mr0btain.ticketman.data.payment.PaymentProvider
import org.mr0btain.ticketman.data.payment.PaymentSettings
import org.mr0btain.ticketman.data.payment.StripeSettings
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.SessionResponse
import kotlin.test.*

/** A copy of the database or a backup shouldn't hold anything that logs in or reaches the shop's accounts */
class SecretsAtRestTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }
    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.login(): AuthResponse {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().add(admin)
        return json.decodeFromString(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest(admin.username, admin.password, "desk", "Desk")))
            }.bodyAsText()
        )
    }

    @Test
    fun refreshTokensAreOnlyKeptHashed() = testApplication {
        val auth = login()
        val refresh = auth.refreshToken!!

        val stored = transaction { TokenTable.selectAll().single()[TokenTable.token] }
        assertNotEquals(refresh, stored)
        assertFalse(stored.contains("."), "That's a token, not a hash of one")

        // It still works, and the sessions list still knows when it runs out
        val refreshed = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"token":"$refresh"}""")
        }
        assertEquals(HttpStatusCode.Accepted, refreshed.status)
        val sessions = json.decodeFromString<List<SessionResponse>>(client.get("/api/admin/sessions") { bearerAuth(auth.accessToken!!) }.bodyAsText())
        assertNotNull(sessions.single().expiresAt)

        // What's stored can't be sent as the token
        val hash = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"token":"$stored"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, hash.status)
    }

    @Test
    fun savedKeysAreSealedAndComeBackAsSaved() = testApplication {
        login()
        val payments = PaymentSettings(enabled = true, provider = PaymentProvider.STRIPE, stripe = StripeSettings(secretKey = "sk_live_secret", webhookSecret = "whsec_secret"))
        val messages = NotificationSettings(smtp = SmtpSettings(password = "mail_secret"), sms = SmsSettings(authToken = "text_secret"))
        assertTrue(PaymentDatabase().saveSettings(payments))
        assertTrue(NotifyDatabase().saveSettings(messages))

        val rawPayments = transaction { PaymentSettingsTable.selectAll().single()[PaymentSettingsTable.settings] }
        val rawMessages = transaction { NotifySettingsTable.selectAll().single()[NotifySettingsTable.settings] }
        listOf(rawPayments.stripe.secretKey, rawPayments.stripe.webhookSecret, rawMessages.smtp.password, rawMessages.sms.authToken).forEach { stored ->
            assertTrue(stored!!.startsWith("sealed:"), "$stored is readable")
        }

        assertEquals("sk_live_secret", PaymentDatabase().settings().stripe.secretKey)
        assertEquals("text_secret", NotifyDatabase().settings().sms.authToken)
    }

    @Test
    fun keysFromBeforeSealingGetSealedAndOnesThatWontOpenAreLeftAlone() = testApplication {
        login()
        val vault = SealedSecrets.vault

        // Saved plain, like an older version did
        SealedSecrets.vault = null
        PaymentDatabase().saveSettings(PaymentSettings(stripe = StripeSettings(secretKey = "sk_old")))
        SealedSecrets.vault = vault
        assertEquals("sk_old", PaymentDatabase().settings().stripe.secretKey)

        PaymentDatabase().sealStoredSecrets()
        val sealed = transaction { PaymentSettingsTable.selectAll().single()[PaymentSettingsTable.settings] }.stripe.secretKey
        assertTrue(sealed!!.startsWith("sealed:"))
        assertEquals("sk_old", PaymentDatabase().settings().stripe.secretKey)

        // Under another secret it reads as not set, and starting up doesn't lose it for when the right one comes back
        SealedSecrets.vault = PasscodeVault("another secret")
        assertNull(PaymentDatabase().settings().stripe.secretKey)
        PaymentDatabase().sealStoredSecrets()
        SealedSecrets.vault = vault
        assertEquals("sk_old", PaymentDatabase().settings().stripe.secretKey)
    }

    @Test
    fun aSweepStepGoingWrongDoesntStopTheNext() = runTest {
        val ran = mutableListOf<String>()

        sweepStep("first") { throw IllegalStateException("Disk full") }
        sweepStep("second") { ran += "second" }

        assertEquals(listOf("second"), ran)
        assertFailsWith<CancellationException> { sweepStep("stopping") { throw CancellationException("Stopping") } }
    }
}
