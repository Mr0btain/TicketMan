package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.components.utils.formatQuantity
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.*
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.ui.composables.EmptyMessage
import org.mr0btain.ticketman.ui.composables.SectionCard
import org.mr0btain.ticketman.ui.composables.SimpleTextItem
import org.mr0btain.ticketman.ui.screens.counter.components.TicketDetail
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors
import kotlin.uuid.Uuid

/** A line being edited, kept as text so half typed numbers don't get thrown away */
private class ChargeDraft(charge: Charge) {
    val identifier: Uuid = charge.identifier
    val type: ChargeType = charge.type
    val inventoryIdentifier: Uuid? = charge.inventoryIdentifier

    var description by mutableStateOf(charge.description)
    var quantity by mutableStateOf(formatQuantity(charge.quantity))
    var price by mutableStateOf(Money.format(charge.unitPriceCents).removePrefix("$"))
    var taxable by mutableStateOf(charge.taxable)

    /** Null while something typed isn't a number yet */
    fun toCharge(): Charge? {
        val amount = quantity.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val unitPrice = Money.parse(price)?.takeIf { it >= 0 } ?: return null

        return Charge(identifier, type, description.trim(), amount, unitPrice, inventoryIdentifier, taxable)
    }
}

/**
 * Builds the invoice, takes payment and hands the ticket back
 * @param onFinish Saves the lines if they changed, takes [PaymentRequest]s and finishes, calls back with what went
 * wrong or null if it worked
 */
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

    // Rebuilt when the server sends back new lines, but not when only the payments changed so edits aren't lost
    val drafts = remember(invoice.identifier, invoice.charges) { mutableStateListOf<ChargeDraft>().apply { addAll(invoice.charges.map { ChargeDraft(it) }) } }
    var discount by remember(invoice.identifier, invoice.discountCents) { mutableStateOf(if (invoice.discountCents > 0) Money.format(invoice.discountCents).removePrefix("$") else "") }
    val pending = remember(ticket.identifier) { mutableStateListOf<PaymentRequest>() }
    var paymentAmount by remember(ticket.identifier) { mutableStateOf("") }
    var paymentMethod by remember(ticket.identifier) { mutableStateOf(PaymentMethod.CARD) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val charges = drafts.map { it.toCharge() }
    val validCharges = charges.filterNotNull()
    val chargesProblem = when {
        charges.any { it == null } -> "Fix the quantity or price on each line"
        validCharges.any { it.description.isBlank() } -> "Every line needs a description"
        discount.isNotBlank() && Money.parse(discount) == null -> "Discount isn't an amount"
        else -> null
    }
    val discountCents = Money.parse(discount)?.coerceAtLeast(0) ?: 0

    // Preview with the same math the server uses
    val totals = InvoiceMath.totals(validCharges, discountCents, invoice.taxRateBasisPoints)
    val paid = invoice.paidCents + pending.sumOf { it.amountCents }
    val balance = totals.totalCents - paid
    val unsaved = chargesProblem == null && (validCharges != invoice.charges || discountCents != invoice.discountCents)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text(text = "Pickup ${ticket.label}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(text = invoice.label, fontSize = 16.sp)
            detail.customer?.let { Text(text = it.cleanName(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }

        FlowRow {
            // ------------------------------------------ Lines ------------------------------------------
            SectionCard(modifier = Modifier.width(720.dp), title = "Invoice") {
                if (drafts.isEmpty()) EmptyMessage("No charges yet, add labor or parts below")

                drafts.forEach { draft ->
                    ChargeRow(draft, onRemove = { drafts.remove(draft) })
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        drafts.add(ChargeDraft(Charge(type = ChargeType.LABOR, description = "Labor", unitPriceCents = ticket.estimateCents ?: 0)))
                    }) { Text("Add Labor") }

                    OutlinedButton(onClick = {
                        drafts.add(ChargeDraft(Charge(type = ChargeType.OTHER, description = "")))
                    }) { Text("Add Other") }
                }

                PartPicker(onSearch = onSearchParts) { part ->
                    drafts.add(
                        ChargeDraft(
                            Charge(type = ChargeType.PART, description = part.name, unitPriceCents = part.priceCents, inventoryIdentifier = part.identifier)
                        )
                    )
                }

                OutlinedTextField(
                    modifier = Modifier.width(200.dp).padding(top = 8.dp),
                    value = discount,
                    onValueChange = { discount = it },
                    label = { Text("Discount ($)") },
                    singleLine = true
                )
            }

            // ------------------------------------------ Totals and payment ------------------------------------------
            SectionCard(modifier = Modifier.width(380.dp), title = "Payment") {
                SimpleTextItem("Subtotal", Money.format(totals.subtotalCents))
                if (totals.discountCents > 0) SimpleTextItem("Discount", "-${Money.format(totals.discountCents)}")
                SimpleTextItem("Tax (${invoice.taxRateBasisPoints / 100.0}%)", Money.format(totals.taxCents))
                Text(
                    modifier = Modifier.padding(vertical = 4.dp),
                    text = "Total ${Money.format(totals.totalCents)}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider()

                // Deposits and anything else already taken
                invoice.payments.forEach { payment ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "${Money.format(payment.amountCents)} ${formatEnumName(payment.method.name).lowercase()}" +
                                (payment.note.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""),
                            fontSize = 14.sp
                        )
                        Text(text = formatDateTime(payment.receivedAt), fontSize = 11.sp)
                        TextButton(onClick = { onRemovePayment(payment) }) { Text("Remove") }
                    }
                }

                // Taken now, sent with the pickup
                pending.forEach { payment ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "${Money.format(payment.amountCents)} ${formatEnumName(payment.method.name).lowercase()} (now)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = { pending.remove(payment) }) { Text("Remove") }
                    }
                }

                Text(
                    modifier = Modifier.padding(vertical = 6.dp),
                    text = if (balance >= 0) "Balance due ${Money.format(balance)}" else "Change due ${Money.format(-balance)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (balance > 0) MaterialTheme.colorScheme.error else CustomMaterialColors.success
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PaymentMethod.entries.forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(formatEnumName(method.name)) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = paymentAmount,
                        onValueChange = { paymentAmount = it },
                        label = { Text("Amount ($)") },
                        placeholder = { if (balance > 0) Text(Money.format(balance).removePrefix("$")) },
                        singleLine = true
                    )

                    Spacer(Modifier.padding(4.dp))

                    // A blank amount takes whatever is still owed
                    val amount = if (paymentAmount.isBlank()) balance.takeIf { it > 0 } else Money.parse(paymentAmount)?.takeIf { it > 0 }

                    Button(
                        enabled = amount != null,
                        onClick = {
                            amount?.let { pending.add(PaymentRequest(paymentMethod, it)) }
                            paymentAmount = ""
                        }
                    ) { Text("Take") }
                }
            }
        }

        (message ?: chargesProblem)?.let {
            Text(modifier = Modifier.padding(horizontal = 16.dp), text = it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }

        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                enabled = unsaved && !busy,
                onClick = {
                    busy = true
                    message = null
                    onSave(validCharges, discountCents) { problem ->
                        busy = false
                        message = problem
                    }
                }
            ) { Text("Save Invoice") }

            Button(
                enabled = chargesProblem == null && balance <= 0 && !busy,
                onClick = {
                    busy = true
                    message = null
                    onFinish(validCharges, discountCents, pending.toList()) { problem ->
                        busy = false
                        message = problem
                    }
                }
            ) { Text("Finish Pickup") }
        }
    }
}

