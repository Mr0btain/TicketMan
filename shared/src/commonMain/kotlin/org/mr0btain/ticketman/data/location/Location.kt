package org.mr0btain.ticketman.data.location

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.ShopProfile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * One of the shop's locations. Tickets, invoices, stock counts, stations, appointments and purchase orders belong to
 * one; customers, the catalog of items and settings are the company's. Every server has at least one, the main
 * location, made on start from the shop profile
 * @param code Put before the location's ticket, invoice and purchase order numbers, like `DT` for DT-26-00000042.
 * 1 to 4 capital letters or digits. Blank only for the main location, whose numbers stay as they always were
 * @param name What staff call it, like "Downtown"
 * @param street Its address and contacts, on its claim slips, invoices and the status page. Blank ones use the shop
 * profile's
 * @param taxRate Its sales tax rate as typed, like "8.25", null for the shop profile's
 * @param taxClassRates Its rates for the shop's tax classes, by class, for the ones that differ here
 * @param main Filled in by the server: the location older records belong to, which can't be retired
 * @param retired Taken out of use: not offered for new work, its records kept
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Location(
    val identifier: Uuid = Uuid.generateV7(),
    val code: String = "",
    val name: String = "",
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val phone: String = "",
    val email: String = "",
    val taxRate: String? = null,
    val taxClassRates: Map<String, String> = emptyMap(),
    val main: Boolean = false,
    val retired: Boolean = false
) {
    /** What's wrong with this location among [others] (the rest, not this one), or null */
    fun problem(others: List<Location>): String? = when {
        name.isBlank() -> "Give the location a name"
        name.trim().length > MAX_NAME -> "Names are up to $MAX_NAME characters"
        code.isBlank() && !main -> "Give the location a code for its numbers, like DT"
        code.isNotBlank() && !CODE.matches(code) -> "A code is 1 to 4 capital letters or digits, like DT"
        code == "INV" || code == "PO" -> "$code is taken by invoice and order numbers"
        others.any { it.code == code } -> "Another location already uses ${code.ifBlank { "no code" }}"
        others.any { it.name.trim().equals(name.trim(), ignoreCase = true) } -> "Another location is already called ${name.trim()}"
        taxRate != null && Money.percentToBasisPoints(taxRate) == null -> "The tax rate has to be a percent, like 8.25"
        taxClassRates.values.any { Money.percentToBasisPoints(it) == null } -> "Each tax class rate has to be a percent, like 2"
        main && retired -> "The main location can't be retired"
        else -> null
    }

    /** Its name with the code, for pickers */
    val label: String
        get() = if (code.isBlank()) name else "$name ($code)"

    companion object {
        const val MAX_NAME = 60
        val CODE = Regex("^[A-Z0-9]{1,4}$")
    }
}

/**
 * The shop profile as it applies at [location]: its own address, contacts and tax rates in place of the company's
 * where it has them. The shop's name stays, with the location's after it when there's more than one, so a claim
 * slip says which shop it's from. Null leaves the profile as it is
 * @param named Whether to put the location's name after the shop's, true when the shop has more than one
 */
fun ShopProfile.at(location: Location?, named: Boolean = false): ShopProfile {
    if (location == null) return this

    return copy(
        name = if (named && location.name.isNotBlank()) listOf(name, location.name).filter { it.isNotBlank() }.joinToString(", ") else name,
        street = location.street.ifBlank { street },
        city = location.city.ifBlank { city },
        state = location.state.ifBlank { state },
        zip = location.zip.ifBlank { zip },
        phone = location.phone.ifBlank { phone },
        email = location.email.ifBlank { email },
        taxRate = location.taxRate ?: taxRate,
        taxClasses = taxClasses.map { taxClass -> location.taxClassRates[taxClass.identifier]?.let { taxClass.copy(rate = it) } ?: taxClass }
    )
}

/** The active locations, main first then by name */
val List<Location>.active: List<Location>
    get() = filter { !it.retired }.sortedWith(compareByDescending<Location> { it.main }.thenBy { it.name.lowercase() })

/** The location with this id, or null */
@OptIn(ExperimentalUuidApi::class)
fun List<Location>.byId(identifier: Uuid?): Location? = identifier?.let { id -> firstOrNull { it.identifier == id } }
