package org.mr0btain.ticketman.ui.screens.setup

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import org.mr0btain.ticketman.ui.screens.setup.components.SetupHandler
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class SetupHandlerTest {
    private var serverProfile = ShopProfile()
    private val requests = mutableListOf<String>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private val engine = MockEngine { request ->
        requests += "${request.method.value} ${request.url.encodedPath}"
        when (request.url.encodedPath) {
            SERVER_PING_PATH -> respond(SERVER_PING_BODY, HttpStatusCode.OK)
            "/api/shop" -> respond(Json.encodeToString(serverProfile), HttpStatusCode.OK, jsonHeaders)
            "/api/shop/update" -> {
                serverProfile = Json.decodeFromString(request.body.toByteArray().decodeToString())
                respond("", HttpStatusCode.Accepted)
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }

    private suspend fun TestScope.settle(done: () -> Boolean) {
        repeat(300) {
            advanceUntilIdle()
            if (done()) return
            withContext(Dispatchers.Default) { delay(10.milliseconds) }
        }
    }

    private suspend fun TestScope.handler(admin: Boolean, onSaved: (ShopProfile) -> Unit = {}): SetupHandler {
        val remote = RemoteHandler(engine, LocalStorage(InMemoryKeyValueStore()))
        remote.setUrl("http://localhost")
        requests.clear()
        return SetupHandler(remote, this, isAdmin = { admin }, onShopChanged = onSaved)
    }

    @Test
    fun anAdminOnANewShopIsWalkedThroughItAndFinishingMarksItDone() = runTest {
        var saved: ShopProfile? = null
        val setup = handler(admin = true) { saved = it }

        setup.check()
        settle { setup.profile.value != null }
        assertTrue(setup.needed)

        var result: String? = "not yet"
        setup.finish(ShopProfile(name = "Fix It Shop")) { result = it }
        settle { result != "not yet" }

        assertNull(result)
        assertTrue(serverProfile.setupDone)
        assertEquals("Fix It Shop", saved?.name)
        assertFalse(setup.needed)
    }

    @Test
    fun skippingMarksItDoneAsItIs() = runTest {
        val setup = handler(admin = true)
        setup.check()
        settle { setup.profile.value != null }

        var result: String? = "not yet"
        setup.skip { result = it }
        settle { result != "not yet" }

        assertTrue(serverProfile.setupDone)
        assertEquals("", serverProfile.name)
    }

    @Test
    fun nobodyButAnAdminAndNoShopAlreadyNamedSeesIt() = runTest {
        val staff = handler(admin = false)
        staff.check()
        advanceUntilIdle()
        assertFalse(staff.needed)
        assertEquals(emptyList(), requests)

        serverProfile = ShopProfile(name = "Been here a while")
        val admin = handler(admin = true)
        admin.check()
        settle { "GET /api/shop" in requests }
        settle { false }
        assertFalse(admin.needed)
    }
}
