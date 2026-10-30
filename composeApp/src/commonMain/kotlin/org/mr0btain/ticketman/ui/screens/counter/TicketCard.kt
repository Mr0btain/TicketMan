package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.components.data.rememberKept
import ticketman.composeapp.generated.resources.map_pin
import org.mr0btain.ticketman.ui.kit.selection.TmMenuItem
import org.mr0btain.ticketman.ui.kit.selection.TmMenu
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.ui.kit.feedback.TmLocationBadge
import kotlin.uuid.Uuid
import org.mr0btain.ticketman.data.shop.Storage
import org.mr0btain.ticketman.ui.kit.data.LocalStorageTerms
import org.mr0btain.ticketman.components.utils.formatDate
import kotlin.time.Clock
import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatDay
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.components.utils.formatQuantity
import org.mr0btain.ticketman.components.utils.formatTimeAgo
import org.mr0btain.ticketman.data.ticket.StatusChoice
import org.mr0btain.ticketman.ui.kit.feedback.statusLabel
import org.mr0btain.ticketman.ui.kit.selection.TmStatusPicker
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.ui.kit.inputs.TmSignatureView
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import androidx.compose.ui.text.style.TextOverflow
import org.mr0btain.ticketman.data.estimate.DecisionMethod
import org.mr0btain.ticketman.data.estimate.Estimate
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.notify.OutboxMessage
import org.mr0btain.ticketman.data.notify.OutboxStatus
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.containers.TmScrollColumn
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.feedback.TmPriorityBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmStatusBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmDot
import org.mr0btain.ticketman.ui.kit.feedback.tone
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.components.*
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.clock
import ticketman.composeapp.generated.resources.edit_3
import ticketman.composeapp.generated.resources.file_text
import ticketman.composeapp.generated.resources.globe
import ticketman.composeapp.generated.resources.send
import ticketman.composeapp.generated.resources.clipboard
import ticketman.composeapp.generated.resources.message_square
import ticketman.composeapp.generated.resources.smartphone
import ticketman.composeapp.generated.resources.user

/** Width of the detail panels, they wrap onto new rows on narrow windows */
private val PANEL_WIDTH = 400.dp

