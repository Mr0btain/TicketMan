package org.mr0btain.ticketman.ui.screens.admin.server

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.data.ticket.DeviceKind
import org.mr0btain.ticketman.data.ticket.IntakeChecklist
import org.mr0btain.ticketman.data.ticket.IntakeChecklists
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.x

/**
 * What's checked at check in on each kind of device. A kind uses the built in list until the shop makes its own
 */
@Composable
fun ChecklistsEditor(checklists: List<IntakeChecklist>, onChange: (List<IntakeChecklist>) -> Unit) {
    val spacing = TmTheme.spacing
    var kind by remember { mutableStateOf(DeviceKind.PHONE) }
    val own = checklists.firstOrNull { it.kind == kind }

    fun save(items: List<String>?) =
        onChange(checklists.filter { it.kind != kind } + listOfNotNull(items?.let { IntakeChecklist(kind, it) }))

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "Intake checklists", style = TmTheme.typography.label, color = TmTheme.colors.textMuted)
        Text(
            text = "What gets checked and written on the claim slip when a device comes in, for each kind of device.",
            style = TmTheme.typography.caption,
            color = TmTheme.colors.textMuted
        )

        TmDropdown(label = "Kind of device", options = DeviceKind.entries, selected = kind, onSelect = { kind = it }, optionLabel = { it.label })

        if (own == null) {
            Text(
                text = "Using the built in list: ${IntakeChecklists.defaults[kind].orEmpty().joinToString(", ").ifBlank { "nothing" }}",
                style = TmTheme.typography.body,
                color = TmTheme.colors.text
            )
            TmButton(text = "Make our own", variant = TmButtonVariant.SECONDARY, onClick = { save(IntakeChecklists.defaults[kind].orEmpty()) })
            return@Column
        }

        own.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                TmTextField(
                    modifier = Modifier.weight(1f),
                    value = item,
                    onValueChange = { text -> save(own.items.toMutableList().also { it[index] = text.take(60) }) },
                    placeholder = "Something to check, like Cameras"
                )
                TmIconButton(icon = Res.drawable.x, contentDescription = "Remove $item", onClick = { save(own.items.filterIndexed { i, _ -> i != index }) }, size = TmControlSize.SMALL)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            TmButton(text = "Add a check", variant = TmButtonVariant.SECONDARY, leadingIcon = Res.drawable.plus, onClick = { save(own.items + "") })
            TmButton(text = "Use the built in list", variant = TmButtonVariant.GHOST, onClick = { save(null) })
        }
    }
}
