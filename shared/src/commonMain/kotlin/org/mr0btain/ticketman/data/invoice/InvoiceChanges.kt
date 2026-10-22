package org.mr0btain.ticketman.data.invoice

import org.mr0btain.ticketman.server.request.ChargeChange
import org.mr0btain.ticketman.server.request.InvoiceChangeRequest

/**
 * Edits to an invoice's lines as changes rather than a whole new list, so two people working on one invoice don't
 * undo each other: the counter editing it while a technician logs work, which adds a line on the server
 */
object InvoiceChanges {
    /**
     * What changed from [before] (the lines when the invoice was opened) to [after] (the lines being saved), matched
     * by line id
     */
    fun between(before: List<Charge>, beforeDiscount: Long, after: List<Charge>, afterDiscount: Long): InvoiceChangeRequest {
        val old = before.associateBy { it.identifier }
        val new = after.associateBy { it.identifier }

        return InvoiceChangeRequest(
            added = after.filter { it.identifier !in old },
            changed = after.mapNotNull { line -> old[line.identifier]?.takeIf { it != line }?.let { ChargeChange(it, line) } },
            removed = before.filter { it.identifier !in new },
            discountCents = afterDiscount.takeIf { it != beforeDiscount },
            discountWas = beforeDiscount
        )
    }

    /** [apply]'s answer */
    sealed interface Outcome {
        data class Applied(val charges: List<Charge>, val discountCents: Long) : Outcome

        /** Someone else changed what this change touches. [message] says which line */
        data class Conflict(val message: String) : Outcome
    }

    /**
     * Applies [request] to the lines the invoice has now. Changed lines keep their place and new ones go on the end.
     * A line removed that's already gone is fine; one changed or removed that someone else changed or removed since,
     * or a discount someone else changed, is a [Outcome.Conflict]
     */
    fun apply(current: List<Charge>, currentDiscount: Long, request: InvoiceChangeRequest): Outcome {
        val lines = current.toMutableList()
        fun indexOf(line: Charge) = lines.indexOfFirst { it.identifier == line.identifier }
        fun name(line: Charge) = "\"${line.description.ifBlank { line.type.name.lowercase() }}\""

        request.removed.forEach { line ->
            val index = indexOf(line)
            if (index < 0) return@forEach
            if (lines[index] != line) return Outcome.Conflict("Someone else changed ${name(line)} on this invoice")
            lines.removeAt(index)
        }

        request.changed.forEach { (before, after) ->
            val index = indexOf(before)
            if (index < 0) return Outcome.Conflict("Someone else removed ${name(before)} from this invoice")
            if (lines[index] != before) return Outcome.Conflict("Someone else changed ${name(before)} on this invoice")
            lines[index] = after
        }

        request.added.forEach { line ->
            val index = indexOf(line)
            // Sent twice, like after an answer that was lost
            if (index >= 0) {
                if (lines[index] != line) return Outcome.Conflict("Someone else changed ${name(line)} on this invoice")
                return@forEach
            }
            lines += line
        }

        val discount = request.discountCents ?: currentDiscount
        if (request.discountCents != null && currentDiscount != request.discountWas && currentDiscount != discount) {
            return Outcome.Conflict("Someone else changed this invoice's discount")
        }

        return Outcome.Applied(lines, discount)
    }
}