/**
 * Everything about one ticket, with quick status buttons for staff that can make changes
 * @param canEdit Restricted users see the card but none of the buttons that change things
 * @param statusLink The customer's status page link, null until the shop has a public address
 * @param onReplaceStatusLink Gives the ticket a new link, cutting off the old one
 * @param estimates Sending estimates and writing down answers, null hides them (view only, or in previews)
 * @param onSendStatusLink Sends the customer their link now, null when there's no link to send
 * @param parts Ordering parts for the ticket, null hides the panel (in previews)
 * @param onOpenTicket Opens another ticket, one checked in with this or an earlier repair of the device
 * @param passcode Showing and changing the device's passcode, null for anyone who can't
 * @param extraActions Room for buttons added by later screens, like pickup
 * @param moveTo The other locations it can be sent to, empty with one location
 * @param onMove Sends it to one of [moveTo]
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TicketCard(
    detail: TicketDetail,
    canEdit: Boolean,
    onStatusChange: (StatusChoice) -> Unit,
    onAddNote: (String) -> Unit,
    onCustomerClick: () -> Unit,
    onBack: () -> Unit,
    statusLink: String? = null,
    onReplaceStatusLink: () -> Unit = {},
    estimates: EstimateActions? = null,
    onSendStatusLink: (() -> Unit)? = null,
    parts: PartsActions? = null,
    onOpenTicket: (Uuid) -> Unit = {},
    passcode: PasscodeActions? = null,
    printAction: @Composable () -> Unit = {},
    extraActions: @Composable RowScope.() -> Unit = {},
    moveTo: List<Location> = emptyList(),
    onMove: (Location) -> Unit = {}
) {
    val ticket = detail.ticket
    var note by rememberKept("ticket-note", ticket.identifier) { mutableStateOf("") }
    var confirmReplace by remember(ticket.identifier) { mutableStateOf(false) }
    var newEstimate by remember(ticket.identifier) { mutableStateOf(false) }
    var answering by remember(ticket.identifier) { mutableStateOf<Estimate?>(null) }
    var confirmWithdraw by remember(ticket.identifier) { mutableStateOf<Estimate?>(null) }
    val changeable = canEdit && !ticket.isClosed
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        // ------------------------------------------ Header ------------------------------------------
        TmPageHeader(
            title = ticket.label,
            subtitle = "${ticket.device.toString().ifBlank { "Unknown device" }}, " +
                    "in ${formatTimeAgo(ticket.identifier.timestampMillis()).removeSuffix(" ago")}",
            onBack = onBack,
            titleContent = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    TmTicketIdText(number = ticket.label, style = TmTheme.typography.numericDisplay)
                    TmStatusBadge(ticket.status.choice)
                    TmPriorityBadge(ticket.priority.priority)
                    TmLocationBadge(ticket.locationIdentifier, always = true)
                    if (ticket.isOverdue) TmBadge(text = "Overdue", tone = colors.danger, solid = true)
                    if (ticket.warrantyOf != null) TmBadge(text = "Warranty return", tone = colors.accent)
                    if (ticket.abandonedAt != null) TmBadge(text = "Abandoned", tone = colors.danger, solid = true)
                }
            },
            // Anyone can print, restricted users included. Staff who can edit get it beside the other actions
            actions = { if (!canEdit || ticket.isClosed) printAction() }
        )

        // ------------------------------------------ Quick actions ------------------------------------------
        if (canEdit && !ticket.isClosed) {
            // Wraps the buttons under the status on narrow windows
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                itemVerticalAlignment = Alignment.Bottom
            ) {
                TmStatusPicker(selected = ticket.status.choice, onSelect = onStatusChange)

                Spacer(Modifier.weight(1f))

                // Off to another of the shop's locations, keeping its number
                if (moveTo.isNotEmpty()) {
                    Box {
                        var moving by remember(ticket.identifier) { mutableStateOf(false) }
                        TmButton(text = "Move", variant = TmButtonVariant.SECONDARY, size = TmControlSize.LARGE, leadingIcon = Res.drawable.map_pin, onClick = { moving = true })
                        TmMenu(expanded = moving, onDismissRequest = { moving = false }) {
                            moveTo.forEach { location ->
                                TmMenuItem(text = "To ${location.name}", onClick = {
                                    moving = false
                                    onMove(location)
                                })
                            }
                        }
                    }
                }

                printAction()
                extraActions()
            }
        } else if (!canEdit) {
            TmBadge(text = "View only", tone = colors.neutral)
        }

        // ------------------------------------------ Details ------------------------------------------
        // Only the panels scroll, so the header and quick actions stay put
        TmScrollColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Two columns of panels when they fit, one under the other when not, never wider than the window
            BoxWithConstraints {
                val panelWidth = min(PANEL_WIDTH, maxWidth)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ------------------------------------------ Device ------------------------------------------
                        TmPanel(modifier = Modifier.width(panelWidth), title = "Device", icon = Res.drawable.smartphone) {
                            Text(text = ticket.device.toString().ifBlank { "Unknown device" }, style = TmTheme.typography.subtitle, color = colors.text)
                            if (ticket.device.serial.isNotBlank()) TmKeyValueRow(label = "Serial", value = ticket.device.serial, numeric = true)

                            DetailBlock("Problem", ticket.problem.ifBlank { "Not written down" })
                            if (ticket.condition.isNotBlank()) DetailBlock("Condition", ticket.condition)
                            if (ticket.accessories.isNotBlank()) DetailBlock("Left with it", ticket.accessories)

                            TmDivider()

                            Column {
                                TmKeyValueRow(label = "Checked in", value = formatDateTime(ticket.identifier.timestampMillis()))
                                ticket.promisedAt?.let { promised ->
                                    TmKeyValueRow(label = "Promised") {
                                        Text(
                                            text = formatDay(promised),
                                            style = TmTheme.typography.body,
                                            color = if (ticket.isOverdue) colors.danger.color else colors.text
                                        )
                                    }
                                }
                                ticket.pickedUpAt?.let { TmKeyValueRow(label = "Picked up", value = formatDateTime(it)) }
                                ticket.estimateCents?.let { TmKeyValueRow(label = "Estimate") { TmMoneyText(it) } }
                                Storage.feeStarts(ticket, LocalStorageTerms.current)?.let { start ->
                                    TmKeyValueRow(label = "Storage from", value = formatDate(start))
                                }
                                ticket.abandonedAt?.let { TmKeyValueRow(label = "Abandoned", value = formatDate(it)) }
                                ticket.warrantyUntil?.let { until ->
                                    val covered = until >= Clock.System.now().toEpochMilliseconds()
                                    TmKeyValueRow(label = if (covered) "Warranty until" else "Warranty ended", value = formatDate(until))
                                }
                            }
                        }

                        // ------------------------------------------ Notes, signature ------------------------------------------
                        TmPanel(
                            modifier = Modifier.width(panelWidth),
                            title = "Notes",
                            subtitle = if (detail.notes.isEmpty()) "None yet" else "${detail.notes.size} notes",
                            icon = Res.drawable.message_square
                        ) {
                            if (canEdit && !ticket.isClosed) {
                                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
                                    TmTextField(
                                        modifier = Modifier.weight(1f),
                                        value = note,
                                        onValueChange = { note = it },
                                        placeholder = "Add a note",
                                        singleLine = false
                                    )

                                    TmButton(
                                        text = "Add",
                                        enabled = note.isNotBlank(),
                                        variant = TmButtonVariant.SECONDARY,
                                        onClick = {
                                            onAddNote(note)
                                            note = ""
                                        }
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                detail.notes.reversed().forEach { entry ->
                                    Column {
                                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = entry.author.ifBlank { "Unknown" }, style = TmTheme.typography.caption, color = colors.textMuted)
                                            if (entry.customerVisible) TmBadge(text = "Customer sees", tone = colors.accent, dot = false)
                                        }
                                        Text(text = entry.message, style = TmTheme.typography.body, color = colors.text)
                                    }
                                }
                            }
                        }

                        // ------------------------------------------ Messages ------------------------------------------
                        TmPanel(
                            modifier = Modifier.width(panelWidth),
                            title = "Messages",
                            subtitle = detail.messages?.let { if (it.isEmpty()) "None sent" else if (it.size == 1) "1 message" else "${it.size} messages" } ?: "Couldn't load",
                            icon = Res.drawable.send
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                detail.messages.orEmpty().take(MESSAGES_SHOWN).forEach { message -> MessageRow(message) }
                            }

                            if (onSendStatusLink != null && changeable) {
                                TmButton(text = "Send status link", onClick = onSendStatusLink, variant = TmButtonVariant.SECONDARY, size = TmControlSize.SMALL)
                            }
                        }

                        detail.signature?.let { signature ->
                            TmPanel(
                                modifier = Modifier.width(panelWidth),
                                title = "Signature",
                                subtitle = "${signature.signedName}, ${formatDateTime(signature.signedAt)}",
                                icon = Res.drawable.edit_3
                            ) {
                                TmSignatureView(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(signature.aspectRatio.coerceIn(1f, 6f)),
                                    strokes = signature.strokes
                                )

                                Text(text = "Taken by ${signature.takenBy}", style = TmTheme.typography.caption, color = colors.textMuted)
                            }
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ------------------------------------------ Customer ------------------------------------------
                        TmPanel(modifier = Modifier.width(panelWidth), title = "Customer", icon = Res.drawable.user) {
                            val customer = detail.customer

                            if (customer == null) {
                                Text(text = "No customer on this ticket", style = TmTheme.typography.body, color = colors.textMuted)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(modifier = Modifier.weight(1f), text = customer.name, style = TmTheme.typography.subtitle, color = colors.text)
                                    TmButton(text = "Open", onClick = onCustomerClick, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                                }
                                Column {
                                    if (customer.phone.isNotBlank()) TmKeyValueRow(label = "Phone", value = customer.phone)
                                    if (customer.email.isNotBlank()) TmKeyValueRow(label = "Email", value = customer.email)
                                }
                            }

                            TmKeyValueRow(label = "Technician", value = detail.technician?.text ?: "Not assigned")
                        }

                        // ------------------------------------------ Status page ------------------------------------------
                        TmPanel(
                            modifier = Modifier.width(panelWidth),
                            title = "Status page",
                            subtitle = detail.notes.count { it.customerVisible }.let { shared ->
                                when (shared) {
                                    0 -> "No updates shared yet"
                                    1 -> "1 update shared"
                                    else -> "$shared updates shared"
                                }
                            },
                            icon = Res.drawable.globe
                        ) {
                            if (statusLink == null) {
                                Text(
                                    text = "Set the shop's public address in Admin, Server to give customers a link to follow this repair",
                                    style = TmTheme.typography.body,
                                    color = colors.textMuted
                                )
                            } else {
                                // Selectable so it can be copied into a text or email by hand
                                SelectionContainer {
                                    Text(text = statusLink, style = TmTheme.typography.body, color = colors.text)
                                }
                                Text(
                                    text = "On the claim slip's QR. Customers see the statuses you share, notes marked for them and the balance",
                                    style = TmTheme.typography.caption,
                                    color = colors.textMuted
                                )
                                if (canEdit) {
                                    TmButton(text = "New link", onClick = { confirmReplace = true }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                                }
                            }
                        }

                        // ------------------------------------------ Estimate ------------------------------------------
                        if (estimates != null) {
                            EstimatePanel(
                                modifier = Modifier.width(panelWidth),
                                detail = detail,
                                canChange = changeable,
                                onNew = { newEstimate = true },
                                onAnswer = { answering = it },
                                onWithdraw = { confirmWithdraw = it }
                            )
                        }

                        // ------------------------------------------ Parts ------------------------------------------
                        if (parts != null) {
                            PartsPanel(
                                modifier = Modifier.width(panelWidth),
                                ticket = ticket.identifier,
                                orders = detail.purchases,
                                canOrder = changeable,
                                actions = parts
                            )
                        }

                        // ------------------------------------------ Invoice ------------------------------------------
                        TmPanel(modifier = Modifier.width(panelWidth), title = "Invoice", icon = Res.drawable.file_text) {
                            val invoice = detail.invoice

                            if (invoice == null) {
                                Text(text = "No invoice yet, one is made at check in or pickup", style = TmTheme.typography.body, color = colors.textMuted)
                            } else {
                                TmKeyValueRow(label = invoice.label) {
                                    TmBadge(text = formatEnumName(invoice.status.name), tone = colors.neutral, dot = false)
                                }

                                Column {
                                    invoice.charges.forEach { charge ->
                                        TmKeyValueRow(label = "${formatQuantity(charge.quantity)} x ${charge.description}") { TmMoneyText(charge.totalCents) }
                                    }
                                }

                                TmDivider()

                                Column {
                                    TmKeyValueRow(label = "Total") { TmMoneyText(invoice.totalCents) }
                                    TmKeyValueRow(label = "Paid") { TmMoneyText(invoice.paidCents) }
                                    if (invoice.refundedCents > 0) TmKeyValueRow(label = "Refunded") { TmMoneyText(invoice.refundedCents) }
                                    // Cash payments keep what was handed over, so paying with more leaves change, not a debt
                                    val balance = invoice.balanceCents
                                    TmKeyValueRow(label = if (balance >= 0) "Balance" else "Change given") {
                                        TmMoneyText(if (balance >= 0) balance else -balance, style = TmTheme.typography.numericLarge)
                                    }
                                }
                            }
                        }

                        // ------------------------------------------ Intake ------------------------------------------
                        if (ticket.checklist.isNotEmpty() || ticket.passcodeSet || (passcode != null && !ticket.isClosed)) {
                            IntakePanel(modifier = Modifier.width(panelWidth), ticket = ticket, passcode = passcode?.takeIf { !ticket.isClosed })
                        }

                        // ------------------------------------------ Visit and device ------------------------------------------
                        if (detail.linked.isNotEmpty() || detail.deviceHistory.isNotEmpty()) {
                            TmPanel(modifier = Modifier.width(panelWidth), title = "Related repairs", icon = deviceIcon(ticket.device.kind)) {
                                if (detail.linked.isNotEmpty()) {
                                    Text(text = "Checked in with it", style = TmTheme.typography.label, color = colors.textMuted)
                                    Column { detail.linked.forEach { TicketRow(it, onClick = { onOpenTicket(it.identifier) }) } }
                                }
                                if (detail.deviceHistory.isNotEmpty()) {
                                    Text(text = "This device before", style = TmTheme.typography.label, color = colors.textMuted)
                                    Column { detail.deviceHistory.forEach { TicketRow(it, onClick = { onOpenTicket(it.identifier) }) } }
                                }
                            }
                        }

                        // ------------------------------------------ History ------------------------------------------
                        TmPanel(modifier = Modifier.width(panelWidth), title = "History", icon = Res.drawable.clock) {
                            if (detail.history.isEmpty()) {
                                Text(text = "No status changes recorded", style = TmTheme.typography.body, color = colors.textMuted)
                            }

                            Column {
                                detail.history.reversed().forEach { change ->
                                    TmKeyValueRow(label = "${change.changedBy}, ${formatDateTime(change.time)}") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                            TmDot(colors.tone(change.status).color)
                                            Text(text = statusLabel(change.choice), style = TmTheme.typography.label, color = colors.text)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (estimates != null && newEstimate) {
            EstimateDialog(
                start = detail.pendingEstimate?.lines ?: detail.estimates?.firstOrNull()?.lines.orEmpty(),
                laborCents = estimates.laborCents,
                taxRateBasisPoints = estimates.taxRateBasisPoints,
                canNotify = true,
                onSearchParts = estimates.onSearchParts,
                onSend = { lines, note, notify -> estimates.onSend(lines, note, notify) { newEstimate = false } },
                onDismiss = { newEstimate = false }
            )
        }

        answering?.let { estimate ->
            EstimateAnswerDialog(
                estimate = estimate,
                onRecord = { approved, method, note ->
                    answering = null
                    estimates?.onAnswer(estimate, approved, method, note)
                },
                onDismiss = { answering = null }
            )
        }

        confirmWithdraw?.let { estimate ->
            TmConfirmDialog(
                title = "Withdraw the estimate?",
                message = "The customer won't be able to approve it any more.",
                confirmText = "Withdraw",
                destructive = true,
                onConfirm = {
                    confirmWithdraw = null
                    estimates?.onWithdraw(estimate)
                },
                onDismissRequest = { confirmWithdraw = null }
            )
        }

        if (confirmReplace) {
            TmConfirmDialog(
                title = "Give ${ticket.label} a new link?",
                message = "The link on the claim slip, and any you've sent, will stop working. Print a new slip or send the new link.",
                confirmText = "New link",
                onConfirm = {
                    confirmReplace = false
                    onReplaceStatusLink()
                },
                onDismissRequest = { confirmReplace = false }
            )
        }
    }
}

/**
 * What the ticket card needs to send estimates and record answers
 * @param onSend Sends it, then calls the last lambda when it went
 */
