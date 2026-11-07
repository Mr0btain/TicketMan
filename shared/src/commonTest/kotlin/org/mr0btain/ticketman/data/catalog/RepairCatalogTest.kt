package org.mr0btain.ticketman.data.catalog

import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.ticket.Device
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RepairCatalogTest {
    private val screen = RepairOffer(name = "Screen", priceCents = 18_900, inventoryIdentifier = Uuid.random(), warrantyDays = 90)
    private val battery = RepairOffer(name = "Battery", priceCents = 7_900)
    private val pro = CatalogModel(manufacturer = "Apple", model = "iPhone 14 Pro", repairs = listOf(screen, battery))
    private val plain = CatalogModel(manufacturer = "Apple", model = "iPhone 14")
    private val pixel = CatalogModel(manufacturer = "Google", model = "Pixel 8")
    private val menu = listOf(pro, plain, pixel)

    @Test
    fun theSameModelComesFirstThenOnesHoldingWhatWasTyped() {
        assertEquals(listOf(plain, pro), RepairCatalog.matches(menu, Device("apple", "iPhone 14")))
        assertEquals(listOf(pro), RepairCatalog.matches(menu, Device("", "14 pro")))
        assertEquals(listOf(pixel), RepairCatalog.matches(menu, Device("Google", "")))
        assertEquals(emptyList(), RepairCatalog.matches(menu, Device("", "")))
        assertEquals(emptyList(), RepairCatalog.matches(menu, Device("Samsung", "Galaxy")))
    }

    @Test
    fun aModelOnTheMenuTwiceOrRepairsNamedTwiceAreRefused() {
        assertNull(RepairCatalog.problem(pro, menu))
        assertNotNull(RepairCatalog.problem(CatalogModel(manufacturer = " apple ", model = "IPHONE 14  pro"), menu))
        assertNotNull(RepairCatalog.problem(pro.copy(repairs = listOf(screen, battery.copy(name = " screen"))), menu))
        assertNotNull(RepairCatalog.problem(pro.copy(repairs = listOf(screen.copy(priceCents = -1))), menu))
        assertNotNull(RepairCatalog.problem(pro.copy(model = ""), menu))
    }

    @Test
    fun aRepairWithAPartIsThatPartsLineOtherwiseLabor() {
        val picked = RepairCatalog.find(menu, listOf(screen.identifier, battery.identifier))!!
        val lines = picked.map { RepairCatalog.charge(it) }

        assertEquals(listOf(ChargeType.PART, ChargeType.LABOR), lines.map { it.type })
        assertEquals("Screen (Apple iPhone 14 Pro)", lines.first().description)
        assertEquals(screen.inventoryIdentifier, lines.first().inventoryIdentifier)
        assertEquals(26_800, RepairCatalog.totalCents(picked))
        assertEquals("Screen, Battery", RepairCatalog.problemText(picked))
        assertNull(RepairCatalog.find(menu, listOf(Uuid.random())))
    }
}
