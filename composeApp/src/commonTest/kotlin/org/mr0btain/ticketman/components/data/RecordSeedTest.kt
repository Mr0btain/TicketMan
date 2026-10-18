package org.mr0btain.ticketman.components.data

import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.components.data.user.UserSeed
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import kotlin.test.*
import kotlin.uuid.Uuid

/** The record seeds: loading a record leaves them clean, edits mark what changed, and the record comes back out */
class RecordSeedTest {
    private val note = Note(author = "Sam", message = "Screen ordered")

    // ------------------------------------------ Ticket ------------------------------------------

    private val ticket = Ticket(device = Device("Apple", "iPhone 14"), problem = "Cracked screen", estimateCents = 12_000)
    private val customerRef = EntityReference(Uuid.random(), "Jane Doe")

    @Test
    fun aLoadedTicketIsCleanAndComesBackOut() {
        val seed = TicketSeed().also { it.setTicket(ticket, listOf(note), customerRef) }

        assertFalse(seed.isDirty)
        assertFalse(seed.isNew)
        assertEquals(ticket, seed.ticket)
        assertEquals(customerRef, seed.customer)
        assertEquals(listOf(note), seed.notes)
    }

    @Test
    fun ticketEditsAreTracked() {
        val seed = TicketSeed().also { it.setTicket(ticket) }

        seed.changeProblem("Won't charge")
        seed.changeEstimate(null)
        seed.changeCustomer(customerRef)

        assertEquals(setOf(ChangedField.PROBLEM, ChangedField.ESTIMATE, ChangedField.CUSTOMER), seed.getChangedFields().toSet())
        assertEquals(ticket.copy(problem = "Won't charge", estimateCents = null), seed.ticket)
    }

    @Test
    fun changingATicketBackIsUnchanged() {
        val seed = TicketSeed().also { it.setTicket(ticket) }

        seed.changeProblem("Won't charge")
        seed.changeEstimate(null)
        seed.changeProblem(ticket.problem)

        // Only the field still different counts
        assertEquals(listOf(ChangedField.ESTIMATE), seed.getChangedFields())

        seed.changeEstimate(ticket.estimateCents)
        assertFalse(seed.isDirty)
    }

    @Test
    fun savingTakesTheCurrentFieldsAsClean() {
        val seed = TicketSeed().also { it.setTicket(ticket) }
        seed.changeProblem("Won't charge")

        seed.markClean()
        assertFalse(seed.isDirty)

        // Going back to the old value is now a change
        seed.changeProblem(ticket.problem)
        assertEquals(listOf(ChangedField.PROBLEM), seed.getChangedFields())
    }

    @Test
    fun aNoteAddedThenDeletedIsUnchanged() {
        val seed = TicketSeed().also { it.setTicket(ticket, listOf(note)) }
        val added = Note(message = "Typo")

        seed.saveNote(added)
        seed.deleteNote(added.identifier)

        assertFalse(seed.isDirty)
    }

    @Test
    fun settingATicketFieldToWhatItIsIsNotAChange() {
        val seed = TicketSeed().also { it.setTicket(ticket) }

        seed.changeProblem(ticket.problem)
        seed.changeDevice(ticket.device)
        seed.changeEstimate(ticket.estimateCents)

        assertFalse(seed.isDirty)
    }

    @Test
    fun clearingATicketStartsANewOne() {
        val seed = TicketSeed().also { it.setTicket(ticket, listOf(note), customerRef) }
        seed.changeProblem("Half typed")

        seed.clear()

        assertTrue(seed.isNew)
        assertFalse(seed.isDirty)
        assertNotEquals(ticket.identifier, seed.identifier)
        assertNull(seed.customer)
        assertTrue(seed.notes.isEmpty())
        assertEquals("", seed.problem)
    }

    // ------------------------------------------ Notes, shared by every seed ------------------------------------------

    @Test
    fun addingAndDeletingNotesMarksThemChanged() {
        val seed = TicketSeed().also { it.setTicket(ticket, listOf(note)) }
        val added = Note(message = "Customer called")

        seed.saveNote(added)
        assertEquals(listOf(note, added), seed.notes)
        assertTrue(ChangedField.NOTE in seed.getChangedFields())

        seed.markClean()
        seed.deleteNote(note.identifier)
        assertEquals(listOf(added), seed.notes)
        assertTrue(ChangedField.NOTE in seed.getChangedFields())
    }

    @Test
    fun deletingANoteThatIsntThereChangesNothing() {
        val seed = TicketSeed().also { it.setTicket(ticket, listOf(note)) }

        seed.deleteNote(Uuid.random())

        assertFalse(seed.isDirty)
        assertEquals(listOf(note), seed.notes)
    }

    @Test
    fun recordPhotosAreKeptOncePerIdentifier() {
        val seed = InventorySeed()
        val photo = StoredImage(width = 40, height = 30)

        seed.addImage(photo)
        seed.addImage(photo.copy(description = "Same photo"))
        assertEquals(listOf(photo), seed.images)

        seed.removeImage(photo.identifier)
        assertTrue(seed.images.isEmpty())
        assertTrue(ChangedField.IMAGE in seed.getChangedFields())
    }

