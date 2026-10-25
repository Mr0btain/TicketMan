package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.RefundRequest
import org.mr0btain.ticketman.server.request.SaleRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import java.time.LocalDate
import kotlin.test.*

/**
 * The QuickBooks, Xero and payments exports
 */
class AccountingExportTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val jane = Customer(name = "Jane Doe", email = "jane@example.com")
    private val food = TaxClass("food", "Food", "2")
    private val today = LocalDate.now()

    private lateinit var token: String
    private lateinit var adminToken: String

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup(accounting: AccountingSettings = AccountingSettings(dateFormat = ExportDateFormat.ISO)) {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
        }
        token = login(tech)
        adminToken = login(admin)
        post("/api/data/customer/add", json.encodeToString(jane))
        val shop = ShopProfile(name = "Fix It", taxRate = "10", taxClasses = listOf(food), accounting = accounting)
        assertEquals(HttpStatusCode.Accepted, post("/api/shop/update", json.encodeToString(shop), adminToken).status)
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String =
        json.decodeFromString<AuthResponse>(client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }.bodyAsText()).accessToken!!

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, as_: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(as_ ?: token)
            setBody(body)
        }

    /** $20 of labor (taxed at 10%), a $5 snack (Food, 2%) and a $5 untaxed kit, $2 off, to Jane */
    private suspend fun ApplicationTestBuilder.sale(): Invoice {
        val charges = listOf(
            Charge(type = ChargeType.LABOR, description = "Screen fitting", quantity = 1.0, unitPriceCents = 2_000),
            Charge(type = ChargeType.OTHER, description = "Snack", quantity = 1.0, unitPriceCents = 500, taxClass = "food"),
            Charge(type = ChargeType.OTHER, description = "Kit", quantity = 1.0, unitPriceCents = 500, taxable = false)
        )
        val quote = json.decodeFromString<Invoice>(
            post("/api/counter/sale", json.encodeToString(SaleRequest(jane.identifier, charges, 200, listOf(PaymentRequest(PaymentMethod.CARD, 100_000))))).bodyAsText()
        )
        assertEquals(InvoiceStatus.PAID, quote.status)
        return quote
    }

    private suspend fun ApplicationTestBuilder.export(format: String, as_: String? = null): Pair<HttpStatusCode, List<String>> {
        val response = client.get("/api/invoice/export?from=$today&to=$today&format=$format") { bearerAuth(as_ ?: adminToken) }
        return response.status to response.bodyAsText().trim().split("\r\n")
    }

    @Test
    fun quickBooksGetsALinePerChargeAndTheDiscount() = testApplication {
        setup()
        val invoice = sale()
        val (status, rows) = export("quickbooks")
        assertEquals(HttpStatusCode.OK, status)

        assertEquals("InvoiceNo,Customer,InvoiceDate,DueDate,Memo,Item(Product/Service),ItemDescription,ItemQuantity,ItemRate,ItemAmount,Taxable,TaxRate", rows[0])
        assertEquals(
            listOf(
                "${invoice.label},Jane Doe,$today,$today,Counter sale,Labor,Screen fitting,1,20.00,20.00,Y,10%",
                "${invoice.label},Jane Doe,$today,$today,Counter sale,Other,Snack,1,5.00,5.00,Y,2%",
                "${invoice.label},Jane Doe,$today,$today,Counter sale,Other,Kit,1,5.00,5.00,N,",
                "${invoice.label},Jane Doe,$today,$today,Counter sale,Discount,Discount,1,-2.00,-2.00,Y,10%"
            ),
            rows.drop(1)
        )
    }

    @Test
    fun xeroGetsAccountsAndTaxTypesFromTheShop() = testApplication {
        setup(AccountingSettings(salesAccount = "4000", taxType = "GST on Income", exemptTaxType = "No GST", classTaxTypes = mapOf("food" to "Food Tax"), dueDays = 14, dateFormat = ExportDateFormat.ISO))
        val invoice = sale()
        val (_, rows) = export("xero")

        assertEquals("*ContactName,EmailAddress,*InvoiceNumber,Reference,*InvoiceDate,*DueDate,*Description,*Quantity,*UnitAmount,*AccountCode,*TaxType", rows[0])
        val due = today.plusDays(14)
        assertEquals("Jane Doe,jane@example.com,${invoice.label},Counter sale,$today,$due,Screen fitting,1,20.00,4000,GST on Income", rows[1])
        assertEquals("Jane Doe,jane@example.com,${invoice.label},Counter sale,$today,$due,Snack,1,5.00,4000,Food Tax", rows[2])
        assertTrue(rows[3].endsWith(",Kit,1,5.00,4000,No GST"))
        assertTrue(rows[4].endsWith(",Discount,1,-2.00,4000,GST on Income"))
    }

    @Test
    fun paymentsListRefundsAsNegative() = testApplication {
        setup()
        val invoice = sale()
        assertEquals(HttpStatusCode.OK, post("/api/invoice/${invoice.identifier}/refund", json.encodeToString(RefundRequest(PaymentMethod.CARD, 500, "Kit unused")), adminToken).status)

        val (_, rows) = export("payments")
        assertEquals("Date,Invoice,Customer,Method,Amount,Reference,Note,Received by", rows[0])
        assertEquals(2, rows.size - 1)
        assertTrue(rows[1].startsWith("$today,${invoice.label},Jane Doe,Card,"))
        assertTrue(rows[2].startsWith("$today,${invoice.label},Jane Doe,Card,-5.00,,Kit unused,boss"), rows[2])
    }

    @Test
    fun theFormatAndTheSettingsAreChecked() = testApplication {
        setup(AccountingSettings(dateFormat = ExportDateFormat.MONTH_FIRST))
        sale()
        val us = today.let { "%02d/%02d/%d".format(it.monthValue, it.dayOfMonth, it.year) }
        assertTrue(export("quickbooks").second[1].contains(",$us,$us,"), "dates as the shop picked")

        assertEquals(HttpStatusCode.BadRequest, export("sage").first)
        assertEquals(HttpStatusCode.Forbidden, export("xero", token).first)
        assertEquals("Invoice,Date,Status,Paid on,Ticket,Customer,Subtotal,Discount,Tax,Total,Paid,Balance,Refunded", export("invoices").second[0])

        val bad = ShopProfile(accounting = AccountingSettings(salesAccount = " "))
        assertEquals(HttpStatusCode.BadRequest, post("/api/shop/update", json.encodeToString(bad), adminToken).status)
    }
}
