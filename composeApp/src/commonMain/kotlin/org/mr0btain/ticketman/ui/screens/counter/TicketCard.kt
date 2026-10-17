package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatDay
import org.mr0btain.ticketman.components.utils.formatQuantity
import org.mr0btain.ticketman.components.utils.formatTimeAgo
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.ui.composables.EmptyMessage
import org.mr0btain.ticketman.ui.composables.SectionCard
import org.mr0btain.ticketman.ui.composables.SimpleTextItem
import org.mr0btain.ticketman.ui.composables.TablePriorityItem
import org.mr0btain.ticketman.ui.composables.TableStatusItem
import org.mr0btain.ticketman.ui.composables.signature.SignatureView
import org.mr0btain.ticketman.ui.screens.counter.components.*

/**
 * Everything about one ticket, with quick status buttons for staff that can make changes
 * @param canEdit Restricted users see the card but none of the buttons that change things
 * @param extraActions Room for buttons added by later screens, like pickup
 */
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ------------------------------------------ Header ------------------------------------------
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }

            Spacer(Modifier.padding(8.dp))

            Text(text = ticket.label, fontSize = 34.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.padding(8.dp))

            TableStatusItem(
                status = ticket.status,
                textStyle = TextStyle.Default.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary)
            )

            TablePriorityItem(
                priority = ticket.priority,
                textStyle = TextStyle.Default.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary)
            )

            Spacer(Modifier.weight(1f))

            // Anyone can print, restricted users included
            printAction()

            Spacer(Modifier.padding(8.dp))

            Text(
                text = "In ${formatTimeAgo(ticket.identifier.timestampMillis()).removeSuffix(" ago")}",
                fontSize = 14.sp
            )
        }

        // ------------------------------------------ Quick actions ------------------------------------------
        if (canEdit && !ticket.isClosed) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TicketStatusType.selectable.forEach { status ->
                    val current = ticket.status.status == status

                    if (current) {
                        Button(onClick = {}, enabled = false) { Text(CounterHandler.statusLabel(status)) }
                    } else {
                        OutlinedButton(onClick = { onStatusChange(status) }) { Text(CounterHandler.statusLabel(status)) }
                    }
                }

                extraActions()
            }
        } else if (!canEdit) {
            Text(modifier = Modifier.padding(horizontal = 12.dp), text = "View only", fontSize = 13.sp)
        }

        // ------------------------------------------ Details ------------------------------------------
        FlowRow {
            SectionCard(modifier = Modifier.width(420.dp), title = "Device") {
                Text(text = ticket.device.toString().ifBlank { "Unknown device" }, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (ticket.device.serial.isNotBlank()) SimpleTextItem("Serial", ticket.device.serial)

                FieldLabel("Problem")
                Text(text = ticket.problem.ifBlank { "-" })

                if (ticket.condition.isNotBlank()) {
                    FieldLabel("Condition")
                    Text(text = ticket.condition)
                }

                if (ticket.accessories.isNotBlank()) {
                    FieldLabel("Left with it")
                    Text(text = ticket.accessories)
                }

                FieldLabel("Dates")
                SimpleTextItem("Checked in", formatDateTime(ticket.identifier.timestampMillis()))
                ticket.promisedAt?.let { promised ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Promised:", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = formatDay(promised) + if (ticket.isOverdue) " (overdue)" else "",
                            fontSize = 16.sp,
                            color = if (ticket.isOverdue) MaterialTheme.colorScheme.error else Color.Unspecified
                        )
                    }
                }
                ticket.pickedUpAt?.let { SimpleTextItem("Picked up", formatDateTime(it)) }
                ticket.estimateCents?.let { SimpleTextItem("Estimate", Money.format(it)) }
            }

            SectionCard(modifier = Modifier.width(360.dp), title = "Customer") {
                val customer = detail.customer

                if (customer == null) {
                    EmptyMessage("No customer on this ticket")
                } else {
                    TextButton(onClick = onCustomerClick, contentPadding = PaddingValues(0.dp)) {
                        Text(text = customer.cleanName(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (customer.phone.isNotBlank()) SimpleTextItem("Phone", customer.phone)
                    if (customer.email.isNotBlank()) SimpleTextItem("Email", customer.email)
                }

                FieldLabel("Technician")
                Text(text = detail.technician?.text?.let { cleanName(it) } ?: "Not assigned")

                FieldLabel("Invoice")
                val invoice = detail.invoice
                if (invoice == null) {
                    Text(text = "None yet")
                } else {
                    SimpleTextItem(invoice.label, invoice.status.name.lowercase().replaceFirstChar { it.titlecase() })

                    invoice.charges.forEach { charge ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "${formatQuantity(charge.quantity)} x ${charge.description}", fontSize = 13.sp)
                            Text(text = Money.format(charge.totalCents), fontSize = 13.sp)
                        }
                    }

                    SimpleTextItem("Total", Money.format(invoice.totalCents))
                    SimpleTextItem("Paid", Money.format(invoice.paidCents))
                    SimpleTextItem("Balance", Money.format(invoice.balanceCents))
                }
            }
        }

        // ------------------------------------------ History and notes ------------------------------------------
        FlowRow {
            SectionCard(modifier = Modifier.width(420.dp), title = "Notes (${detail.notes.size})") {
                if (canEdit && !ticket.isClosed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Add a note") },
                            maxLines = 3
                        )

                        Spacer(Modifier.padding(4.dp))

                        Button(
                            enabled = note.isNotBlank(),
                            onClick = {
                                onAddNote(note)
                                note = ""
                            }
                        ) { Text("Add") }
                    }
                }

                if (detail.notes.isEmpty()) EmptyMessage("No notes")

                detail.notes.reversed().forEach { entry ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(text = entry.author.ifBlank { "Unknown" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = entry.message, fontSize = 14.sp)
                    }
                    HorizontalDivider()
                }
            }

            detail.signature?.let { signature ->
                SectionCard(modifier = Modifier.width(420.dp), title = "Signature") {
                    SignatureView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(signature.aspectRatio.coerceIn(1f, 6f)),
                        strokes = signature.strokes
                    )

                    Text(text = "${signature.signedName}, ${formatDateTime(signature.signedAt)}", fontSize = 13.sp)
                    Text(text = "Taken by ${signature.takenBy}", fontSize = 12.sp)
                }
            }

            SectionCard(modifier = Modifier.width(360.dp), title = "History") {
                if (detail.history.isEmpty()) EmptyMessage("No status changes recorded")

                detail.history.reversed().forEach { change ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = CounterHandler.statusLabel(change.status), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(text = "${change.changedBy} - ${formatDateTime(change.time)}", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
