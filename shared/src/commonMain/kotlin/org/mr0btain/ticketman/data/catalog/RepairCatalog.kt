package org.mr0btain.ticketman.data.catalog

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Warranty
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.DeviceKind
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * One repair the shop offers on a model at a set price, like a screen replacement on an iPhone 14 Pro
 * @param name What it's called on the ticket and the invoice line
 * @param priceCents The whole price the customer pays, part included
 * @param inventoryIdentifier The part it uses, null for labor only. With one, the invoice line is that part, so its
 * stock is taken at pickup and the profit counts its cost
 * @param warrantyDays How long the repair is guaranteed, null for the shop's own warranty
 * @param taxClass The shop's tax class it's charged at, null for the main rate
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class RepairOffer(
    val identifier: Uuid = Uuid.generateV7(),
    val name: String = "",
    val priceCents: Long = 0,
    val inventoryIdentifier: Uuid? = null,
    val warrantyDays: Int? = null,
    val taxClass: String? = null
)

/**
 * A device model on the shop's repair menu, with the repairs offered on it. Check-in picks one from the device
 * typed, and the repairs ticked go on the invoice as lines
 */
@OptIn(ExperimentalUuidApi::class)
@Serializable
data class CatalogModel(
    val identifier: Uuid = Uuid.generateV7(),
    val kind: DeviceKind = DeviceKind.PHONE,
    val manufacturer: String = "",
    val model: String = "",
    val repairs: List<RepairOffer> = emptyList()
) {
    /** Like "Apple iPhone 14 Pro" */
    val label: String
        get() = "${manufacturer.trim()} ${model.trim()}".trim()
}

/** A repair picked from the menu, with the model it's on */
data class PickedRepair(val model: CatalogModel, val offer: RepairOffer)

/** Rules for the repair menu, checked on both sides */
@OptIn(ExperimentalUuidApi::class)
object RepairCatalog {
    const val MAX_NAME = 80
    const val MAX_REPAIRS = 60

    /** What's wrong with [model] given the rest of the menu, [others], or null when it can be saved */
    fun problem(model: CatalogModel, others: List<CatalogModel>): String? {
        val names = model.repairs.map { it.name.trim().lowercase() }
        return when {
            model.model.isBlank() -> "Enter the model"
            model.manufacturer.length > MAX_NAME || model.model.length > MAX_NAME -> "Names can be up to $MAX_NAME characters"
            others.any { it.identifier != model.identifier && same(it, model) } -> "${model.label} is on the menu already"
            model.repairs.size > MAX_REPAIRS -> "A model can have up to $MAX_REPAIRS repairs"
            model.repairs.any { it.name.isBlank() } -> "Every repair needs a name"
            model.repairs.any { it.name.length > MAX_NAME } -> "Repair names can be up to $MAX_NAME characters"
            names.toSet().size != names.size -> "Two repairs have the same name"
            model.repairs.any { it.priceCents < 0 } -> "Prices can't be negative"
            model.repairs.any { (it.warrantyDays ?: 0) !in 0..Warranty.MAX_DAYS } -> "A warranty can be 0 to ${Warranty.MAX_DAYS} days"
            model.repairs.map { it.identifier }.toSet().size != model.repairs.size -> "Each repair needs its own id"
            else -> null
        }
    }

    /** [model] with its names trimmed */
    fun cleaned(model: CatalogModel): CatalogModel =
        model.copy(
            manufacturer = model.manufacturer.trim(),
            model = model.model.trim(),
            repairs = model.repairs.map { it.copy(name = it.name.trim(), taxClass = it.taxClass?.ifBlank { null }) }
        )

    // Same manufacturer and model, ignoring case and spacing
    private fun same(a: CatalogModel, b: CatalogModel): Boolean =
        key(a.manufacturer) == key(b.manufacturer) && key(a.model) == key(b.model)

    private fun key(text: String): String = text.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * The models on [menu] that fit [device] as typed so far, best first: the same model, then ones whose name holds
     * what was typed. Nothing typed matches nothing
     */
    fun matches(menu: List<CatalogModel>, device: Device): List<CatalogModel> {
        val model = key(device.model)
        val maker = key(device.manufacturer)
        if (model.isEmpty() && maker.isEmpty()) return emptyList()

        return menu
            .filter { entry ->
                (maker.isEmpty() || key(entry.manufacturer).contains(maker) || maker.contains(key(entry.manufacturer))) &&
                    (model.isEmpty() || key(entry.model).contains(model) || key(entry.label).contains(model))
            }
            .sortedWith(compareByDescending<CatalogModel> { key(it.model) == model }.thenBy { it.label.lowercase() })
    }

    /** Where to find each of [identifiers] on [menu], null when one isn't on it */
    fun find(menu: List<CatalogModel>, identifiers: List<Uuid>): List<PickedRepair>? =
        identifiers.map { id ->
            menu.firstNotNullOfOrNull { model -> model.repairs.firstOrNull { it.identifier == id }?.let { PickedRepair(model, it) } }
                ?: return null
        }

    /** The invoice line for a repair: the part it uses, or labor when it has none */
    fun charge(picked: PickedRepair): Charge {
        val offer = picked.offer
        return Charge(
            type = if (offer.inventoryIdentifier != null) ChargeType.PART else ChargeType.LABOR,
            description = "${offer.name} (${picked.model.label})",
            quantity = 1.0,
            unitPriceCents = offer.priceCents,
            inventoryIdentifier = offer.inventoryIdentifier,
            taxClass = offer.taxClass,
            warrantyDays = offer.warrantyDays
        )
    }

    /** The ticket's problem when nothing else was typed, like "Screen replacement, Battery" */
    fun problemText(picked: List<PickedRepair>): String = picked.joinToString(", ") { it.offer.name }

    /** What the repairs come to before tax */
    fun totalCents(picked: List<PickedRepair>): Long = picked.sumOf { it.offer.priceCents }
}
