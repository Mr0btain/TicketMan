package org.mr0btain.ticketman.components.network.user

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import kotlin.test.*

class ServerUserClientTest {
    private val store = InMemoryKeyValueStore()
    private val localStorage = LocalStorage(store)
    private val requests = mutableListOf<String>()

    private val engine = MockEngine { request ->
        requests += request.url.encodedPath

        when (request.url.encodedPath) {
            "/api/auth" -> respond(
                content = """{"id":null,"accessToken":"access","refreshToken":"refresh"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )

            else -> respond("", HttpStatusCode.OK)
        }
    }

    private val client = ServerUserClient(createUserHttpClient(engine), localStorage).apply {
        changeServerUrl("http://localhost")
    }

    @Test
    fun loginWithBlankCredentialsSkipsRequest() = runTest {
        assertFalse(client.login("", ""))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun loginStoresTokensInMatchingFields() = runTest {
        assertTrue(client.login("user", "pass"))

        assertEquals("access", localStorage.accessToken)
        assertEquals("refresh", localStorage.refreshToken)
    }

    @Test
    fun loginSavesCredentialsWhenRemembered() = runTest {
        localStorage.rememberLogin = true

        assertTrue(client.login("user", "pass"))

        assertEquals("user", localStorage.username)
        assertEquals("pass", localStorage.password)
    }

    @Test
    fun loginDoesNotSaveCredentialsWhenNotRemembered() = runTest {
        localStorage.rememberLogin = false

        assertTrue(client.login("user", "pass"))

        assertEquals("", localStorage.username)
        assertEquals("", localStorage.password)
        assertFalse(store.values.containsKey("username"))
        assertFalse(store.values.containsKey("password"))
    }
}