class EstimateActions(
    val laborCents: Long,
    val taxRateBasisPoints: Int,
    val onSearchParts: suspend (String) -> List<Inventory>,
    val onSend: (lines: List<Charge>, note: String, notify: Boolean, onSent: () -> Unit) -> Unit,
    val onAnswer: (Estimate, approved: Boolean, DecisionMethod, note: String) -> Unit,
    val onWithdraw: (Estimate) -> Unit
)

/** Messages shown on the card, the rest are on the admin screen */
private const val MESSAGES_SHOWN = 5

@Composable
private fun MessageRow(message: OutboxMessage) {
    val colors = TmTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = "${message.event.label}, ${message.channel.label.lowercase()}, ${formatDateTime(message.createdAt)}",
                style = TmTheme.typography.caption,
                color = colors.textMuted
            )
            TmBadge(text = message.status.label, tone = colors.tone(message.status), dot = false)
        }
        Text(text = message.body, style = TmTheme.typography.body, color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (message.status == OutboxStatus.FAILED || (message.status == OutboxStatus.PENDING && message.lastError.isNotBlank())) {
            Text(text = message.lastError, style = TmTheme.typography.caption, color = colors.danger.color)
        }
    }
}

/** The estimate waiting for the customer with its lines, or how the last one went */
@Composable
private fun EstimatePanel(
    modifier: Modifier,
    detail: TicketDetail,
    canChange: Boolean,
    onNew: () -> Unit,
    onAnswer: (Estimate) -> Unit,
    onWithdraw: (Estimate) -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    val pending = detail.pendingEstimate
    val latest = detail.estimates?.firstOrNull()

    TmPanel(
        modifier = modifier,
        title = "Estimate",
        subtitle = when {
            detail.estimates == null -> "Couldn't load"
            latest == null -> "None sent"
            else -> latest.status.label
        },
        icon = Res.drawable.clipboard
    ) {
        latest?.let { estimate ->
            Column {
                estimate.lines.forEach { line ->
                    TmKeyValueRow(label = "${formatQuantity(line.quantity)} x ${line.description}") { TmMoneyText(line.totalCents) }
                }
                TmKeyValueRow(label = "Total") { TmMoneyText(estimate.totalCents, style = TmTheme.typography.numericLarge) }
            }

            if (estimate.note.isNotBlank()) Text(text = estimate.note, style = TmTheme.typography.body, color = colors.text)

            val decision = estimate.decision
            Text(
                text = if (decision != null) {
                    val who = decision.signedName.ifBlank { decision.recordedBy }
                    "${if (decision.approved) "Approved" else "Declined"} by $who, ${decision.method.label.lowercase()}, ${formatDateTime(decision.decidedAt)}" +
                        decision.note.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                } else {
                    "Sent by ${estimate.createdBy}, ${formatDateTime(estimate.createdAt)}"
                },
                style = TmTheme.typography.caption,
                color = colors.textMuted
            )
        }

        if (canChange) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (pending != null) {
                    TmButton(text = "Record answer", onClick = { onAnswer(pending) }, variant = TmButtonVariant.SECONDARY, size = TmControlSize.SMALL)
                    TmButton(text = "Withdraw", onClick = { onWithdraw(pending) }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                }
                TmButton(text = if (pending != null) "Change it" else "New estimate", onClick = onNew, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
            }
        }
    }
}

/** A label with a paragraph under it, for longer ticket fields */
@Composable
private fun DetailBlock(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
        Text(text = label, style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
        Text(text = text, style = TmTheme.typography.body, color = TmTheme.colors.text)
    }
}
