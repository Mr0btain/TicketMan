package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.components.utils.formatQuantity
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.selection.TmCheckbox
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.components.TicketDetail
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.credit_card
import ticketman.composeapp.generated.resources.file_text
import ticketman.composeapp.generated.resources.package_icon
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.x
import kotlin.uuid.Uuid

/** Narrower than this, invoice lines take two rows */
private val INVOICE_MIN_WIDTH = 560.dp

/** A line being edited. Quantity stays text so a half typed number like "1." isn't thrown away */
private class ChargeDraft(charge: Charge) {
    val identifier: Uuid = charge.identifier
    val type: ChargeType = charge.type
    val inventoryIdentifier: Uuid? = charge.inventoryIdentifier

    var description by mutableStateOf(charge.description)
    var quantity by mutableStateOf(formatQuantity(charge.quantity))
    var priceCents by mutableLongStateOf(charge.unitPriceCents)
    var taxable by mutableStateOf(charge.taxable)

    /** Null while the quantity isn't a number yet */
    fun toCharge(): Charge? {
        val amount = quantity.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        return Charge(identifier, type, description.trim(), amount, priceCents, inventoryIdentifier, taxable)
    }
}

/**
 * Builds the invoice, takes payment and hands the ticket back
 * @param onFinish Saves the lines if they changed, takes [PaymentRequest]s and finishes, calls back with what went
 * wrong or null if it worked
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickupScreen(
    detail: TicketDetail,
    onSearchParts: suspend (String) -> List<Inventory>,
    onSave: (List<Charge>, Long, (String?) -> Unit) -> Unit,
    onRemovePayment: (Payment) -> Unit,
    onFinish: (List<Charge>, Long, List<PaymentRequest>, (String?) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val ticket = detail.ticket
    val invoice = detail.invoice ?: return
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    // Rebuilt when the server sends back new lines, but not when only the payments changed so edits aren't lost
    val drafts = remember(invoice.identifier, invoice.charges) { mutableStateListOf<ChargeDraft>().apply { addAll(invoice.charges.map { ChargeDraft(it) }) } }
    var discountCents by remember(invoice.identifier, invoice.discountCents) { mutableLongStateOf(invoice.discountCents) }
    val pending = remember(ticket.identifier) { mutableStateListOf<PaymentRequest>() }
    var paymentCents by remember(ticket.identifier) { mutableLongStateOf(0L) }
    var paymentMethod by remember(ticket.identifier) { mutableStateOf(PaymentMethod.CARD) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val charges = drafts.map { it.toCharge() }
    val validCharges = charges.filterNotNull()
    val chargesProblem = when {
        charges.any { it == null } -> "Fix the quantity on each line"
        validCharges.any { it.description.isBlank() } -> "Every line needs a description"
        else -> null
    }

    // Preview with the same math the server uses
    val totals = InvoiceMath.totals(validCharges, discountCents, invoice.taxRateBasisPoints)
    val paid = invoice.paidCents + pending.sumOf { it.amountCents }
    val balance = totals.totalCents - paid
    val unsaved = chargesProblem == null && (validCharges != invoice.charges || discountCents != invoice.discountCents)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        TmPageHeader(
            title = "Pickup",
            subtitle = listOfNotNull(invoice.label, detail.customer?.name).joinToString(", "),
            onBack = onBack,
            titleContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Pickup", style = TmTheme.typography.display, color = colors.text)
                    TmTicketIdText(number = ticket.label, style = TmTheme.typography.numericDisplay)
                }
            }
        )

        // Side by side when there's room. On narrow windows each panel takes the width and each line takes two rows
        BoxWithConstraints {
            val compact = maxWidth < INVOICE_MIN_WIDTH
            val paymentWidth = min(400.dp, maxWidth)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                // ------------------------------------------ Lines ------------------------------------------
                TmPanel(
                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = INVOICE_MIN_WIDTH, max = 760.dp),
                    title = "Invoice",
                    subtitle = if (drafts.isEmpty()) "No charges yet, add labor or parts below" else "${drafts.size} lines",
                    icon = Res.drawable.file_text
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        drafts.forEach { draft -> ChargeRow(draft, compact = compact, onRemove = { drafts.remove(draft) }) }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        TmButton(
                            text = "Add labor",
                            variant = TmButtonVariant.SECONDARY,
                            leadingIcon = Res.drawable.plus,
                            onClick = {
                                drafts.add(ChargeDraft(Charge(type = ChargeType.LABOR, description = "Labor", unitPriceCents = ticket.estimateCents ?: 0)))
                            }
                        )

                        TmButton(
                            text = "Add other",
                            variant = TmButtonVariant.SECONDARY,
                            leadingIcon = Res.drawable.plus,
                            onClick = { drafts.add(ChargeDraft(Charge(type = ChargeType.OTHER, description = ""))) }
                        )
                    }

                    PartPicker(onSearch = onSearchParts) { part ->
                        drafts.add(
                            ChargeDraft(
                                Charge(type = ChargeType.PART, description = part.name, unitPriceCents = part.priceCents, inventoryIdentifier = part.identifier)
                            )
                        )
                    }

                    TmMoneyField(
                        modifier = Modifier.width(200.dp),
                        label = "Discount",
                        cents = discountCents,
                        onCentsChange = { discountCents = it }
                    )
                }

                // ------------------------------------------ Totals and payment ------------------------------------------
                TmPanel(modifier = Modifier.width(paymentWidth), title = "Payment", icon = Res.drawable.credit_card) {
                    Column {
                        TmKeyValueRow(label = "Subtotal") { TmMoneyText(totals.subtotalCents) }
                        if (totals.discountCents > 0) TmKeyValueRow(label = "Discount") { TmMoneyText(-totals.discountCents, signed = true) }
                        TmKeyValueRow(label = "Tax, ${invoice.taxRateBasisPoints / 100.0}%") { TmMoneyText(totals.taxCents) }
                        TmKeyValueRow(label = "Total") { TmMoneyText(totals.totalCents, style = TmTheme.typography.numericLarge) }
                    }

                    if (invoice.payments.isNotEmpty() || pending.isNotEmpty()) {
                        TmDivider()

                        Column {
                            // Deposits and anything else already taken
                            invoice.payments.forEach { payment ->
                                PaymentRow(
                                    amountCents = payment.amountCents,
                                    method = payment.method,
                                    detail = listOfNotNull(payment.note.ifBlank { null }, formatDateTime(payment.receivedAt)).joinToString(", "),
                                    onRemove = { onRemovePayment(payment) }
                                )
                            }

                            // Taken now, sent with the pickup
                            pending.forEach { payment ->
                                PaymentRow(
                                    amountCents = payment.amountCents,
                                    method = payment.method,
                                    detail = "Taking now",
                                    onRemove = { pending.remove(payment) }
                                )
                            }
                        }
                    }

                    TmDivider()

                    // The number the person at the till is looking for
                    TmKeyValueRow(label = if (balance >= 0) "Balance due" else "Change due") {
                        TmMoneyText(
                            cents = if (balance >= 0) balance else -balance,
                            style = TmTheme.typography.numericDisplay,
                            color = if (balance > 0) colors.danger.color else colors.success.color
                        )
                    }

                    TmSegmented(
                        label = "Paid by",
                        options = PaymentMethod.entries,
                        selected = paymentMethod,
                        onSelect = { paymentMethod = it },
                        optionLabel = { formatEnumName(it.name) }
                    )

                    // Leaving it at 0.00 takes whatever is still owed
                    val amount = if (paymentCents == 0L) balance.takeIf { it > 0 } else paymentCents

                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
                        TmMoneyField(
                            modifier = Modifier.weight(1f),
                            label = "Amount",
                            cents = paymentCents,
                            onCentsChange = { paymentCents = it }
                        )

                        TmButton(
                            text = amount?.let { "Take ${formatMoney(it)}" } ?: "Take",
                            enabled = amount != null,
                            variant = TmButtonVariant.SECONDARY,
                            onClick = {
                                amount?.let { pending.add(PaymentRequest(paymentMethod, it)) }
                                paymentCents = 0
                            }
                        )
                    }

                    if (balance > 0) {
                        Text(
                            text = "Leave it at 0.00 to take the full ${formatMoney(balance)}",
                            style = TmTheme.typography.caption,
                            color = colors.textMuted
                        )
                    }

                    (message ?: chargesProblem)?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }

                    TmDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        TmButton(
                            text = "Save invoice",
                            enabled = unsaved && !busy,
                            variant = TmButtonVariant.GHOST,
                            size = TmControlSize.LARGE,
                            onClick = {
                                busy = true
                                message = null
                                onSave(validCharges, discountCents) { problem ->
                                    busy = false
                                    message = problem
                                }
                            }
                        )

                        TmButton(
                            modifier = Modifier.weight(1f),
                            text = "Finish pickup",
                            enabled = chargesProblem == null && balance <= 0,
                            loading = busy,
                            size = TmControlSize.LARGE,
                            onClick = {
                                busy = true
                                message = null
                                onFinish(validCharges, discountCents, pending.toList()) { problem ->
                                    busy = false
                                    message = problem
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(amountCents: Long, method: PaymentMethod, detail: String, onRemove: () -> Unit) {
    TmListRow(
        title = formatEnumName(method.name),
        subtitle = detail,
        leading = { TmMoneyText(amountCents, Modifier.width(96.dp)) },
        trailing = { TmIconButton(icon = Res.drawable.x, contentDescription = "Remove payment", onClick = onRemove, size = TmControlSize.SMALL) }
    )
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

/** How many of a part are left, in words */
fun stockText(stock: Int): String = when {
    stock == Int.MAX_VALUE -> "Stock not tracked"
    stock <= 0 -> "Out of stock"
    else -> "$stock in stock"
}

/**
 * Shown after a pickup with what was paid and any change to hand back
 * @param actions Room for more buttons, like printing the receipt
 */
@Composable
fun PickedUpView(
    invoice: Invoice,
    onDone: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier.fillMaxSize().padding(spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterVertically)
    ) {
        TmBadge(text = "Picked up", tone = colors.success)
        TmTicketIdText(number = invoice.label, style = TmTheme.typography.numericDisplay)
        Text(
            text = "Total ${formatMoney(invoice.totalCents)}, paid ${formatMoney(invoice.paidCents)}",
            style = TmTheme.typography.bodyLarge,
            color = colors.textMuted
        )

        if (invoice.balanceCents < 0) {
            Text(text = "Change due", style = TmTheme.typography.title, color = colors.text)
            TmMoneyText(
                cents = -invoice.balanceCents,
                style = TmTheme.typography.numericHero,
                color = colors.success.color
            )
        }

        Spacer(Modifier.height(spacing.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
            actions()
            TmButton(text = "Done", onClick = onDone, size = TmControlSize.LARGE)
        }
    }
}
