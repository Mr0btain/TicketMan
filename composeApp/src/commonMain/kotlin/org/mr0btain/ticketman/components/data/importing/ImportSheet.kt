package org.mr0btain.ticketman.components.data.importing

import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.money.Money

/** What a CSV can be imported as */
enum class ImportKind(val label: String, private val singular: String, val plural: String) {
    CUSTOMERS("Customers", "customer", "customers"),
    INVENTORY("Inventory", "item", "items");

    /** Like "1 customer" or "12 customers" */
    fun count(n: Int): String = "$n ${if (n == 1) singular else plural}"
}

/**
 * A field records are filled from, with the header names it's matched to when a file is opened
 * @param required A row without it can't be imported
 */
enum class ImportField(
    val kind: ImportKind,
    val label: String,
    private val headers: List<String>,
    val required: Boolean = false
) {
    NAME(ImportKind.CUSTOMERS, "Name", listOf("name", "full name", "customer", "customer name", "contact", "contact name")),
    FIRST_NAME(ImportKind.CUSTOMERS, "First name", listOf("first name", "first", "firstname", "given name")),
    LAST_NAME(ImportKind.CUSTOMERS, "Last name", listOf("last name", "last", "lastname", "surname", "family name")),
    PHONE(ImportKind.CUSTOMERS, "Phone", listOf("phone", "phone number", "telephone", "tel", "mobile", "cell", "cell phone", "mobile phone")),
    EMAIL(ImportKind.CUSTOMERS, "Email", listOf("email", "e-mail", "email address", "mail")),
    STREET(ImportKind.CUSTOMERS, "Street", listOf("street", "address", "address 1", "address1", "address line 1", "street address")),
    CITY(ImportKind.CUSTOMERS, "City", listOf("city", "town")),
    STATE(ImportKind.CUSTOMERS, "State", listOf("state", "province", "region", "county")),
    ZIP(ImportKind.CUSTOMERS, "Zip", listOf("zip", "zip code", "zipcode", "postal code", "postcode", "postal")),

    ITEM_NAME(ImportKind.INVENTORY, "Name", listOf("name", "item", "item name", "product", "product name", "part", "part name", "title"), required = true),
    DESCRIPTION(ImportKind.INVENTORY, "Description", listOf("description", "details", "notes")),
    STOCK(ImportKind.INVENTORY, "Stock", listOf("stock", "qty", "quantity", "on hand", "in stock", "count", "inventory")),
    COST(ImportKind.INVENTORY, "Cost", listOf("cost", "unit cost", "cost price", "buy price", "purchase price")),
    PRICE(ImportKind.INVENTORY, "Price", listOf("price", "retail", "retail price", "sell price", "sale price", "unit price", "msrp"));

    fun matches(header: String): Boolean = normalize(header) in headers

    companion object {
        fun of(kind: ImportKind): List<ImportField> = entries.filter { it.kind == kind }

        private fun normalize(header: String) = header.trim().lowercase().replace('_', ' ').replace(Regex("\\s+"), " ")
    }
}

/**
 * One row of the file made into a record, or why it couldn't be
 * @param line The row's line in the file, from 1, counting the header
 */
data class ImportRow<T>(
    val line: Int,
    val record: T?,
    val problem: String? = null
)

/**
 * A CSV opened for import: its headers, its rows, and which column fills which field
 */
class ImportSheet(
    val kind: ImportKind,
    val headers: List<String>,
    val rows: List<List<String>>
) {
    /** Which column each field comes from, guessed from the headers. Null for a field the file doesn't have */
    val mapping: Map<ImportField, Int?> = ImportField.of(kind).associateWith { field -> headers.indexOfFirst { field.matches(it) }.takeIf { it >= 0 } }

    /** The fields with a column for them, after [changes] made by hand */
    fun mapped(changes: Map<ImportField, Int?>): Map<ImportField, Int?> = mapping + changes

    /** Every row as a customer, with the name from a Name column or from First and Last name */
    fun customers(mapping: Map<ImportField, Int?>): List<ImportRow<Customer>> =
        rows.mapIndexed { index, cells ->
            fun cell(field: ImportField) = mapping[field]?.let { cells.getOrNull(it) }?.trim().orEmpty()

            val name = cell(ImportField.NAME).ifBlank { listOf(cell(ImportField.FIRST_NAME), cell(ImportField.LAST_NAME)).filter { it.isNotBlank() }.joinToString(" ") }
            val customer = Customer(
                name = name,
                phone = cell(ImportField.PHONE),
                email = cell(ImportField.EMAIL),
                address = Address(cell(ImportField.STREET), cell(ImportField.CITY), cell(ImportField.STATE), cell(ImportField.ZIP))
            )

            if (name.isBlank()) ImportRow(index + 2, null, "No name") else ImportRow(index + 2, customer)
        }

    /** Every row as an inventory item. A blank stock is an item that isn't counted */
    fun inventory(mapping: Map<ImportField, Int?>): List<ImportRow<Inventory>> =
        rows.mapIndexed { index, cells ->
            fun cell(field: ImportField) = mapping[field]?.let { cells.getOrNull(it) }?.trim().orEmpty()

            val name = cell(ImportField.ITEM_NAME)
            val stockText = cell(ImportField.STOCK)
            val stock = if (stockText.isBlank()) Int.MAX_VALUE else stockText.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt()
            val cost = cell(ImportField.COST).let { if (it.isBlank()) 0L else Money.parse(it) }
            val price = cell(ImportField.PRICE).let { if (it.isBlank()) 0L else Money.parse(it) }

            val problem = when {
                name.isBlank() -> "No name"
                stock == null -> "Stock \"$stockText\" isn't a whole number"
                cost == null -> "Cost \"${cell(ImportField.COST)}\" isn't an amount"
                price == null -> "Price \"${cell(ImportField.PRICE)}\" isn't an amount"
                else -> null
            }

            if (problem != null) ImportRow(index + 2, null, problem)
            else ImportRow(index + 2, Inventory(name = name, description = cell(ImportField.DESCRIPTION), stock = stock!!, costCents = cost!!, priceCents = price!!))
        }

    companion object {
        /**
         * Reads a CSV with a header row
         * @return Null when there's nothing in it but the headers
         */
        fun read(kind: ImportKind, text: String): ImportSheet? {
            val all = parseCsv(text)
            if (all.size < 2) return null

            val width = all.maxOf { it.size }
            val headers = all.first().let { first -> List(width) { first.getOrNull(it)?.trim().orEmpty().ifBlank { "Column ${it + 1}" } } }
            return ImportSheet(kind, headers, all.drop(1))
        }
    }
}
