package org.mr0btain.ticketman.ui.screens.records.components.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.inventory.LocationStock
import org.mr0btain.ticketman.data.inventory.StockTransfer
import org.mr0btain.ticketman.data.inventory.StockTransferRequest
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmSectionLabel
import org.mr0btain.ticketman.ui.kit.feedback.LocalLocations
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import kotlin.uuid.Uuid

/**
 * Moves an item's parts from one location to another, counted down at one and up at the other together, with its
 * recent moves below
 * @param stockAt The item's count at each location
 * @param history Its moves, newest first, null while they load or when they couldn't be read
 * @param onMove Moves them, calling back with what went wrong or null once moved
 */
@Composable
fun StockTransferDialog(
    itemName: String,
    stockAt: List<LocationStock>,
    history: List<StockTransfer>?,
    onMove: (StockTransferRequest, (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit
) {
    val spacing = TmTheme.spacing
    val colors = TmTheme.colors
    val locations = LocalLocations.current
    val live = locations.all.filter { !it.retired }
    fun countAt(location: Location?) = location?.let { stockAt.firstOrNull { it.locationIdentifier == location.identifier }?.stock } ?: 0
    fun name(identifier: Uuid) = locations.all.firstOrNull { it.identifier == identifier }?.name ?: "a removed location"

    // From where the most are, unless that's here: a part is usually fetched to where it's needed
    val here = live.firstOrNull { it.identifier == locations.working }
    var from by remember { mutableStateOf(live.filter { it != here }.maxByOrNull { countAt(it) } ?: live.firstOrNull()) }
    var to by remember { mutableStateOf(here?.takeIf { it != from } ?: live.firstOrNull { it != from }) }
    var quantity by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val count = quantity.trim().toIntOrNull()
    val request = if (from != null && to != null && count != null) StockTransferRequest(from!!.identifier, to!!.identifier, count, note.trim()) else null
    val problem = when {
        from == null || to == null -> "Pick where from and where to"
        count == null -> "Enter how many"
        count > countAt(from) -> "Only ${countAt(from)} at ${from!!.name}"
        else -> request?.problem()
    }

    TmDialog(
        title = "Move stock",
        description = "$itemName from one location to another",
        onDismissRequest = onDismissRequest,
        maxWidth = 520.dp,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                TmButton(text = "Cancel", variant = TmButtonVariant.SECONDARY, onClick = onDismissRequest)
                TmButton(
                    text = "Move",
                    enabled = problem == null && request != null,
                    loading = saving,
                    onClick = {
                        val moving = request ?: return@TmButton
                        saving = true
                        error = null
                        onMove(moving) { failed ->
                            saving = false
                            error = failed
                        }
                    }
                )
            }
        }
    ) {
        TmFieldPair(
            {
                TmDropdown(
                    modifier = it,
                    label = "From",
                    options = live,
                    selected = from,
                    onSelect = { picked -> if (picked == to) to = from; from = picked },
                    optionLabel = { location -> "${location.name} (${countAt(location)})" }
                )
            },
            {
                TmDropdown(
                    modifier = it,
                    label = "To",
                    options = live,
                    selected = to,
                    onSelect = { picked -> if (picked == from) from = to; to = picked },
                    optionLabel = { location -> "${location.name} (${countAt(location)})" }
                )
            }
        )
        TmFieldPair(
            {
                TmTextField(
                    modifier = it,
                    value = quantity,
                    onValueChange = { v -> quantity = v.filter { c -> c.isDigit() }.take(6) },
                    label = "How many",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            { TmTextField(modifier = it, value = note, onValueChange = { v -> note = v }, label = "Note", placeholder = "For ticket 26-00000042") }
        )

        error?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
            ?: problem?.takeIf { count != null }?.let { TmNotice(text = it, kind = TmNoticeKind.WARNING) }

        if (!history.isNullOrEmpty()) {
            TmSectionLabel(text = "Recent moves")
            history.take(RECENT).forEach { move ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = move.quantity.toString(), style = TmTheme.typography.numeric, color = colors.text, modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(text = "${name(move.fromLocation)} to ${name(move.toLocation)}", style = TmTheme.typography.label, color = colors.text)
                        Text(
                            text = listOfNotNull(formatDateTime(move.movedAt), move.movedBy.ifBlank { null }, move.note.ifBlank { null }).joinToString(", "),
                            style = TmTheme.typography.caption,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }
    }
}

private const val RECENT = 8
