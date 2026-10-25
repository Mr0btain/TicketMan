package org.mr0btain.ticketman.data.invoice

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.money.Money
import kotlin.random.Random

/**
 * A tax rate the shop charges on some things instead of its main rate, like a lower one on food or a higher one on
 * services. Set in the shop profile; lines and inventory items pick one by [identifier]
 * @param rate A percent as typed, like "2.5"
 */
@Serializable
data class TaxClass(
    val identifier: String,
    val name: String,
    val rate: String
)

/**
 * A tax class's rate as it was when an invoice or estimate was made, kept on it so changing the shop's rates later
 * doesn't change what a customer was charged
 */
@Serializable
data class TaxRate(
    val identifier: String,
    val name: String,
    val basisPoints: Int
)

/**
 * The tax on one group of lines, for showing the breakdown
 * @param name The class's name, or "Tax" for the shop's main rate
 * @param taxableCents What it was charged on, after its share of the discount
 */
@Serializable
data class TaxLine(
    val name: String,
    val basisPoints: Int,
    val taxableCents: Long,
    val taxCents: Long
) {
    /** Like "Food, 2.5%" */
    val label: String
        get() = "$name, ${basisPoints / 100.0}%"
}

object TaxClasses {
    /** A new class's identifier */
    fun newIdentifier(random: Random = Random.Default): String =
        (1..8).map { "abcdefghijkmnpqrstuvwxyz23456789"[random.nextInt(32)] }.joinToString("")

    /** The rates of [classes] whose rate reads as a percent */
    fun rates(classes: List<TaxClass>): List<TaxRate> =
        classes.mapNotNull { taxClass ->
            Money.percentToBasisPoints(taxClass.rate)?.let { TaxRate(taxClass.identifier, taxClass.name.trim(), it) }
        }

    /** What's wrong with the classes a shop is saving, or null */
    fun problem(classes: List<TaxClass>): String? {
        classes.forEach { taxClass ->
            if (taxClass.identifier.isBlank()) return "Every tax class needs an identifier"
            if (taxClass.name.isBlank()) return "Every tax class needs a name"
            if (Money.percentToBasisPoints(taxClass.rate) == null) return "Tax class ${taxClass.name.trim()} needs a rate from 0 to 100"
        }
        if (classes.map { it.identifier }.toSet().size != classes.size) return "Two tax classes have the same identifier"
        return null
    }

    /**
     * The rates an invoice kept, with any of the shop's [current] ones it doesn't have yet added on, for lines that
     * use a class made after it
     */
    fun merged(kept: List<TaxRate>, current: List<TaxRate>): List<TaxRate> =
        kept + current.filter { rate -> kept.none { it.identifier == rate.identifier } }
}
