package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.components.utils.formatQuantity
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.selection.TmCheckbox
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.gift
import ticketman.composeapp.generated.resources.package_icon
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.x
import kotlin.uuid.Uuid

/*
 * Editing invoice lines, at pickup and when building an estimate
 */

/** A line being edited. Quantity stays text so a half typed number like "1." isn't thrown away */
class ChargeDraft(charge: Charge) {
    val identifier: Uuid = charge.identifier
    val type: ChargeType = charge.type
    val inventoryIdentifier: Uuid? = charge.inventoryIdentifier

    var description by mutableStateOf(charge.description)
    var quantity by mutableStateOf(formatQuantity(charge.quantity))
    var priceCents by mutableLongStateOf(charge.unitPriceCents)
    var taxable by mutableStateOf(charge.taxable)

    /** A gift card's code, blank for a new card */
    var reference by mutableStateOf(charge.reference)

    /** Null while the quantity isn't a number yet */
    fun toCharge(): Charge? {
        val amount = quantity.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        return Charge(identifier, type, description.trim(), amount, priceCents, inventoryIdentifier, taxable, reference.trim())
    }
}

/**
 * The lines with buttons to add labor, something else, or a part from inventory
 * @param laborCents Price a new labor line starts at
 * @param giftCards Offers a gift card line, sold when the invoice is paid. Not on estimates
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChargeLines(
    drafts: SnapshotStateList<ChargeDraft>,
    compact: Boolean,
    laborCents: Long,
    onSearchParts: suspend (String) -> List<Inventory>,
    giftCards: Boolean = false
) {
    val spacing = TmTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        drafts.forEach { draft -> ChargeRow(draft, compact = compact, onRemove = { drafts.remove(draft) }) }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        TmButton(
            text = "Add labor",
            variant = TmButtonVariant.SECONDARY,
            leadingIcon = Res.drawable.plus,
            onClick = { drafts.add(ChargeDraft(Charge(type = ChargeType.LABOR, description = "Labor", unitPriceCents = laborCents))) }
        )

        TmButton(
            text = "Add other",
            variant = TmButtonVariant.SECONDARY,
            leadingIcon = Res.drawable.plus,
            onClick = { drafts.add(ChargeDraft(Charge(type = ChargeType.OTHER, description = ""))) }
        )

        if (giftCards) {
            TmButton(
                text = "Add gift card",
                variant = TmButtonVariant.SECONDARY,
                leadingIcon = Res.drawable.gift,
                onClick = { drafts.add(ChargeDraft(Charge(type = ChargeType.GIFT_CARD, description = "Gift card", taxable = false))) }
            )
        }
    }

    PartPicker(onSearch = onSearchParts) { part ->
        drafts.add(ChargeDraft(Charge(type = ChargeType.PART, description = part.name, unitPriceCents = part.priceCents, inventoryIdentifier = part.identifier)))
    }
}

/** What's wrong with the lines being edited, or null */
fun chargesProblem(drafts: List<ChargeDraft>): String? {
    val charges = drafts.map { it.toCharge() }
    return when {
        charges.any { it == null } -> "Fix the quantity on each line"
        charges.any { it!!.description.isBlank() } -> "Every line needs a description"
        charges.any { it!!.type == ChargeType.GIFT_CARD && it.unitPriceCents <= 0 } -> "Put an amount on the gift card"
        else -> null
    }
}

/**
 * One invoice line to edit
 * @param compact Puts the numbers on a second row under the description, for narrow windows
 */
@Composable
private fun ChargeRow(draft: ChargeDraft, compact: Boolean, onRemove: () -> Unit) {
    val spacing = TmTheme.spacing

    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChargeTypeBadge(draft)
                ChargeDescription(draft, Modifier.weight(1f))
                RemoveCharge(onRemove)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChargeNumbers(draft, Modifier.weight(1f))
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChargeTypeBadge(draft)
        ChargeDescription(draft, Modifier.weight(1f))
        ChargeNumbers(draft)
        RemoveCharge(onRemove)
    }
}

@Composable
private fun ChargeTypeBadge(draft: ChargeDraft) {
    TmBadge(modifier = Modifier.width(64.dp), text = formatEnumName(draft.type.name), tone = TmTheme.colors.neutral, dot = false)
}

@Composable
private fun ChargeDescription(draft: ChargeDraft, modifier: Modifier) {
    TmTextField(
        modifier = modifier,
        value = draft.description,
        onValueChange = { draft.description = it },
        placeholder = "Description",
        // Part names come from inventory
        readOnly = draft.type == ChargeType.PART
    )
}

/**
 * Quantity, price, taxable and the line's total
 * @param totalModifier Lets the total take the spare room on a row of its own
 */
@Composable
private fun RowScope.ChargeNumbers(draft: ChargeDraft, totalModifier: Modifier = Modifier.width(88.dp)) {
    val charge = draft.toCharge()

    // One card, never taxed: its code goes where the quantity and tax would
    if (draft.type == ChargeType.GIFT_CARD) {
        TmTextField(
            modifier = Modifier.width(80.dp + 120.dp + TmTheme.spacing.sm),
            value = draft.reference,
            onValueChange = { draft.reference = it },
            placeholder = "Code, blank for new",
            textStyle = TmTheme.typography.numeric
        )
        TmMoneyField(modifier = Modifier.width(120.dp), cents = draft.priceCents, onCentsChange = { draft.priceCents = it })
        Box(totalModifier, contentAlignment = Alignment.CenterEnd) {
            if (charge != null) TmMoneyText(charge.totalCents)
        }
        return
    }

    TmTextField(
        modifier = Modifier.width(80.dp),
        value = draft.quantity,
        onValueChange = { draft.quantity = it },
        placeholder = if (draft.type == ChargeType.LABOR) "Hours" else "Qty",
        textStyle = TmTheme.typography.numeric,
        error = if (draft.quantity.toDoubleOrNull()?.let { it > 0 } == true) null else ""
    )

    TmMoneyField(
        modifier = Modifier.width(120.dp),
        cents = draft.priceCents,
        onCentsChange = { draft.priceCents = it }
    )

    TmCheckbox(checked = draft.taxable, onCheckedChange = { draft.taxable = it }, label = "Tax")

    Box(totalModifier, contentAlignment = Alignment.CenterEnd) {
        if (charge != null) TmMoneyText(charge.totalCents) else Text("", style = TmTheme.typography.numeric)
    }
}

@Composable
private fun RemoveCharge(onRemove: () -> Unit) {
    TmIconButton(icon = Res.drawable.x, contentDescription = "Remove line", onClick = onRemove, size = TmControlSize.SMALL)
}

/** Searches inventory and adds the picked part as a line */
@Composable
private fun PartPicker(onSearch: suspend (String) -> List<Inventory>, onPick: (Inventory) -> Unit) {
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Inventory>>(emptyList()) }

    LaunchedEffect(query) {
        delay(300)
        matches = onSearch(query)
    }

    TmSearchField(
        modifier = Modifier.fillMaxWidth(),
        query = query,
        onQueryChange = { query = it },
        placeholder = "Add a part from inventory"
    )

    Column {
        matches.forEach { part ->
            TmListRow(
                title = part.name,
                icon = Res.drawable.package_icon,
                subtitle = stockText(part.stock),
                trailing = { TmMoneyText(part.priceCents) },
                onClick = {
                    onPick(part)
                    query = ""
                    matches = emptyList()
                }
            )
        }
    }
}
