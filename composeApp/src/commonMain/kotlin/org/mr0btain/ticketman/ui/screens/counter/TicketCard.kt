package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import androidx.compose.foundation.layout.*
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
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.ui.kit.inputs.TmSignatureView
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
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
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.components.*
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.clock
import ticketman.composeapp.generated.resources.edit_3
import ticketman.composeapp.generated.resources.file_text
import ticketman.composeapp.generated.resources.message_square
import ticketman.composeapp.generated.resources.smartphone
import ticketman.composeapp.generated.resources.user

/** Width of the detail panels, they wrap onto new rows on narrow windows */
private val PANEL_WIDTH = 400.dp

/**
 * Everything about one ticket, with quick status buttons for staff that can make changes
 * @param canEdit Restricted users see the card but none of the buttons that change things
 * @param extraActions Room for buttons added by later screens, like pickup
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TicketCard(
    detail: TicketDetail,
    canEdit: Boolean,
    onStatusChange: (TicketStatusType) -> Unit,
    onAddNote: (String) -> Unit,
    onCustomerClick: () -> Unit,
    onBack: () -> Unit,
    printAction: @Composable () -> Unit = {},
    extraActions: @Composable RowScope.() -> Unit = {}
) {
    val ticket = detail.ticket
    var note by remember(ticket.identifier) { mutableStateOf("") }
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
                    TmStatusBadge(ticket.status.status)
                    TmPriorityBadge(ticket.priority.priority)
                    if (ticket.isOverdue) TmBadge(text = "Overdue", tone = colors.danger, solid = true)
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
                TmSegmented(
                    label = "Status",
                    options = TicketStatusType.selectable,
                    selected = ticket.status.status,
                    onSelect = { if (it != ticket.status.status) onStatusChange(it) },
                    optionLabel = { CounterHandler.statusLabel(it) }
                )

                Spacer(Modifier.weight(1f))

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
                                        Text(text = entry.author.ifBlank { "Unknown" }, style = TmTheme.typography.caption, color = colors.textMuted)
                                        Text(text = entry.message, style = TmTheme.typography.body, color = colors.text)
                                    }
                                }
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
                                    // Cash payments keep what was handed over, so paying with more leaves change, not a debt
                                    val balance = invoice.balanceCents
                                    TmKeyValueRow(label = if (balance >= 0) "Balance" else "Change given") {
                                        TmMoneyText(if (balance >= 0) balance else -balance, style = TmTheme.typography.numericLarge)
                                    }
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
                                            Text(text = CounterHandler.statusLabel(change.status), style = TmTheme.typography.label, color = colors.text)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
