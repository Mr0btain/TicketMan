package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.NetworkError
import kotlin.test.*
import kotlin.uuid.Uuid

/**
 * The invoice PDF and CSV export come back as bytes, with the server's reason when they're refused
 */
class InvoiceFilesTest {
    private val requests = mutableListOf<String>()
    private val pdfBytes = "%PDF-1.7 not really".encodeToByteArray()

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}${request.url.encodedQuery.let { if (it.isBlank()) "" else "?$it" }}"

        when {
            request.url.encodedPath.endsWith("/pdf") -> respond(pdfBytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/pdf"))
            request.url.parameters["from"] == "2026-10-01" -> respond("Invoice,Date\r\n", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/csv"))
            else -> respond("Give from and to as days like 2026-10-01, from first", HttpStatusCode.BadRequest)
        }
    }

    private val invoices = InvoiceRemoteRepository(
        HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    ).apply { serverUrl = "http://localhost" }

    @Test
    fun thePdfIsTheBodyAsItCame() = runTest {
        val id = Uuid.random()
        val pdf = invoices.pdf(id)

        assertContentEquals(pdfBytes, (pdf as TmResult.Success).data)
        assertEquals(listOf("GET /api/invoice/$id/pdf"), requests)
    }

    @Test
    fun theExportAsksForItsDays() = runTest {
        val csv = invoices.export("2026-10-01", "2026-10-31")

        assertEquals("Invoice,Date\r\n", (csv as TmResult.Success).data.decodeToString())
        assertEquals(listOf("GET /api/invoice/export?from=2026-10-01&to=2026-10-31"), requests)
    }

    @Test
    fun aRefusedExportSaysWhy() = runTest {
        val refused = invoices.export("someday", "2026-10-31")

        val error = (refused as TmResult.Error).error
        assertEquals(NetworkError.BAD_REQUEST, error.type)
        assertEquals("Give from and to as days like 2026-10-01, from first", error.message)
    }
}
