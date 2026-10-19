package org.mr0btain.ticketman.data.money

import kotlin.math.abs
import kotlin.math.roundToLong
import org.mr0btain.ticketman.data.shop.ShopProfile

/**
 * Money is kept as whole cents everywhere so sums don't drift. These turn cents into text and back
 */
object Money {
    /** What [format] puts before amounts unless told otherwise. A shop can pick its own, [ShopProfile.currencySymbol] */
    const val DEFAULT_SYMBOL = "$"

    /** Longest currency symbol a shop can set */
    const val MAX_SYMBOL_LENGTH = 4

    /** 1234 as "$12.34", negative as "-$12.34", with [symbol] in place of "$" */
    fun format(cents: Long, symbol: String = DEFAULT_SYMBOL): String {
        val sign = if (cents < 0) "-" else ""
        val value = abs(cents)
        return "$sign$symbol${value / 100}.${(value % 100).toString().padStart(2, '0')}"
    }

    /**
     * Reads what someone typed or what older versions stored, like "12.5", "$12.50" or "$12.3456".
     * Anything but digits, one dot and a leading minus is ignored. Null if there's no number
     */
    fun parse(text: String): Long? {
        val negative = text.trim().startsWith("-")
        val cleaned = text.filter { it.isDigit() || it == '.' }
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null

        val dollars = cleaned.toDoubleOrNull() ?: return null
        val cents = (dollars * 100).roundToLong()

        return if (negative) -cents else cents
    }

    /** Dollars as a double, for fields that edit dollars */
    fun fromDollars(dollars: Double): Long = (dollars * 100).roundToLong()

    fun toDollars(cents: Long): Double = cents / 100.0

    /**
     * Tax rate like "7.5" (percent) as basis points, 750. Null if it isn't a number from 0 to 100
     */
    fun percentToBasisPoints(percent: String): Int? {
        val value = percent.trim().removeSuffix("%").toDoubleOrNull() ?: return null
        if (value < 0 || value > 100) return null
        return (value * 100).roundToLong().toInt()
    }

    /** [cents] times a rate in basis points, rounded half up to the cent */
    fun applyRate(cents: Long, basisPoints: Int): Long =
        (cents * basisPoints + 5_000) / 10_000
}
