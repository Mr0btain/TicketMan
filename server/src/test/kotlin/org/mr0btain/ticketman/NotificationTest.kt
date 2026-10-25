package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.*
import org.mr0btain.ticketman.components.notify.NotificationSenders
import org.mr0btain.ticketman.components.notify.NotificationService
import org.mr0btain.ticketman.components.notify.SendResult
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.notify.*
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.TestMessageRequest
import org.mr0btain.ticketman.server.request.TicketMessageRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Messages to customers: settings, what's sent when, the outbox's retries and pickup reminders. The senders are
 * swapped for ones that only write down what they were given
 */
@OptIn(ExperimentalUuidApi::class)
class NotificationTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val viewer = User(username = "viewer", password = "viewer_pass", accessLevel = AccessLevel.RESTRICTED)

    private val awaitingParts = CustomStatus(name = "Awaiting parts", actsAs = TicketStatusType.ON_HOLD)
    private val shop = ShopProfile(name = "Fix It Shop", phone = "555-0100", publicUrl = "https://repairs.example.com", statuses = listOf(awaitingParts))

    private val settings = NotificationSettings(
        emailEnabled = true,
        smtp = SmtpSettings(host = "mail.example.com", username = "shop", password = "mail-secret", fromAddress = "shop@example.com"),
        smsEnabled = true,
        sms = SmsSettings(accountSid = "AC123", authToken = "sms-secret", fromNumber = "+15550000000"),
        events = setOf(NotificationEvent.READY_FOR_PICKUP, NotificationEvent.STATUS_CHANGED, NotificationEvent.PICKUP_REMINDER),
        statusChangeStatuses = listOf(awaitingParts.choice)
    )

    private data class Sent(val channel: NotificationChannel, val to: String, val subject: String, val body: String, val secret: String?)

    private val sent = mutableListOf<Sent>()
    private var answer: SendResult = SendResult.Sent

    private lateinit var adminToken: String
    private lateinit var token: String
    private var clock = System.currentTimeMillis()

    @BeforeTest
    fun fakeSenders() {
        NotificationSenders.email = org.mr0btain.ticketman.components.notify.EmailSender { smtp, to, subject, body ->
            synchronized(sent) { sent += Sent(NotificationChannel.EMAIL, to, subject, body, smtp.password) }
            answer
        }
        NotificationSenders.sms = org.mr0btain.ticketman.components.notify.SmsSender { sms, to, body ->
            synchronized(sent) { sent += Sent(NotificationChannel.SMS, to, "", body, sms.authToken) }
            answer
        }
    }

    @AfterTest
    fun cleanup() {
        NotificationSenders.reset()
        cleanupTestDataDir(dataDir)
    }

    private suspend fun ApplicationTestBuilder.setup(saved: NotificationSettings = settings) {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
            add(viewer)
        }
        adminToken = login(admin)
        token = login(tech)

        assertEquals(HttpStatusCode.Accepted, post("/api/shop/update", json.encodeToString(shop), adminToken).status)
        assertEquals(HttpStatusCode.OK, post("/api/notify/settings", json.encodeToString(saved), adminToken).status)
    }

    /** One with its own clock, starting now, sending through the same database and senders as the server's */
    private fun service() = NotificationService(
        NotifyDatabase(), TicketDatabase(), CustomerDatabase(), InvoiceDatabase(), ShopDatabase(), EstimateDatabase(),
        AuditLogDatabase(UserDatabase()), now = { clock }
    ).also { clock = maxOf(clock, System.currentTimeMillis() + 1) }

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

    private suspend fun ApplicationTestBuilder.ticket(identifier: Uuid): Ticket =
        json.decodeFromString(client.get("/api/data/ticket/get/$identifier") { bearerAuth(token) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.messages(identifier: Uuid, as_: String = token): List<OutboxMessage> =
        json.decodeFromString(client.get("/api/notify/ticket/$identifier") { bearerAuth(as_) }.bodyAsText())

    private suspend fun ApplicationTestBuilder.newTicket(email: Boolean = true, sms: Boolean = false, phone: String = "(555) 123-4567"): Ticket {
        val customer = Customer(name = "Jane Doe", phone = phone, email = "jane@home.test", notifyEmail = email, notifySms = sms)
        assertEquals(HttpStatusCode.Created, post("/api/data/customer/add", json.encodeToString(customer)).status)

        val ticket = Ticket(device = Device("Apple", "iPhone 14"))
        val added = post("/api/data/ticket/add", json.encodeToString(ticket))
        assertEquals(HttpStatusCode.Created, added.status)
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/set/customer/${ticket.identifier}", json.encodeToString(customer.identifier)).status)

        return json.decodeFromString(added.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.setStatus(ticket: Ticket, choice: StatusChoice, time: Long = System.currentTimeMillis()) =
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/update", json.encodeToString(ticket(ticket.identifier).copy(status = choice.toStatus(time)))).status)

    private fun sentNow(): List<Sent> = synchronized(sent) { sent.toList() }

    // ------------------------------------------ Settings ------------------------------------------

    @Test
    fun theSecretsNeverLeaveTheServerAndAreKeptWhenLeftOut() = testApplication {
        setup()

        val read = client.get("/api/notify/settings") { bearerAuth(adminToken) }
        val shown = json.decodeFromString<NotificationSettings>(read.bodyAsText())
        assertFalse("mail-secret" in read.bodyAsText() || "sms-secret" in read.bodyAsText())
        assertTrue(shown.smtpPasswordSet && shown.smsTokenSet)
        assertNull(shown.smtp.password)

        // Saved back as read: the secrets stay
        assertEquals(HttpStatusCode.OK, post("/api/notify/settings", json.encodeToString(shown.copy(smtp = shown.smtp.copy(fromName = "Fix It"))), adminToken).status)
        val test = TestMessageRequest(shown, NotificationChannel.EMAIL, "me@example.com")
        assertEquals(HttpStatusCode.OK, post("/api/notify/test", json.encodeToString(test), adminToken).status)
        assertEquals("mail-secret", sentNow().single().secret)
        assertEquals("Test from Fix It Shop", sentNow().single().subject)

        // An empty one clears it
        post("/api/notify/settings", json.encodeToString(shown.copy(smtp = shown.smtp.copy(password = ""))), adminToken)
        assertFalse(json.decodeFromString<NotificationSettings>(client.get("/api/notify/settings") { bearerAuth(adminToken) }.bodyAsText()).smtpPasswordSet)
    }

    @Test
    fun onlyAdminsSetItAndBadSettingsAreRefused() = testApplication {
        setup()

        assertEquals(HttpStatusCode.Forbidden, client.get("/api/notify/settings") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Forbidden, post("/api/notify/settings", json.encodeToString(settings)).status)

        listOf(
            settings.copy(smtp = settings.smtp.copy(host = "")),
            settings.copy(smtp = settings.smtp.copy(fromAddress = "nope")),
            settings.copy(sms = settings.sms.copy(fromNumber = "555-0000")),
            settings.copy(reminderAfterDays = 0),
            settings.copy(templates = mapOf(NotificationEvent.READY_FOR_PICKUP to MessageTemplate("Hi", " ")))
        ).forEach { bad -> assertEquals(HttpStatusCode.BadRequest, post("/api/notify/settings", json.encodeToString(bad), adminToken).status, bad.toString()) }

        // A test with a send that fails says why
        answer = SendResult.Failed("No such mailbox")
        val failed = post("/api/notify/test", json.encodeToString(TestMessageRequest(settings, NotificationChannel.EMAIL, "me@example.com")), adminToken)
        assertEquals(HttpStatusCode.BadRequest, failed.status)
        assertEquals("No such mailbox", failed.bodyAsText())
    }

    // ------------------------------------------ What's sent when ------------------------------------------

    @Test
    fun readyForPickupGoesToTheChannelsTheCustomerAgreedTo() = testApplication {
        setup()
        val ticket = newTicket(email = true, sms = false)

        setStatus(ticket, StatusChoice(TicketStatusType.DONE))
        service().sendDue()

        val message = messages(ticket.identifier).single()
        assertEquals(NotificationEvent.READY_FOR_PICKUP, message.event)
        assertEquals(NotificationChannel.EMAIL, message.channel)
        assertEquals(OutboxStatus.SENT, message.status)

        val email = sentNow().single()
        assertEquals("jane@home.test", email.to)
        assertEquals("Your Apple iPhone 14 is ready", email.subject)
        assertTrue("Hi Jane," in email.body)
        assertTrue("https://repairs.example.com/status/${ticket.statusToken}" in email.body)
        assertTrue(ticket.label in email.body)

        // Restricted staff can read what was sent
        assertEquals(1, messages(ticket.identifier, login(viewer)).size)
    }

    @Test
    fun statusChangesOnlySendForThePickedStatuses() = testApplication {
        setup()
        val ticket = newTicket(email = false, sms = true)

        setStatus(ticket, StatusChoice(TicketStatusType.ON_HOLD))
        assertTrue(messages(ticket.identifier).isEmpty())

        setStatus(ticket, awaitingParts.choice)
        service().sendDue()

        val text = sentNow().single()
        assertEquals(NotificationChannel.SMS, text.channel)
        assertEquals("+15551234567", text.to)
        assertTrue("is now: Awaiting parts" in text.body)
        assertEquals("sms-secret", text.secret)
    }

    @Test
    fun nothingGoesToCustomersWhoDidntAgreeUnlessStaffSendIt() = testApplication {
        setup()
        val ticket = newTicket(email = false, sms = false)

        setStatus(ticket, StatusChoice(TicketStatusType.DONE))
        assertTrue(messages(ticket.identifier).isEmpty())

        // By hand it goes, to every channel with a contact
        val sentByHand = post("/api/notify/ticket/${ticket.identifier}/send", json.encodeToString(TicketMessageRequest()))
        assertEquals(HttpStatusCode.Created, sentByHand.status)
        val queued = json.decodeFromString<List<OutboxMessage>>(sentByHand.bodyAsText())
        assertEquals(setOf(NotificationChannel.EMAIL, NotificationChannel.SMS), queued.map { it.channel }.toSet())
        assertTrue(queued.all { it.event == NotificationEvent.STATUS_LINK && it.createdBy == tech.username })

        assertEquals(HttpStatusCode.Forbidden, post("/api/notify/ticket/${ticket.identifier}/send", "{}", login(viewer)).status)
        assertEquals(HttpStatusCode.NotFound, post("/api/notify/ticket/${Uuid.random()}/send", "{}").status)
    }

    @Test
    fun turningAChannelOffCallsOffWhatsWaiting() = testApplication {
        setup()
        val ticket = newTicket(email = true)
        setStatus(ticket, StatusChoice(TicketStatusType.DONE))

        assertEquals(HttpStatusCode.OK, post("/api/notify/settings", json.encodeToString(settings.copy(emailEnabled = false)), adminToken).status)
        service().sendDue()

        assertTrue(sentNow().isEmpty())
        assertEquals(OutboxStatus.CANCELLED, messages(ticket.identifier).single().status)

        // And a manual one isn't possible
        assertEquals(HttpStatusCode.BadRequest, post("/api/notify/ticket/${ticket.identifier}/send", json.encodeToString(TicketMessageRequest(channels = setOf(NotificationChannel.EMAIL)))).status)
    }

    // ------------------------------------------ The outbox ------------------------------------------

    @Test
    fun aMessageIsTriedAgainLaterThenGivenUp() = testApplication {
        setup()
        val ticket = newTicket(email = true)
        answer = SendResult.Retry("Connection refused")

        setStatus(ticket, StatusChoice(TicketStatusType.DONE))
        val service = service()
        service.sendDue()

        val waiting = messages(ticket.identifier).single()
        assertEquals(OutboxStatus.PENDING, waiting.status)
        assertEquals(1, waiting.attempts)
        assertEquals("Connection refused", waiting.lastError)
        assertEquals(clock + NotificationService.RETRY_DELAYS[0].inWholeMilliseconds, waiting.nextAttemptAt)

        // Not before its time
        service.sendDue()
        assertEquals(1, messages(ticket.identifier).single().attempts)

        NotificationService.RETRY_DELAYS.forEach { delay ->
            clock += delay.inWholeMilliseconds
            service.sendDue()
        }

        val failed = messages(ticket.identifier).single()
        assertEquals(OutboxStatus.FAILED, failed.status)
        assertEquals(NotificationService.RETRY_DELAYS.size + 1, failed.attempts)
        assertTrue(failed.lastError.startsWith("Gave up"))

        // An admin tries it again once it's fixed
        answer = SendResult.Sent
        assertEquals(HttpStatusCode.OK, post("/api/notify/outbox/${failed.identifier}/retry", "", adminToken).status)
        service.sendDue()
        assertEquals(OutboxStatus.SENT, messages(ticket.identifier).single().status)
        assertEquals(HttpStatusCode.Conflict, post("/api/notify/outbox/${failed.identifier}/retry", "", adminToken).status)
    }

    @Test
    fun aRefusedMessageFailsAtOnceAndWaitingOnesCanBeCancelled() = testApplication {
        setup()
        val ticket = newTicket(email = true)
        answer = SendResult.Failed("No such mailbox")

        setStatus(ticket, StatusChoice(TicketStatusType.DONE))
        service().sendDue()
        assertEquals(OutboxStatus.FAILED, messages(ticket.identifier).single().status)

        val other = newTicket(email = true)
        answer = SendResult.Retry("Down")
        setStatus(other, StatusChoice(TicketStatusType.DONE))
        val waiting = messages(other.identifier).single()

        assertEquals(HttpStatusCode.Forbidden, post("/api/notify/outbox/${waiting.identifier}/cancel", "").status)
        assertEquals(HttpStatusCode.OK, post("/api/notify/outbox/${waiting.identifier}/cancel", "", adminToken).status)
        assertEquals(HttpStatusCode.Conflict, post("/api/notify/outbox/${waiting.identifier}/cancel", "", adminToken).status)

        val outbox = json.decodeFromString<List<OutboxMessage>>(client.get("/api/notify/outbox?offset=0&limit=10") { bearerAuth(adminToken) }.bodyAsText())
        assertEquals(listOf(OutboxStatus.CANCELLED, OutboxStatus.FAILED), outbox.map { it.status })
    }

    // ------------------------------------------ Reminders ------------------------------------------

    @Test
    fun remindersGoAfterTheWaitThenEveryFewDaysUpToTheLimit() = testApplication {
        setup(settings.copy(events = setOf(NotificationEvent.PICKUP_REMINDER), reminderAfterDays = 3, reminderEveryDays = 7, reminderLimit = 2))
        val ticket = newTicket(email = true)
        val readyAt = clock
        setStatus(ticket, StatusChoice(TicketStatusType.DONE), time = readyAt)
        val service = service()

        clock = readyAt + 2.days.inWholeMilliseconds
        assertEquals(0, service.queueReminders())

        clock = readyAt + 3.days.inWholeMilliseconds
        assertEquals(1, service.queueReminders())
        assertEquals(0, service.queueReminders())

        clock += 6.days.inWholeMilliseconds
        assertEquals(0, service.queueReminders())
        clock += 1.days.inWholeMilliseconds + 1.hours.inWholeMilliseconds
        assertEquals(1, service.queueReminders())

        clock += 30.days.inWholeMilliseconds
        assertEquals(0, service.queueReminders())
        assertEquals(2, messages(ticket.identifier).count { it.event == NotificationEvent.PICKUP_REMINDER })
    }
}
