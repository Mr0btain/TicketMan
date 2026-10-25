package org.mr0btain.ticketman.components.data.importing

import kotlin.test.*

class ImportSheetTest {
    @Test
    fun csvQuotesSeparatorsAndLineBreaks() {
        val rows = parseCsv("﻿Name,Notes\r\n\"Doe, Jane\",\"Said \"\"hi\"\"\nthen left\"\r\n\r\nBob,\n")

        assertEquals(listOf(listOf("Name", "Notes"), listOf("Doe, Jane", "Said \"hi\"\nthen left"), listOf("Bob", "")), rows)
    }

    @Test
    fun semicolonsAndTabsAreFoundFromTheHeader() {
        assertEquals(listOf(listOf("Name", "Price"), listOf("Cable", "12,50")), parseCsv("Name;Price\nCable;12,50"))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), parseCsv("a\tb\n1\t2"))
    }

    @Test
    fun customerColumnsAreMatchedByHeader() {
        val sheet = ImportSheet.read(ImportKind.CUSTOMERS, "First Name,Last Name,Mobile,E-mail,Address,Town,Postcode,Notes\nJane,Doe,555-0142,jane@example.com,1 Main St,Springfield,12345,VIP")!!

        assertEquals(0, sheet.mapping[ImportField.FIRST_NAME])
        assertEquals(1, sheet.mapping[ImportField.LAST_NAME])
        assertNull(sheet.mapping[ImportField.NAME])
        assertEquals(2, sheet.mapping[ImportField.PHONE])
        assertEquals(6, sheet.mapping[ImportField.ZIP])

        val jane = sheet.customers(sheet.mapping).single()
        assertEquals(2, jane.line)
        assertEquals("Jane Doe", jane.record?.name)
        assertEquals("jane@example.com", jane.record?.email)
        assertEquals("Springfield", jane.record?.address?.city)
    }

    @Test
    fun aNameColumnWinsAndAColumnCanBeLeftOut() {
        val sheet = ImportSheet.read(ImportKind.CUSTOMERS, "Customer,First,Phone\nJane Doe,J,5550142\n,,5550199")!!
        val mapping = sheet.mapped(mapOf(ImportField.PHONE to null))

        val rows = sheet.customers(mapping)
        assertEquals("Jane Doe", rows[0].record?.name)
        assertEquals("", rows[0].record?.phone)
        assertEquals("No name", rows[1].problem)
    }

    @Test
    fun inventoryAmountsAndStock() {
        val sheet = ImportSheet.read(ImportKind.INVENTORY, "Product,Qty,Cost,Retail Price\nCable,10,\$3.00,12.5\nScreen,,60,120\nCase,two,1,2\nCharger,1,1,abc")!!

        val rows = sheet.inventory(sheet.mapping)
        assertEquals(10, rows[0].record?.stock)
        assertEquals(1250, rows[0].record?.priceCents)
        assertEquals(300, rows[0].record?.costCents)
        assertEquals(Int.MAX_VALUE, rows[1].record?.stock)
        assertEquals("Stock \"two\" isn't a whole number", rows[2].problem)
        assertEquals("Price \"abc\" isn't an amount", rows[3].problem)
    }

    @Test
    fun inventoryCodesCategoryAndReorderPoint() {
        val sheet = ImportSheet.read(ImportKind.INVENTORY, "Name,SKU,UPC,Category,Reorder Level\nCable,CBL-1,0123,Cables,4\nScreen,,,,lots")!!

        val rows = sheet.inventory(sheet.mapping)
        assertEquals("CBL-1", rows[0].record?.sku)
        assertEquals("0123", rows[0].record?.barcode)
        assertEquals("Cables", rows[0].record?.category)
        assertEquals(4, rows[0].record?.reorderPoint)
        assertEquals("Reorder point \"lots\" isn't a whole number", rows[1].problem)
    }

    @Test
    fun aFileWithOnlyHeadersIsNothingToImport() {
        assertNull(ImportSheet.read(ImportKind.INVENTORY, "Name,Price\n"))
    }
}
