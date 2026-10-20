package org.mr0btain.ticketman.components.service

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.response.ImportOutcome
import org.mr0btain.ticketman.server.response.ImportResult
import org.mr0btain.ticketman.server.response.ImportRowResult
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Adds customers or inventory from another system, read from a CSV by the client. Records already here are skipped:
 * customers with the same phone number or email, items with the same name. A dry run says what would happen, which
 * is the client's preview, so the preview and the import can't disagree
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("PrivatePropertyName")
class ImportService(
    private val customers: CustomerDatabase,
    private val inventory: InventoryDatabase,
    private val auditLog: AuditLogDatabase
) {
    private val TAG = "ImportService"

    /**
     * @return Null when the existing customers couldn't be read
     */
    fun customers(list: List<Customer>, dryRun: Boolean, actor: Uuid?): ImportResult? {
        val existing = customers.contacts() ?: return null
        // Phone numbers are compared by their digits, so "(555) 014-2000" matches "5550142000"
        val byPhone = existing.filter { phoneKey(it.second) != null }.associateBy({ phoneKey(it.second)!! }, { it.first })
        val byEmail = existing.filter { it.third.isNotBlank() }.associateBy({ it.third.trim().lowercase() }, { it.first })
        val seenPhones = mutableMapOf<String, Int>()
        val seenEmails = mutableMapOf<String, Int>()

        val rows = list.mapIndexed { index, customer ->
            val phone = phoneKey(customer.phone)
            val email = customer.email.trim().lowercase().takeIf { it.isNotEmpty() }

            val outcome = when {
                customer.name.isBlank() -> error(index, "No name")
                email != null && !email.contains('@') -> error(index, "\"${customer.email}\" isn't an email address")
                phone != null && byPhone[phone] != null -> match(index, "${byPhone[phone]} has that phone number")
                email != null && byEmail[email] != null -> match(index, "${byEmail[email]} has that email")
                phone != null && seenPhones[phone] != null -> match(index, "Same phone number as row ${seenPhones[phone]!! + 1}")
                email != null && seenEmails[email] != null -> match(index, "Same email as row ${seenEmails[email]!! + 1}")
                else -> ImportRowResult(index, ImportOutcome.ADD)
            }

            if (outcome.outcome == ImportOutcome.ADD) {
                phone?.let { seenPhones[it] = index }
                email?.let { seenEmails[it] = index }
            }
            outcome
        }

        val toAdd = rows.filter { it.outcome == ImportOutcome.ADD }.map { list[it.row] }
        if (!dryRun && toAdd.isNotEmpty()) {
            transaction {
                toAdd.forEach { customer ->
                    customers.add(customer.copy(identifier = Uuid.generateV7(), name = customer.name.trim(), email = customer.email.trim(), phone = customer.phone.trim()))
                }
                auditLog.log(actor, AuditAction.CREATE, "customers", null, summary("customers", toAdd.size, rows))
            }
            AppLogger.m(TAG, summary("customers", toAdd.size, rows))
        }

        return ImportResult(rows, added = if (dryRun) 0 else toAdd.size, dryRun = dryRun)
    }

    /**
     * @return Null when the existing items couldn't be read
     */
    fun inventory(list: List<Inventory>, dryRun: Boolean, actor: Uuid?): ImportResult? {
        val existing = inventory.names()?.associateBy { it.trim().lowercase() } ?: return null
        val seen = mutableMapOf<String, Int>()

        val rows = list.mapIndexed { index, item ->
            val key = item.name.trim().lowercase()

            val outcome = when {
                key.isEmpty() -> error(index, "No name")
                item.priceCents < 0 || item.costCents < 0 -> error(index, "Prices can't be negative")
                item.stock < 0 -> error(index, "Stock can't be negative")
                existing[key] != null -> match(index, "${existing[key]} is already in inventory")
                seen[key] != null -> match(index, "Same name as row ${seen[key]!! + 1}")
                else -> ImportRowResult(index, ImportOutcome.ADD)
            }

            if (outcome.outcome == ImportOutcome.ADD) seen[key] = index
            outcome
        }

        val toAdd = rows.filter { it.outcome == ImportOutcome.ADD }.map { list[it.row] }
        if (!dryRun && toAdd.isNotEmpty()) {
            transaction {
                toAdd.forEach { item -> inventory.add(item.copy(identifier = Uuid.generateV7(), name = item.name.trim())) }
                auditLog.log(actor, AuditAction.CREATE, "inventory", null, summary("items", toAdd.size, rows))
            }
            AppLogger.m(TAG, summary("items", toAdd.size, rows))
        }

        return ImportResult(rows, added = if (dryRun) 0 else toAdd.size, dryRun = dryRun)
    }

    private fun error(row: Int, message: String) = ImportRowResult(row, ImportOutcome.ERROR, message)

    private fun match(row: Int, message: String) = ImportRowResult(row, ImportOutcome.MATCH, message)

    private fun summary(what: String, added: Int, rows: List<ImportRowResult>): String {
        val matched = rows.count { it.outcome == ImportOutcome.MATCH }
        val failed = rows.count { it.outcome == ImportOutcome.ERROR }
        return "Imported $added $what, skipped $matched already there and $failed with problems"
    }

    companion object {
        /** Most records in one import, so one request can't tie the server up */
        const val MAX_ROWS = 20_000

        /** A phone number's digits, null when there are too few to be one worth matching */
        fun phoneKey(phone: String): String? = phone.filter { it.isDigit() }.takeIf { it.length >= 7 }
    }
}
