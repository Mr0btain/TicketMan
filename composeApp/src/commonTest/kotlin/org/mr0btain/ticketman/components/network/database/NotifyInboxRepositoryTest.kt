package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.data.notify.InboundMessage
import org.mr0btain.ticketman.data.notify.InboxResponse
import org.mr0btain.ticketman.data.notify.NotificationChannel
import org.mr0btain.ticketman.data.notify.NotificationEvent
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.request.TicketMessageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The counter's texts from customers: the inbox, a ticket's texts, marking them read and replying */
@OptIn(ExperimentalUuidApi::class)
class NotifyInboxRepositoryTest {
    private val requests = mutableListOf<String>()
    private val bodies = mutableListOf<String>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val ticket = Uuid.random()
    private val text = InboundMessage(ticketIdentifier = ticket, sender = "+15551234567", body = "Is it ready?", receivedAt = 1)

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}" + (request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
        (request.body as? io.ktor.http.content.TextContent)?.let { bodies += it.text }

        when (request.url.encodedPath) {
            "/api/notify/inbox" -> respond(Json.encodeToString(InboxResponse(1, listOf(text))), HttpStatusCode.OK, jsonHeaders)
            "/api/notify/ticket/$ticket/received" -> respond(Json.encodeToString(listOf(text)), HttpStatusCode.OK, jsonHeaders)
            "/api/notify/ticket/$ticket/send" -> respond("[]", HttpStatusCode.Created, jsonHeaders)
            else -> respond("", HttpStatusCode.Accepted)
        }
    }

    private val notify = NotifyRemoteRepository(HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }).apply { serverUrl = "http://localhost" }

    @Test
    fun theInboxAndATicketsTextsAreRead() = runTest {
        assertEquals(TmResult.Success(InboxResponse(1, listOf(text))), notify.inbox(30))
        assertEquals(TmResult.Success(listOf(text)), notify.received(ticket))
        assertEquals(listOf("GET /api/notify/inbox?limit=30", "GET /api/notify/ticket/$ticket/received"), requests)
    }

    @Test
    fun textsAreMarkedReadByTicketOrOneAtATime() = runTest {
        assertIs<TmResult.Success<*>>(notify.markTicketRead(ticket))
        assertIs<TmResult.Success<*>>(notify.markRead(text.identifier))
        assertEquals(listOf("POST /api/notify/ticket/$ticket/read", "POST /api/notify/inbox/${text.identifier}/read"), requests)
    }

    @Test
    fun aReplyIsATextInTheReplyTemplate() = runTest {
        assertIs<TmResult.Success<*>>(notify.reply(ticket, "Ready at 3pm"))
        assertEquals(TicketMessageRequest(NotificationEvent.REPLY, setOf(NotificationChannel.SMS), "Ready at 3pm"), Json.decodeFromString(bodies.single()))
    }
}