    // ------------------------------------------ Customer ------------------------------------------

    private val customer = Customer(
        name = "Jane:Doe",
        address = Address("1 Main St", "Springfield", "IL", "62701"),
        email = "jane@example.com",
        phone = "5550100"
    )

    @Test
    fun aLoadedCustomerIsCleanAndComesBackOut() {
        val seed = CustomerSeed().also { it.setCustomer(customer, listOf(note), listOf(ticket)) }

        assertFalse(seed.isDirty)
        assertEquals(customer, seed.customer)
        assertEquals("Jane", seed.firstName)
        assertEquals("Doe", seed.lastName)
        assertEquals(listOf(ticket), seed.tickets)
    }

    @Test
    fun customerContactFieldsAreCleanedAsTheyreTyped() {
        val seed = CustomerSeed()

        seed.changePhone("(555) 010-0199")
        seed.changeZip("62701-")
        seed.changeEmail(" jane @example.com ")

        assertEquals("5550100199", seed.phone)
        assertEquals("62701", seed.zip)
        assertEquals("jane@example.com", seed.email)
    }

    @Test
    fun aCustomersTicketsAreAddedOnceAndRemovedById() {
        val seed = CustomerSeed().also { it.setCustomer(customer) }

        seed.addTicket(ticket)
        seed.addTicket(ticket.copy(problem = "Same ticket"))
        assertEquals(listOf(ticket), seed.tickets)
        assertEquals(listOf(ChangedField.TICKET), seed.getChangedFields())

        seed.deleteTicket(Uuid.random())
        assertEquals(listOf(ticket), seed.tickets)

        // Back to no tickets, as it was loaded
        seed.deleteTicket(ticket.identifier)
        assertTrue(seed.tickets.isEmpty())
        assertFalse(seed.isDirty)
    }

    @Test
    fun aOnePartNameHasNoLastName() {
        val customerSeed = CustomerSeed().also { it.setCustomer(customer.copy(name = "Jane")) }
        val userSeed = UserSeed().also { it.setUser(user.copy(name = "Jane"), emptyList()) }

        assertEquals("Jane", customerSeed.firstName)
        assertEquals("", customerSeed.lastName)
        assertEquals("Jane", userSeed.firstName)
        assertEquals("", userSeed.lastName)
    }

    @Test
    fun aClearedCustomerHasNoName() {
        val seed = CustomerSeed().also { it.setCustomer(customer) }

        seed.clear()

        assertEquals("", seed.firstName)
        assertEquals("", seed.lastName)
        assertTrue(seed.isNew)
    }

    // ------------------------------------------ Inventory ------------------------------------------

    private val part = Inventory(name = "iPhone 14 Screen", description = "OEM", stock = 5, priceCents = 12_000, costCents = 6_050)

    @Test
    fun aLoadedItemIsCleanAndItsPricesSurviveDollars() {
        val photo = StoredImage(width = 40, height = 30)
        val seed = InventorySeed().also { it.setInitial(part, listOf(note), listOf(photo)) }

        assertFalse(seed.isDirty)
        assertEquals(part, seed.inventory)
        assertEquals(120.0, seed.price)
        assertEquals(60.5, seed.cost)
        assertEquals(listOf(photo), seed.images)
    }

    @Test
    fun itemEditsAreTracked() {
        val seed = InventorySeed().also { it.setInitial(part) }

        seed.changeStock(4)
        seed.changePrice(119.99)
        seed.changeName(part.name)

        assertEquals(setOf(ChangedField.STOCK, ChangedField.PRICE), seed.getChangedFields().toSet())
        assertEquals(11_999, seed.inventory.priceCents)
    }

    // ------------------------------------------ User ------------------------------------------

    private val user = User(username = "jdoe", name = "Jane:Doe", email = "jane@example.com", phone = "5550100", accessLevel = AccessLevel.NORMAL)

    @Test
    fun aLoadedUserIsCleanAndComesBackOut() {
        val seed = UserSeed().also { it.setUser(user, listOf(note)) }

        assertFalse(seed.isDirty)
        assertEquals(user, seed.user)
        assertEquals("Jane", seed.firstName)
        assertEquals("Doe", seed.lastName)
    }

    @Test
    fun userEditsAreTracked() {
        val seed = UserSeed().also { it.setUser(user, emptyList()) }

        seed.changeAccessLevel(AccessLevel.ADMIN)
        seed.changeName(emptyList())
        seed.changeName(listOf("Jane", "Smith"))

        assertEquals(setOf(ChangedField.ACCESS_LEVEL, ChangedField.NAME), seed.getChangedFields().toSet())
        assertEquals("Jane:Smith", seed.user.name)
    }

    @Test
    fun aClearedUserHasNoName() {
        val seed = UserSeed().also { it.setUser(user, emptyList()) }

        seed.clear()

        assertEquals("", seed.firstName)
        assertEquals("", seed.lastName)
        assertTrue(seed.isNew)
        assertEquals(AccessLevel.RESTRICTED, seed.accessLevel)
    }
}