@Composable
private fun ChargeRow(draft: ChargeDraft, onRemove: () -> Unit) {
    val charge = draft.toCharge()

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(modifier = Modifier.width(56.dp), text = formatEnumName(draft.type.name), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = draft.description,
            onValueChange = { draft.description = it },
            label = { Text("Description") },
            singleLine = true,
            // Part names come from inventory
            readOnly = draft.type == ChargeType.PART
        )

        OutlinedTextField(
            modifier = Modifier.width(90.dp).padding(start = 4.dp),
            value = draft.quantity,
            onValueChange = { draft.quantity = it },
            label = { Text(if (draft.type == ChargeType.LABOR) "Hours" else "Qty") },
            isError = draft.quantity.toDoubleOrNull()?.let { it <= 0 } ?: true,
            singleLine = true
        )

        OutlinedTextField(
            modifier = Modifier.width(110.dp).padding(start = 4.dp),
            value = draft.price,
            onValueChange = { draft.price = it },
            label = { Text("Each ($)") },
            isError = Money.parse(draft.price) == null,
            singleLine = true
        )

        Checkbox(checked = draft.taxable, onCheckedChange = { draft.taxable = it })
        Text(text = "Tax", fontSize = 12.sp)

        Text(
            modifier = Modifier.width(80.dp),
            text = charge?.let { Money.format(it.totalCents) } ?: "-",
            fontWeight = FontWeight.SemiBold
        )

        TextButton(onClick = onRemove) { Text("X") }
    }
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

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        value = query,
        onValueChange = { query = it },
        label = { Text("Add a part from inventory") },
        singleLine = true
    )

    matches.forEach { part ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onPick(part)
                query = ""
                matches = emptyList()
            },
            color = Color.Transparent
        ) {
            Row(Modifier.padding(6.dp)) {
                Text(modifier = Modifier.weight(1f), text = part.name, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (part.stock == Int.MAX_VALUE) "" else "${part.stock} in stock  ",
                    fontSize = 12.sp,
                    color = if (part.stock <= 0) MaterialTheme.colorScheme.error else Color.Unspecified
                )
                Text(text = Money.format(part.priceCents))
            }
        }
    }
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
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Picked up", fontSize = 22.sp)
        Text(text = invoice.label, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(text = "Total ${Money.format(invoice.totalCents)}  -  Paid ${Money.format(invoice.paidCents)}", fontSize = 18.sp)

        if (invoice.balanceCents < 0) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = "Change due ${Money.format(-invoice.balanceCents)}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = CustomMaterialColors.success
            )
        }

        Spacer(Modifier.padding(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            actions()
            Button(onClick = onDone) { Text("Done") }
        }
    }
}
