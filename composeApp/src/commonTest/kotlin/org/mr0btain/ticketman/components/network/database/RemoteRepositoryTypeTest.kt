package org.mr0btain.ticketman.components.network.database

import io.ktor.util.reflect.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.User
import kotlin.test.*

/**
 * Ktor's json converter looks up a serializer from the TypeInfo it's given. These check that the type info each
 * repository hands to RemoteRepository resolves to a working serializer, which an erased T can't.
 */
class RemoteRepositoryTypeTest {
    // Pulls the private type info out the same way Ktor resolves it
    @Suppress("UNCHECKED_CAST")
    private fun <T> serializerFor(type: TypeInfo): KSerializer<T> = serializer(type.kotlinType!!) as KSerializer<T>

    private inline fun <reified T : MainItem> assertListRoundTrips(items: List<T>) {
        val json = Json.encodeToString(items)
        val decoded = Json.decodeFromString(serializerFor<List<T>>(typeInfo<List<T>>()), json)
        assertEquals(items, decoded)
    }

    @Test
    fun listTypesDecode() {
        assertListRoundTrips(listOf(Customer(name = "a"), Customer(name = "b")))
        assertListRoundTrips(listOf(Ticket()))
        assertListRoundTrips(listOf(Inventory(name = "part")))
        assertListRoundTrips(listOf(User(username = "user")))
    }

    @Test
    fun erasedTypeParameterCannotResolveSerializer() {
        // What fetch() was doing before: T comes from the class so it's erased
        fun <T> erasedListType() = typeInfo<List<T>>()

        assertFailsWith<IllegalArgumentException> { serializer(erasedListType<Customer>().kotlinType!!) }
    }
}
