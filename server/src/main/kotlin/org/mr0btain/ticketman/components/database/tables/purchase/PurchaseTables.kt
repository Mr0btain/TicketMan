package org.mr0btain.ticketman.components.database.tables.purchase

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.purchase.PurchaseLine

object SupplierTable : UuidTable("suppliers") {
    val name = text("name")
    val contactName = text("contact_name").default("")
    val phone = text("phone").default("")
    val email = text("email").default("")
    val website = text("website").default("")
    val accountNumber = text("account_number").default("")
    val notes = text("notes").default("")
}

/**
 * Orders to suppliers, numbered per year like invoices. [supplier] isn't a foreign key, so deleting a supplier
 * leaves its orders with the name they had. Lines are kept whole as JSON, changed only under the purchase service's
 * lock
 */
object PurchaseOrderTable : UuidTable("purchase_orders") {
    val numberYear = integer("number_year")
    val numberSequence = long("number_sequence")
    val supplier = uuid("supplier_id").nullable()
    val supplierName = text("supplier_name").default("")
    val status = text("status")
    val lines = json<List<PurchaseLine>>("lines", Json { ignoreUnknownKeys = true })
    val note = text("note").default("")
    val createdAt = long("created_at")
    val createdBy = text("created_by").default("")
    val orderedAt = long("ordered_at").nullable()
    val receivedAt = long("received_at").nullable()
    val expectedAt = long("expected_at").nullable()

    // Added with locations: where it's delivered, and the code its number starts with
    val numberPrefix = text("number_prefix").default("")

    // Added with locations: the location it belongs to. Null only until LocalDatabase gives older rows the main one
    val location = uuid("location_id").nullable().index()

    init {
        // Older databases have it on (year, sequence) alone until LocalDatabase's number-prefix fix
        uniqueIndex(NUMBER_INDEX, numberPrefix, numberYear, numberSequence)
    }

    const val NUMBER_INDEX = "purchase_orders_number_unique"
}
