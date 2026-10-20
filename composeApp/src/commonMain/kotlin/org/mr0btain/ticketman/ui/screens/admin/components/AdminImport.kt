package org.mr0btain.ticketman.ui.screens.admin.components

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.importing.ImportField
import org.mr0btain.ticketman.components.data.importing.ImportKind
import org.mr0btain.ticketman.components.data.importing.ImportRow
import org.mr0btain.ticketman.components.data.importing.ImportSheet
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.response.ImportOutcome
import org.mr0btain.ticketman.server.response.ImportResult

/**
 * Importing customers or inventory from a CSV, on the Server tab. The file is read here; which rows the server would
 * skip comes from a dry run of the same import, redone whenever a column is matched differently
 * @param onImported Called after records were added, so lists reload
 */
class AdminImport(
    private val remoteHandler: RemoteHandler,
    private val support: AdminSupport,
    private val onImported: suspend () -> Unit
) {
    private val admin get() = remoteHandler.adminRepo

    private val _sheet = MutableStateFlow<ImportSheet?>(null)
    /** The file being imported, null when there isn't one */
    val sheet = _sheet.asStateFlow()

    private val _mapping = MutableStateFlow<Map<ImportField, Int?>>(emptyMap())
    /** Which column fills each field */
    val mapping = _mapping.asStateFlow()

    private val _preview = MutableStateFlow<ImportPreview?>(null)
    /** What importing would do, null while it's being worked out */
    val preview = _preview.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)
    /** Why the preview couldn't be made */
    val problem = _problem.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()

    private var checking: Job? = null

    /**
     * Opens a file to import
     * @return What's wrong with it, or null when it's open
     */
    fun open(kind: ImportKind, text: String): String? {
        val sheet = ImportSheet.read(kind, text) ?: return "There's nothing in that file under its headers"

        _sheet.value = sheet
        _mapping.value = sheet.mapping
        check()
        return null
    }

    /** Reads a picked file and opens it, saying so when it can't be */
    fun openFile(kind: ImportKind, file: PlatformFile) {
        if (file.size() > MAX_FILE_BYTES) {
            support.notify("${file.name} is too big to import, split it into files under ${MAX_FILE_BYTES / 1_000_000} MB")
            return
        }

        support.scope.launch {
            val text = try {
                file.readBytes().decodeToString()
            } catch (ex: Exception) {
                support.notify("Couldn't read ${file.name}")
                return@launch
            }

            open(kind, text)?.let { support.notify(it) }
        }
    }

    /** Fills [field] from [column], or nothing when it's null */
    fun map(field: ImportField, column: Int?) {
        _mapping.value = _mapping.value + (field to column)
        check()
    }

    /** Adds the rows the preview says are new */
    fun import(onDone: () -> Unit) {
        val sheet = _sheet.value ?: return

        support.scope.launch {
            _importing.value = true
            when (val result = send(sheet, dryRun = false)) {
                is TmResult.Success -> {
                    val added = result.data.result.added
                    support.notify("Added ${sheet.kind.count(added)}")
                    onImported()
                    support.auditChanged()
                    close()
                    onDone()
                }
                is TmResult.Error -> _problem.value = "Couldn't import: ${result.error.message}"
            }
            _importing.value = false
        }
    }

    fun close() {
        checking?.cancel()
        _sheet.value = null
        _mapping.value = emptyMap()
        _preview.value = null
        _problem.value = null
    }

    // Asks the server what it would do, after a moment so picking columns quickly only asks once
    private fun check() {
        val sheet = _sheet.value ?: return
        checking?.cancel()
        _preview.value = null
        _problem.value = null

        checking = support.scope.launch {
            delay(CHECK_DELAY_MILLIS)
            when (val result = send(sheet, dryRun = true)) {
                is TmResult.Success -> _preview.value = result.data.preview
                is TmResult.Error -> _problem.value = "Couldn't check the file with the server: ${result.error.message}"
            }
        }
    }

    // Sends the rows that could be read, and lines the server's answer up with the file's rows
    private suspend fun send(sheet: ImportSheet, dryRun: Boolean): TmResult<Sent, ServerError> {
        val mapping = _mapping.value

        return when (sheet.kind) {
            ImportKind.CUSTOMERS -> {
                val rows = sheet.customers(mapping)
                sent(rows, admin.importCustomers(rows.mapNotNull { it.record }, dryRun)) { customer ->
                    PreviewDetails(customer.name, contact = listOf(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(", "))
                }
            }
            ImportKind.INVENTORY -> {
                val rows = sheet.inventory(mapping)
                sent(rows, admin.importInventory(rows.mapNotNull { it.record }, dryRun)) { item ->
                    PreviewDetails(item.name, priceCents = item.priceCents, stock = item.stock)
                }
            }
        }
    }

    private fun <T> sent(rows: List<ImportRow<T>>, answer: TmResult<ImportResult, ServerError>, details: (T) -> PreviewDetails): TmResult<Sent, ServerError> {
        val result = when (answer) {
            is TmResult.Error -> return answer
            is TmResult.Success -> answer.data
        }

        // The server numbers what it was sent, which skips rows that couldn't be read here
        val byRecord = result.rows.associateBy { it.row }
        var sentIndex = 0
        val preview = rows.map { row ->
            if (row.record == null) {
                ImportPreviewRow(row.line, PreviewDetails(""), ImportOutcome.ERROR, row.problem.orEmpty())
            } else {
                val answered = byRecord[sentIndex++]
                ImportPreviewRow(row.line, details(row.record), answered?.outcome ?: ImportOutcome.ERROR, answered?.message.orEmpty())
            }
        }

        return TmResult.Success(Sent(result, ImportPreview(preview)))
    }

    private class Sent(val result: ImportResult, val preview: ImportPreview)

    private companion object {
        const val CHECK_DELAY_MILLIS = 300L

        // Far more than the server's 20,000 rows at once
        const val MAX_FILE_BYTES = 20_000_000L
    }
}

/** What importing a file would do, row by row */
data class ImportPreview(val rows: List<ImportPreviewRow>) {
    val toAdd: Int get() = rows.count { it.outcome == ImportOutcome.ADD }
    val matched: Int get() = rows.count { it.outcome == ImportOutcome.MATCH }
    val failed: Int get() = rows.count { it.outcome == ImportOutcome.ERROR }
}

/**
 * @param line The row's line in the file, counting the header
 * @param message Which record it matched, or what's wrong
 */
data class ImportPreviewRow(
    val line: Int,
    val details: PreviewDetails,
    val outcome: ImportOutcome,
    val message: String
)

/** What the preview shows of a row: the name, then contact details for a customer or price and stock for an item */
data class PreviewDetails(
    val name: String,
    val contact: String = "",
    val priceCents: Long? = null,
    val stock: Int? = null
)
