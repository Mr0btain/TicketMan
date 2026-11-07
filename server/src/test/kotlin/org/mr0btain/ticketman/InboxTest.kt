package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.notify.NotificationSenders
import org.mr0btain.ticketman.components.notify.SendResult
import org.mr0btain.ticketman.components.notify.SmsSender
import org.mr0btain.ticketman.components.notify.TwilioSignature
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.notify.*
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.TicketMessageRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Texts customers send back: Twilio's webhook, matching them to the customer and ticket, reading and replying
 */
@OptIn(ExperimentalUuidApi::class)
class InboxTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val viewer = User(username = "viewer", password = "viewer_pass", accessLevel = AccessLevel.RESTRICTED)

    private val publicUrl = "https://repairs.example.com"
    private val webhook = "$publicUrl/status/sms/twilio/webhook"
    private val authToken = "sms-secret"
    private val settings = NotificationSettings(
        smsEnabled = true,
        sms = SmsSettings(accountSid = "AC123", authToken = authToken, fromNumber = "+15550000000")
    )

    private val texts = mutableListOf<Pair<String, String>>()
    private lateinit var token: String
    private lateinit var adminToken: String

    @BeforeTest
    fun fakeSender() {
        NotificationSenders.sms = SmsSender { _, to, body ->
            synchronized(texts) { texts += to to body }
            SendResult.Sent
        }
    }

    @AfterTest
    fun cleanup() {
        NotificationSenders.reset()
        cleanupTestDataDir(dataDir)
    }

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
            add(viewer)
        }
        adminToken = login(admin)
        token = login(tech)
        assertEquals(HttpStatusCode.Accepted, post("/api/shop/update", json.encodeToString(ShopProfile(name = "Fix It Shop", publicUrl = publicUrl)), adminToken).status)
        assertEquals(HttpStatusCode.OK, post("/api/notify/settings", json.encodeToString(settings), adminToken).status)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }
        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, as_: String = token) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(as_)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.newTicket(customer: Customer): Ticket {
        assertEquals(HttpStatusCode.Created, post("/api/data/customer/add", json.encodeToString(customer)).status)
        val ticket = Ticket(device = Device("Apple", "iPhone 14"))
        assertEquals(HttpStatusCode.Created, post("/api/data/ticket/add", json.encodeToString(ticket)).status)
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/set/customer/${ticket.identifier}", json.encodeToString(customer.identifier)).status)
        return ticket
    }

    /** Twilio handing over a text, signed with [signedWith] */
    private suspend fun ApplicationTestBuilder.text(from: String, body: String, sid: String = "SM" + Uuid.random().toHexString(), signedWith: String = authToken) =
        mapOf("MessageSid" to sid, "From" to from, "To" to "+15550000000", "Body" to body).let { params ->
            client.submitForm(url = "/status/sms/twilio/webhook", formParameters = parameters { params.forEach { (key, value) -> append(key, value) } }) {
                header("X-Twilio-Signature", TwilioSignature.of(signedWith, webhook, params))
            }
        }

    private suspend fun ApplicationTestBuilder.inbox(): InboxResponse =
        json.decodeFromString(client.get("/api/notify/inbox") { bearerAuth(token) }.bodyAsText())

    @Test
    fun aReplyToATextLandsOnItsTicketUntilItsRead() = testApplication {
        setup()
        val jane = Customer(name = "Jane Doe", phone = "(555) 123-4567")
        val ticket = newTicket(jane)
        val link = post("/api/notify/ticket/${ticket.identifier}/send", json.encodeToString(TicketMessageRequest(channels = setOf(NotificationChannel.SMS))))
        assertEquals(HttpStatusCode.Created, link.status, link.bodyAsText())

        val answered = text("+15551234567", "Is it ready yet?")
        assertEquals(HttpStatusCode.OK, answered.status, answered.bodyAsText())
        assertEquals("<Response/>", answered.bodyAsText())

        val received = inbox()
        assertEquals(1, received.unread)
        val message = received.messages.single()
        assertEquals(jane.identifier, message.customerIdentifier)
        assertEquals("Jane Doe", message.customerName)
        assertEquals(ticket.identifier, message.ticketIdentifier)
        assertEquals("Is it ready yet?", message.body)

        val onTicket = json.decodeFromString<List<InboundMessage>>(client.get("/api/notify/ticket/${ticket.identifier}/received") { bearerAuth(login(viewer)) }.bodyAsText())
        assertEquals(listOf(message.identifier), onTicket.map { it.identifier })

        assertEquals(HttpStatusCode.Accepted, client.post("/api/notify/ticket/${ticket.identifier}/read") { bearerAuth(token) }.status)
        assertEquals(0, inbox().unread)
        assertEquals("tech", inbox().messages.single().readBy)
    }

    @Test
    fun aTextWithTheWrongSignatureIsRefusedAndOneSentTwiceIsKeptOnce() = testApplication {
        setup()
        assertEquals(HttpStatusCode.Forbidden, text("+15551234567", "Hello", signedWith = "guess").status)
        assertEquals(0, inbox().messages.size)

        text("+15551234567", "Hello", sid = "SM1")
        text("+15551234567", "Hello", sid = "SM1")
        assertEquals(1, inbox().messages.size)
    }

    @Test
    fun aTextFromAKnownNumberFindsItsCustomerAndTheirOneOpenTicket() = testApplication {
        setup()
        val ticket = newTicket(Customer(name = "Jane Doe", phone = "555 123 4567"))
        text("+15551234567", "Did you get my phone?")
        assertEquals(ticket.identifier, inbox().messages.single().ticketIdentifier)
    }

    @Test
    fun aTextFromAKnownNumberFindsItsCustomerAndAnUnknownOneIsStillKept() = testApplication {
        setup()
        val jane = Customer(name = "Jane Doe", phone = "555.123.4567")
        assertEquals(HttpStatusCode.Created, post("/api/data/customer/add", json.encodeToString(jane)).status)

        text("+15551234567", "Do you fix tablets?")
        text("+15559998888", "Wrong number?")

        val (stranger, known) = inbox().messages
        assertEquals(jane.identifier, known.customerIdentifier)
        assertNull(known.ticketIdentifier)
        assertNull(stranger.customerIdentifier)
        assertEquals("+15559998888", stranger.sender)

        assertEquals(HttpStatusCode.Accepted, client.post("/api/notify/inbox/${stranger.identifier}/read") { bearerAuth(token) }.status)
        assertEquals(1, inbox().unread)
    }

    @Test
    fun stopTurnsOffTheirTexts() = testApplication {
        setup()
        val jane = Customer(name = "Jane Doe", phone = "5551234567", notifySms = true)
        newTicket(jane)

        text("+15551234567", "Stop")

        val customer = json.decodeFromString<Customer>(client.get("/api/data/customer/get/${jane.identifier}") { bearerAuth(token) }.bodyAsText())
        assertFalse(customer.notifySms)
        assertEquals(1, customer.revision)
        assertTrue(inbox().messages.single().optedOut)
    }

    @Test
    fun staffReplyInTheShopsWordsAndReplyingReadsTheirTexts() = testApplication {
        setup()
        val ticket = newTicket(Customer(name = "Jane Doe", phone = "5551234567"))
        text("+15551234567", "Any news?")

        assertEquals(HttpStatusCode.BadRequest, post("/api/notify/ticket/${ticket.identifier}/send", json.encodeToString(TicketMessageRequest(NotificationEvent.REPLY, setOf(NotificationChannel.SMS), "  "))).status)

        val reply = post("/api/notify/ticket/${ticket.identifier}/send", json.encodeToString(TicketMessageRequest(NotificationEvent.REPLY, setOf(NotificationChannel.SMS), "Ready at 3pm")))
        assertEquals(HttpStatusCode.Created, reply.status, reply.bodyAsText())
        val queued = json.decodeFromString<List<OutboxMessage>>(reply.bodyAsText()).single()
        assertEquals("Ready at 3pm\nFix It Shop", queued.body)
        assertEquals("+15551234567", queued.recipient)
        assertEquals(0, inbox().unread)
    }
}
