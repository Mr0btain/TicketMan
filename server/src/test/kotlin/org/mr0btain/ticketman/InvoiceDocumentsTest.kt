package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.service.InvoiceDocuments
import org.mr0btain.ticketman.data.audit.AuditEntry
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.InvoiceUpdateRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import java.io.File
import java.time.LocalDate
import kotlin.test.*

/**
 * Invoices as files: the PDF and the CSV export
 */
class InvoiceDocumentsTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)

    private lateinit var adminToken: String
    private lateinit var clerkToken: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(clerk)
        }
        adminToken = login(admin)
        clerkToken = login(clerk)
        post("/api/shop/update", json.encodeToString(ShopProfile(name = "Fix It Shop", taxRate = "8.25", invoiceFooter = "Thanks!")))
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String =
        json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest(user.username, user.password)))
            }.bodyAsText()
        ).accessToken!!

    private suspend fun ApplicationTestBuilder.post(path: String, body: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(adminToken)
            setBody(body)
        }

    // A ticket for a customer, with an invoice holding a part and some labor
    private suspend fun ApplicationTestBuilder.invoicedTicket(customerName: String): Invoice {
        val ticket = json.decodeFromString<Ticket>(post("/api/data/ticket/add", json.encodeToString(Ticket(device = Device("Apple", "iPhone 14")))).bodyAsText())
        val customer = Customer(name = customerName, phone = "5550142", email = "jane@example.com")
        post("/api/data/customer/add", json.encodeToString(customer))
        post("/api/data/customer/add/ticket/${customer.identifier}", json.encodeToString(ticket.identifier))

        val invoice = json.decodeFromString<Invoice>(post("/api/invoice/ticket/${ticket.identifier}", "").bodyAsText())
        val charges = listOf(
            Charge(type = ChargeType.OTHER, description = "Screen", unitPriceCents = 12_000),
            Charge(type = ChargeType.LABOR, description = "Install", quantity = 0.5, unitPriceCents = 6_000, taxable = false)
        )
        return json.decodeFromString(post("/api/invoice/${invoice.identifier}/update", json.encodeToString(InvoiceUpdateRequest(charges, 0))).bodyAsText())
    }

    @Test
    fun anInvoiceComesBackAsAPdfWithItsDetails() = testApplication {
        setup()
        val invoice = invoicedTicket("Jane Doe")

        val response = client.get("/api/invoice/${invoice.identifier}/pdf") { bearerAuth(clerkToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType()?.withoutParameters())
        assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().contains("${invoice.label}.pdf"))

        val bytes = response.readRawBytes()
        // Kept to look at, like the client's screen renders
        File("build/invoice-pdf").apply { mkdirs() }.resolve("invoice.pdf").writeBytes(bytes)

        Loader.loadPDF(bytes).use { pdf ->
            assertEquals("Invoice ${invoice.label}", pdf.documentInformation.title)
            val text = PDFTextStripper().getText(pdf)
            listOf("Fix It Shop", "Invoice", invoice.label, "Jane Doe", "jane@example.com", "Screen", "Total", "$159.90", "Still owed", "Thanks!")
                .forEach { assertTrue(it in text, "PDF is missing \"$it\":\n$text") }
        }
    }

    @Test
    fun aMissingInvoiceHasNoPdf() = testApplication {
        setup()

        assertEquals(HttpStatusCode.NotFound, client.get("/api/invoice/${Ticket().identifier}/pdf") { bearerAuth(clerkToken) }.status)
    }

    @Test
    fun theExportHasARowPerInvoiceInTheRange() = testApplication {
        setup()
        val first = invoicedTicket("Jane Doe")
        val second = invoicedTicket("=Smith, Bob")
        val today = LocalDate.now()

        val response = client.get("/api/invoice/export?from=$today&to=$today") { bearerAuth(adminToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().contains("invoices-$today-to-$today.csv"))
        val lines = response.bodyAsText().trimEnd().split("\r\n")
        assertEquals(InvoiceDocuments.CSV_HEADER.joinToString(","), lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].startsWith("${first.label},$today,open,,"), lines[1])
        assertTrue(lines[1].endsWith(",Jane Doe,150.00,0.00,9.90,159.90,0.00,159.90"), lines[1])
        // Typed text that looks like a formula isn't run as one, and the comma is quoted
        assertTrue(lines[2].startsWith(second.label) && lines[2].contains(",\"'=Smith, Bob\","), lines[2])

        val audit = json.decodeFromString<List<AuditEntry>>(client.get("/api/admin/audit/fetch?offset=0&limit=20") { bearerAuth(adminToken) }.bodyAsText())
        assertTrue(audit.any { it.entityType == "invoices" && "Exported 2 invoices" in it.details })

        val tomorrow = today.plusDays(1)
        val empty = client.get("/api/invoice/export?from=$tomorrow&to=$tomorrow") { bearerAuth(adminToken) }.bodyAsText()
        assertEquals(1, empty.trimEnd().split("\r\n").size)
    }

    @Test
    fun theExportIsForAdminsWithARange() = testApplication {
        setup()
        val today = LocalDate.now()

        assertEquals(HttpStatusCode.Forbidden, client.get("/api/invoice/export?from=$today&to=$today") { bearerAuth(clerkToken) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/invoice/export?from=$today") { bearerAuth(adminToken) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/invoice/export?from=$today&to=${today.minusDays(1)}") { bearerAuth(adminToken) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/invoice/export?from=yesterday&to=$today") { bearerAuth(adminToken) }.status)
    }

    @Test
    fun csvCellsAreQuotedAndKeptFromBeingFormulas() {
        assertEquals("plain", InvoiceDocuments.csvCell("plain"))
        assertEquals("\"a, b\"", InvoiceDocuments.csvCell("a, b"))
        assertEquals("\"say \"\"hi\"\"\"", InvoiceDocuments.csvCell("say \"hi\""))
        assertEquals("'=SUM(A1)", InvoiceDocuments.csvCell("=SUM(A1)"))
        assertEquals("'@here", InvoiceDocuments.csvCell("@here"))
        // A negative amount is a number, not a formula
        assertEquals("-12.50", InvoiceDocuments.csvCell("-12.50"))
    }
}
