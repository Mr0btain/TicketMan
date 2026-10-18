package org.mr0btain.ticketman.components.network.database

import io.ktor.util.reflect.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.user.User
import kotlin.test.*

/**
 * Ktor's json converter looks up a serializer from the TypeInfo it's given. These check that the type info
 * itemTypes() makes for each repository resolves to a working serializer, which an erased T can't.
 */
class RemoteRepositoryTypeTest {
    // Pulls the private type info out the same way Ktor resolves it
    @Suppress("UNCHECKED_CAST")
    private fun <T> serializerFor(type: TypeInfo): KSerializer<T> = serializer(type.kotlinType!!) as KSerializer<T>

    private inline fun <reified T : MainItem> assertListRoundTrips(items: List<T>) {
        val types = itemTypes<T>()

        val list = Json.encodeToString(items)
        assertEquals(items, Json.decodeFromString(serializerFor<List<T>>(types.list), list))

        val item = Json.encodeToString(items.first())
        assertEquals(items.first(), Json.decodeFromString(serializerFor<T>(types.item), item))
    }

    @Test
    fun listTypesDecode() {
        assertListRoundTrips(listOf(Customer(name = "a"), Customer(name = "b")))
        // Fixed times, the defaults are Clock.now() and get re-made on decode since defaults aren't encoded
        assertListRoundTrips(listOf(Ticket(status = TicketStatus(time = 1), priority = TicketPriority(time = 1))))
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
