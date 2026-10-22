package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.database.tables.DataFixTable
import org.mr0btain.ticketman.components.database.tables.invoice.InvoiceTable
import org.mr0btain.ticketman.components.database.tables.ticket.TicketTable
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.InvoiceChanges
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*

/** An invoice with money against it, or a void one, outlives its ticket until an admin deletes it */
class KeptInvoiceTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)

    private lateinit var token: String
    private lateinit var adminToken: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.start(addUsers: Boolean = true) {
        configureTestServer(dataDir)
        startApplication()
        if (addUsers) UserDatabase().apply {
            add(admin)
            add(tech)
        }
        token = login(tech)
        adminToken = login(admin)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String =
        json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest(user.username, user.password)))
            }.bodyAsText()
        ).accessToken!!

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, as_: String = token) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(as_)
            setBody(body)
        }

    /** A ticket with an invoice for $40, and [paid] taken as a deposit */
    private suspend fun ApplicationTestBuilder.invoicedTicket(paid: Long = 0): Pair<Ticket, Invoice> {
        val ticket = json.decodeFromString<Ticket>(post("/api/data/ticket/add", json.encodeToString(Ticket())).bodyAsText())
        val opened = json.decodeFromString<Invoice>(post("/api/invoice/ticket/${ticket.identifier}", "").bodyAsText())
        val changes = InvoiceChanges.between(emptyList(), 0, listOf(Charge(description = "Repair", unitPriceCents = 4_000)), 0)
        var invoice = json.decodeFromString<Invoice>(post("/api/invoice/${opened.identifier}/update", json.encodeToString(changes)).bodyAsText())
        if (paid > 0) {
            invoice = json.decodeFromString(post("/api/invoice/${invoice.identifier}/payments", json.encodeToString(PaymentRequest(PaymentMethod.CASH, paid))).bodyAsText())
        }
        return ticket to invoice
    }

    private suspend fun ApplicationTestBuilder.deleteTicket(ticket: Ticket) =
        assertEquals(HttpStatusCode.Accepted, client.delete("/api/data/ticket/remove/${ticket.identifier}") { bearerAuth(token) }.status)

    private suspend fun ApplicationTestBuilder.invoice(invoice: Invoice): HttpResponse =
        client.get("/api/invoice/${invoice.identifier}") { bearerAuth(token) }

    @Test
    fun anInvoiceWithADepositOutlivesItsTicket() = testApplication {
        start()
        val (ticket, invoice) = invoicedTicket(paid = 1_000)
        deleteTicket(ticket)

        val kept = json.decodeFromString<Invoice>(invoice(invoice).bodyAsText())
        assertEquals(ticket.label, kept.ticketLabel)
        assertNotNull(kept.ticketRemovedAt)
        assertEquals(1_000, kept.paidCents)

        // Still printable, and still in the books
        assertEquals(HttpStatusCode.OK, client.get("/api/invoice/${invoice.identifier}/pdf") { bearerAuth(token) }.status)
        val csv = client.get("/api/invoice/export?from=2000-01-01&to=2999-12-31") { bearerAuth(adminToken) }.bodyAsText()
        assertContains(csv, ticket.label)

        val audit = client.get("/api/admin/audit/fetch?offset=0&limit=20") { bearerAuth(adminToken) }.bodyAsText()
        assertContains(audit, "Invoice ${invoice.label} kept, it has payments or was voided")
    }

    @Test
    fun aVoidInvoiceIsKeptAndAnUnpaidOneIsNot() = testApplication {
        start()
        val (voidTicket, voided) = invoicedTicket()
        assertEquals(HttpStatusCode.OK, post("/api/invoice/${voided.identifier}/void", "", adminToken).status)
        val (openTicket, open) = invoicedTicket()

        deleteTicket(voidTicket)
        deleteTicket(openTicket)

        assertEquals(InvoiceStatus.VOID, json.decodeFromString<Invoice>(invoice(voided).bodyAsText()).status)
        assertEquals(HttpStatusCode.NotFound, invoice(open).status)
    }

    @Test
    fun onlyAnAdminDeletesAKeptInvoice() = testApplication {
        start()
        val (ticket, invoice) = invoicedTicket(paid = 4_000)

        // Not while it has its ticket, it goes with the ticket
        assertEquals(HttpStatusCode.Conflict, client.delete("/api/invoice/${invoice.identifier}") { bearerAuth(adminToken) }.status)

        deleteTicket(ticket)
        assertEquals(HttpStatusCode.Forbidden, client.delete("/api/invoice/${invoice.identifier}") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.Accepted, client.delete("/api/invoice/${invoice.identifier}") { bearerAuth(adminToken) }.status)
        assertEquals(HttpStatusCode.NotFound, invoice(invoice).status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/invoice/${invoice.identifier}") { bearerAuth(adminToken) }.status)
    }

    @Test
    fun anOldDatabasesCascadeIsDropped() {
        // A database from before, where deleting a ticket deleted its invoice through a foreign key
        testApplication {
            start()
            transaction {
                exec(
                    "ALTER TABLE ${InvoiceTable.tableName} ADD CONSTRAINT fk_invoices_ticket_id__id FOREIGN KEY " +
                        "(${InvoiceTable.ticket.name}) REFERENCES ${TicketTable.tableName}(id) ON DELETE CASCADE"
                )
                DataFixTable.deleteWhere { DataFixTable.name eq "invoice-ticket-key" }
            }
        }
        try {
            transaction { exec("SHUTDOWN") }
        } catch (_: Exception) {
            // Committing after SHUTDOWN fails, the database is closed either way
        }

        testApplication {
            start(addUsers = false)
            val (ticket, invoice) = invoicedTicket(paid = 1_000)
            deleteTicket(ticket)
            assertEquals(HttpStatusCode.OK, invoice(invoice).status)
        }
    }
}
