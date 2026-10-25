package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.data.payment.CardChargeKind
import org.mr0btain.ticketman.data.payment.PaymentOptions
import org.mr0btain.ticketman.data.payment.PaymentProvider
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.request.PaymentLinkRequest
import org.mr0btain.ticketman.server.request.ReaderChargeRequest
import kotlin.test.*
import kotlin.uuid.Uuid

/**
 * Card payment client calls hit the routes the server has, and a refused charge reads as the server's reason
 */
class PaymentRemoteRepositoryTest {
    private val requests = mutableListOf<String>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val charge = CardCharge(kind = CardChargeKind.READER, provider = PaymentProvider.STRIPE, amountCents = 1_000, currency = "USD")

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}"

        when (request.url.encodedPath) {
            "/api/payment/options" -> respond(Json.encodeToString(PaymentOptions(enabled = true, links = true)), HttpStatusCode.OK, jsonHeaders)
            "/api/payment/charge/reader" -> respond("Front counter is waiting for another card ($5.00), cancel that one first", HttpStatusCode.Conflict)
            "/api/payment/charge/link" -> respond(Json.encodeToString(charge.copy(kind = CardChargeKind.LINK)), HttpStatusCode.Created, jsonHeaders)
            "/api/payment/charges" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
            else -> respond(Json.encodeToString(charge), HttpStatusCode.OK, jsonHeaders)
        }
    }

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private val payments = PaymentRemoteRepository(client).apply { serverUrl = "http://localhost" }

    @Test
    fun paymentRoutes() = runTest {
        assertTrue((payments.options() as TmResult.Success).data.links)
        assertEquals(CardChargeKind.LINK, (payments.link(PaymentLinkRequest(Uuid.random())) as TmResult.Success).data.kind)
        payments.charge(charge.identifier)
        payments.cancel(charge.identifier)
        payments.refund(charge.identifier, "Sale not finished")
        payments.charges(0, 50)

        assertEquals(
            listOf(
                "GET /api/payment/options",
                "POST /api/payment/charge/link",
                "GET /api/payment/charge/${charge.identifier}",
                "POST /api/payment/charge/${charge.identifier}/cancel",
                "POST /api/payment/charge/${charge.identifier}/refund",
                "GET /api/payment/charges"
            ),
            requests
        )
    }

    @Test
    fun aBusyReaderSaysWhy() = runTest {
        val result = payments.startReader(ReaderChargeRequest("tmr_1", 1_000))
        assertIs<TmResult.Error<*>>(result)
        assertEquals(NetworkError.CONFLICT, (result as TmResult.Error).error.type)
        assertContains(result.error.message, "waiting for another card")
    }

    @Test
    fun noServerSkipsTheRequest() = runTest {
        val unset = PaymentRemoteRepository(client)
        assertIs<TmResult.Error<*>>(unset.options())
        assertTrue(requests.isEmpty())
    }
}
