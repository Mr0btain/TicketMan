package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.StatusService
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.image.ImageLimits
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.CounterSearchResponse
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The customer status page: its link, what it shows and keeps back, the lookup form and its limits
 */
@OptIn(ExperimentalUuidApi::class)
class StatusPageTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val viewer = User(username = "viewer", password = "viewer_pass", accessLevel = AccessLevel.RESTRICTED)

    private val quietStatus = CustomStatus(name = "Bench test", actsAs = TicketStatusType.IN_PROGRESS, showCustomer = false)
    private val sharedStatus = CustomStatus(name = "Awaiting parts", actsAs = TicketStatusType.ON_HOLD, tone = StatusTone.ACCENT)

    private val shop = ShopProfile(
        name = "Fix It Shop", phone = "555-0100", email = "help@fixit.test", street = "1 Main St", city = "Springfield",
        publicUrl = "https://repairs.example.com", statuses = listOf(quietStatus, sharedStatus)
    )

    private lateinit var token: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
            add(viewer)
        }
        token = login(tech)
        assertEquals(HttpStatusCode.Accepted, post("/api/shop/update", json.encodeToString(shop), login(admin)).status)
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

    private suspend fun ApplicationTestBuilder.ticket(identifier: Uuid): Ticket =
        json.decodeFromString(client.get("/api/data/ticket/get/$identifier") { bearerAuth(token) }.bodyAsText())

    /** A ticket for Jane Doe, phone ending 4567 */
    private suspend fun ApplicationTestBuilder.newTicket(): Ticket {
        val customer = Customer(name = "Jane Q Doe", phone = "(555) 123-4567", email = "jane@home.test")
        assertEquals(HttpStatusCode.Created, post("/api/data/customer/add", json.encodeToString(customer)).status)

        val sent = Ticket(device = Device("Apple", "iPhone 14", "SN-SECRET-1"), problem = "Cracked screen", estimateCents = 15000, statusToken = "f".repeat(32))
        val added = post("/api/data/ticket/add", json.encodeToString(sent))
        assertEquals(HttpStatusCode.Created, added.status)
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/set/customer/${sent.identifier}", json.encodeToString(customer.identifier)).status)

        return json.decodeFromString(added.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.page(token: String?, forwardedFor: String = "203.0.113.1") =
        client.get("/status/$token") { header("X-Forwarded-For", forwardedFor) }

    private suspend fun ApplicationTestBuilder.lookup(number: String, phone: String, forwardedFor: String = "203.0.113.1") =
        createClient { followRedirects = false }.submitForm(
            url = "/status",
            formParameters = parameters {
                append("number", number)
                append("phone", phone)
            }
        ) { header("X-Forwarded-For", forwardedFor) }

    private suspend fun ApplicationTestBuilder.addNote(ticket: Ticket, message: String, customerVisible: Boolean, images: List<StoredImage> = emptyList()) =
        assertEquals(
            HttpStatusCode.Created,
            post("/api/data/ticket/add/note/${ticket.identifier}", json.encodeToString(Note(message = message, customerVisible = customerVisible, images = images))).status
        )

    private suspend fun ApplicationTestBuilder.uploadImage(): StoredImage {
        val png = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        val response = client.submitFormWithBinaryData(
            url = "/api/image/upload",
            formData = formData {
                append(ImageLimits.UPLOAD_FIELD, png, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"photo.png\"")
                    append(HttpHeaders.ContentType, "image/png")
                })
            }
        ) { bearerAuth(token) }

        assertEquals(HttpStatusCode.Created, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    // ------------------------------------------ The link ------------------------------------------

    @Test
    fun everyTicketGetsItsOwnTokenThatStaffCanReplace() = testApplication {
        setup()

        val first = newTicket()
        val second = newTicket()
        val token = first.statusToken!!

        // The server's, never the one sent
        assertTrue(StatusLink.isToken(token))
        assertNotEquals("f".repeat(32), token)
        assertNotEquals(token, second.statusToken)
        assertEquals("https://repairs.example.com/status/$token", StatusLink.url(shop.publicUrl, first))

        // An update can't set it either
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/update", json.encodeToString(ticket(first.identifier).copy(statusToken = "0".repeat(32)))).status)
        assertEquals(token, ticket(first.identifier).statusToken)

        // Only staff who can change things replace it, and the old link stops working
        assertEquals(HttpStatusCode.Forbidden, post("/api/data/ticket/status-token/${first.identifier}", "", login(viewer)).status)
        val replaced = post("/api/data/ticket/status-token/${first.identifier}", "")
        assertEquals(HttpStatusCode.OK, replaced.status)
        val newToken = json.decodeFromString<Ticket>(replaced.bodyAsText()).statusToken!!

        assertNotEquals(token, newToken)
        assertEquals(newToken, ticket(first.identifier).statusToken)
        assertEquals(HttpStatusCode.NotFound, page(token).status)
        assertEquals(HttpStatusCode.OK, page(newToken).status)
        assertEquals(HttpStatusCode.NotFound, post("/api/data/ticket/status-token/${Uuid.random()}", "").status)
    }

    @Test
    fun scanningTheLinkAtTheCounterFindsItsTicket() = testApplication {
        setup()
        newTicket()
        val ticket = newTicket()

        val found = client.get("/api/counter/search") {
            bearerAuth(token)
            parameter("query", StatusLink.url(shop.publicUrl, ticket)!!.uppercase())
            parameter("limit", 10)
        }

        assertEquals(listOf(ticket.identifier), json.decodeFromString<CounterSearchResponse>(found.bodyAsText()).tickets.map { it.ticket.identifier })
    }

    // ------------------------------------------ The page ------------------------------------------

    @Test
    fun thePageShowsWhatTheCustomerMaySeeAndNothingElse() = testApplication {
        setup()
        val ticket = newTicket()

        addNote(ticket, "Passcode is 1234, back glass also cracked", customerVisible = false)
        addNote(ticket, "Your new screen <b>arrives</b> Tuesday", customerVisible = true)

        // Into a status the shop keeps to itself, then one it shares
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/update", json.encodeToString(ticket(ticket.identifier).copy(status = quietStatus.choice.toStatus()))).status)
        assertEquals(HttpStatusCode.Accepted, post("/api/data/ticket/update", json.encodeToString(ticket(ticket.identifier).copy(status = sharedStatus.choice.toStatus()))).status)

        val response = page(ticket.statusToken)
        val html = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Text.Html))
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("no-referrer", response.headers["Referrer-Policy"])
        assertTrue(response.headers["Content-Security-Policy"]!!.startsWith("default-src 'none'"))
        assertNotNull(response.headers["X-Robots-Tag"])

        listOf(ticket.label, "Apple iPhone 14", "Hi Jane.", "Awaiting parts", "In progress", "$150.00", "Fix It Shop", "555-0100", "help@fixit.test", "1 Main St")
            .forEach { assertTrue(it in html, "page should show $it") }

        // Escaped, not markup
        assertTrue("Your new screen &lt;b&gt;arrives&lt;/b&gt; Tuesday" in html)

        // The hidden status reads as the one it acts as, which was already the last one, so it's not there twice
        assertFalse("Bench test" in html)
        assertEquals(1, Regex(">In progress<").findAll(html).count())

        listOf("Passcode", "back glass", "Doe", "123-4567", "jane@home.test", "SN-SECRET-1", "Cracked screen", tech.username)
            .forEach { assertFalse(it in html, "page shouldn't show $it") }
    }

    @Test
    fun theBalanceComesFromTheInvoice() = testApplication {
        setup()
        val ticket = newTicket()

        // An invoice made for the ticket, with a part and half a payment
        val invoice = post("/api/invoice/ticket/${ticket.identifier}", "")
        assertEquals(HttpStatusCode.OK, invoice.status)
        val invoiceId = json.decodeFromString<org.mr0btain.ticketman.data.invoice.Invoice>(invoice.bodyAsText()).identifier

        val lines = """{"added":[{"identifier":"${Uuid.generateV7()}","type":"LABOR","description":"Screen swap","quantity":1.0,"unitPriceCents":10000,"taxable":false}],"discountWas":0}"""
        assertEquals(HttpStatusCode.OK, post("/api/invoice/$invoiceId/update", lines).status)
        assertEquals(HttpStatusCode.OK, post("/api/invoice/$invoiceId/payments", """{"method":"CASH","amountCents":4000}""").status)

        val html = page(ticket.statusToken).bodyAsText()
        listOf("Total so far", "$100.00", "Paid", "$40.00", "Balance due", "$60.00").forEach { assertTrue(it in html, "page should show $it") }
        assertFalse("Screen swap" in html)
    }

    @Test
    fun onlyPhotosOnSharedUpdatesAreServed() = testApplication {
        setup()
        val ticket = newTicket()
        val shared = uploadImage()
        val internal = uploadImage()

        addNote(ticket, "Here's the damage", customerVisible = true, images = listOf(shared))
        addNote(ticket, "Board shot", customerVisible = false, images = listOf(internal))

        val base = "/status/${ticket.statusToken}/photo"
        val html = page(ticket.statusToken).bodyAsText()
        assertTrue("${ticket.statusToken}/photo/${shared.identifier}/thumb" in html)
        assertFalse(internal.identifier.toString() in html)

        val thumb = client.get("$base/${shared.identifier}/thumb")
        assertEquals(HttpStatusCode.OK, thumb.status)
        assertTrue(thumb.headers[HttpHeaders.CacheControl]!!.startsWith("private"))
        assertEquals(HttpStatusCode.OK, client.get("$base/${shared.identifier}/display").status)

        // Not the original, not one on an internal note, not through another ticket's link
        assertEquals(HttpStatusCode.NotFound, client.get("$base/${shared.identifier}/original").status)
        assertEquals(HttpStatusCode.NotFound, client.get("$base/${internal.identifier}/thumb").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/status/${newTicket().statusToken}/photo/${shared.identifier}/thumb").status)
    }

    @Test
    fun badLinksAreNotFoundAndThenLimited() = testApplication {
        setup()
        val ticket = newTicket()

        assertEquals(HttpStatusCode.NotFound, page("not-a-token").status)
        repeat(StatusService.BAD_LINKS_PER_ADDRESS - 1) { assertEquals(HttpStatusCode.NotFound, page("0".repeat(32)).status) }

        // That device is held off, even with a good link; another isn't
        assertEquals(HttpStatusCode.TooManyRequests, page(ticket.statusToken).status)
        assertEquals(HttpStatusCode.OK, page(ticket.statusToken, forwardedFor = "203.0.113.2").status)
    }

    // ------------------------------------------ Looking it up ------------------------------------------

    @Test
    fun theFormFindsATicketByNumberAndPhone() = testApplication {
        setup()
        val ticket = newTicket()

        val form = client.get("/status")
        assertEquals(HttpStatusCode.OK, form.status)
        assertTrue("""name="number"""" in form.bodyAsText())

        // The short form of the number works, and the redirect is relative
        val short = "${ticket.number!!.year % 100}-${ticket.number!!.sequence}"
        val found = lookup(short, "4567")
        assertEquals(HttpStatusCode.SeeOther, found.status)
        assertEquals("status/${ticket.statusToken}", found.headers[HttpHeaders.Location])

        assertEquals(HttpStatusCode.NotFound, lookup(ticket.label, "4568").status)
        assertEquals(HttpStatusCode.NotFound, lookup(ticket.label, "").status)
        assertEquals(HttpStatusCode.NotFound, lookup("99-1", "4567").status)
        assertTrue("find a repair with that number" in lookup("nonsense", "4567").bodyAsText())
    }

    @Test
    fun wrongLookupsAreLimitedPerDeviceAndPerTicket() = testApplication {
        setup()
        val ticket = newTicket()
        val other = newTicket()

        // One device guessing runs out
        repeat(StatusService.LOOKUPS_PER_ADDRESS) { assertEquals(HttpStatusCode.NotFound, lookup("99-${it + 1}", "0000", forwardedFor = "198.51.100.1").status) }
        assertEquals(HttpStatusCode.TooManyRequests, lookup(ticket.label, "4567", forwardedFor = "198.51.100.1").status)

        // Many devices guessing one ticket run it out, for everyone, until the day is up; other tickets still work
        repeat(StatusService.LOOKUPS_PER_NUMBER) { index ->
            assertEquals(HttpStatusCode.NotFound, lookup(ticket.label, "%04d".format(index), forwardedFor = "198.51.100.${index + 10}").status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, lookup(ticket.label, "4567", forwardedFor = "198.51.100.200").status)
        assertEquals(HttpStatusCode.SeeOther, lookup(other.label, "4567", forwardedFor = "198.51.100.200").status)
    }

    @Test
    fun ticketsFromBeforeStatusLinksGetOneOnStart() = testApplication {
        setup()
        val ticket = newTicket()

        // Like a database from before the column, then a restart's data fixes
        org.jetbrains.exposed.v1.jdbc.transactions.transaction {
            exec("UPDATE tickets SET status_token = NULL")
        }
        assertNull(ticket(ticket.identifier).statusToken)

        org.mr0btain.ticketman.components.database.TicketDatabase().backfillStatusTokens()
        assertTrue(StatusLink.isToken(ticket(ticket.identifier).statusToken!!))
    }
}
