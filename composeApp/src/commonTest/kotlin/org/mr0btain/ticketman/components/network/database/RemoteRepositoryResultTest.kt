package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.NetworkError
import kotlin.test.*
import kotlin.uuid.Uuid

/**
 * Note and link calls say whether the server took them, like add/update/remove do
 */
class RemoteRepositoryResultTest {
    private var status = HttpStatusCode.Accepted

    private val client = HttpClient(MockEngine { respond("", status) }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private val customers = CustomerRemoteRepository(client).apply { serverUrl = "http://localhost" }

    @Test
    fun noteAndLinkCallsSucceedWhenTheServerTakesThem() = runTest {
        assertIs<TmResult.Success<Unit>>(customers.addNote(Uuid.random(), Note(message = "hi")))
        assertIs<TmResult.Success<Unit>>(customers.updateNote(Note(message = "hi")))
        assertIs<TmResult.Success<Unit>>(customers.removeNote(Uuid.random()))
        assertIs<TmResult.Success<Unit>>(customers.addTicket(Uuid.random(), Uuid.random()))
    }

    @Test
    fun noteAndLinkCallsReportNotFound() = runTest {
        status = HttpStatusCode.NotFound

        listOf(
            customers.addNote(Uuid.random(), Note(message = "hi")),
            customers.updateNote(Note(message = "hi")),
            customers.removeNote(Uuid.random()),
            customers.addTicket(Uuid.random(), Uuid.random())
        ).forEach { result ->
            assertEquals(NetworkError.NOT_FOUND, assertIs<TmResult.Error<ServerError>>(result).error.type)
        }
    }

    @Test
    fun noServerIsAnError() = runTest {
        val unset = CustomerRemoteRepository(client)

        assertIs<TmResult.Error<*>>(unset.addNote(Uuid.random(), Note(message = "hi")))
        assertIs<TmResult.Error<*>>(unset.removeNote(Uuid.random()))
    }
}
