package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.data.report.CommissionRate
import org.mr0btain.ticketman.data.report.PeriodReport
import org.mr0btain.ticketman.data.report.SalesSummary
import org.mr0btain.ticketman.error.TmResult
import kotlin.test.*
import kotlin.uuid.Uuid

/**
 * Reports, appointments and the time clock: client calls hit the routes the server has
 */
class BusinessRemoteRepositoryTest {
    private val requests = mutableListOf<String>()
    private val bodies = mutableListOf<String>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}" + (request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
        (request.body as? io.ktor.http.content.TextContent)?.let { bodies += it.text }

        when (request.url.encodedPath) {
            "/api/report/period" -> respond(Json.encodeToString(PeriodReport("2026-10-01", "2026-10-31", SalesSummary(invoices = 2))), HttpStatusCode.OK, jsonHeaders)
            "/api/report/commissions" ->
                if (request.method == HttpMethod.Post) respond("Commission has to be from 0% to 100%", HttpStatusCode.BadRequest)
                else respond("[]", HttpStatusCode.OK, jsonHeaders)
            else -> respond("[]", HttpStatusCode.OK, jsonHeaders)
        }
    }

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private val reports = ReportRemoteRepository(client).apply { serverUrl = "http://localhost" }

    @Test
    fun reportRoutes() = runTest {
        val report = reports.period("2026-10-01", "2026-10-31")
        assertEquals(2, (report as TmResult.Success).data.sales.invoices)
        assertIs<TmResult.Success<*>>(reports.commissions())

        val saved = reports.setCommissions(listOf(CommissionRate(Uuid.random(), 20_000)))
        assertEquals("Commission has to be from 0% to 100%", (saved as TmResult.Error).error.message)

        assertEquals(
            listOf("GET /api/report/period?from=2026-10-01&to=2026-10-31", "GET /api/report/commissions", "POST /api/report/commissions"),
            requests
        )
        assertTrue(bodies.single().startsWith("[{\"userIdentifier\""), "rates go as a list")
    }
}
