package org.mr0btain.ticketman.ui.screens.admin.server

import org.mr0btain.ticketman.components.data.rememberKept
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.ticket.CustomStatus
import org.mr0btain.ticketman.data.ticket.CustomStatusRules
import org.mr0btain.ticketman.data.ticket.StatusTone
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.ticket.byId
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.feedback.TmDot
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.feedback.tone
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmCheckbox
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.tag
import ticketman.composeapp.generated.resources.trash_2

/**
 * The shop's own ticket statuses. Each one counts as a built-in status, which decides where its tickets show.
 * Taking one off retires it on the server rather than deleting it, so tickets in it keep its name
 * @param saved The statuses as the server has them, null while the shop profile couldn't be loaded
 */
@Composable
fun TicketStatusesCard(
    saved: List<CustomStatus>?,
    onSave: (List<CustomStatus>) -> Unit
) {
    val loaded = saved ?: emptyList()
    var statuses by rememberKept("statuses", loaded) { mutableStateOf(loaded.filterNot { it.retired }) }
    val retired = loaded.filter { it.retired && statuses.byId(it.identifier) == null }
    val problem = CustomStatusRules.problem(CustomStatusRules.keepingRemoved(loaded, statuses))

    fun change(status: CustomStatus) {
        statuses = statuses.map { if (it.identifier == status.identifier) status else it }
    }

    TmPanel(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        title = "Ticket statuses",
        subtitle = "Your own steps, like Awaiting parts",
        icon = Res.drawable.tag,
        actions = {
            TmButton(
                text = "Save statuses",
                enabled = saved != null && statuses != loaded.filterNot { it.retired } && problem == null,
                onClick = { onSave(statuses) }
            )
        }
    ) {
        if (saved == null) {
            TmNotice(text = "Couldn't load the shop profile. Check the connection, then refresh.", kind = TmNoticeKind.ERROR)
            return@TmPanel
        }

        if (statuses.isEmpty()) {
            Text(
                text = "Only the built-in statuses so far: In progress, On hold and Ready for pickup.",
                style = TmTheme.typography.body,
                color = TmTheme.colors.textMuted
            )
        }

        statuses.forEachIndexed { index, status ->
            if (index > 0) TmDivider()

            StatusEditor(
                status = status,
                // What a saved status counts as is fixed, tickets were already filed by it
                isNew = loaded.byId(status.identifier) == null,
                onChange = ::change,
                onRemove = { statuses = statuses.filterNot { it.identifier == status.identifier } }
            )
        }

        problem?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }

        TmButton(
            text = "Add status",
            variant = TmButtonVariant.SECONDARY,
            enabled = statuses.size < CustomStatusRules.MAX_STATUSES,
            onClick = { statuses = statuses + CustomStatus(name = "") }
        )

        if (retired.isNotEmpty()) {
            TmDivider()
            Text(text = "Retired", style = TmTheme.typography.label, color = TmTheme.colors.textMuted)

            retired.forEach { status ->
                TmListRow(
                    title = status.name,
                    subtitle = "Tickets already in it keep it. Counts as ${status.actsAs.label.lowercase()}",
                    leading = { TmDot(TmTheme.colors.tone(status.tone ?: status.actsAs.tone).color) },
                    trailing = {
                        TmButton(
                            text = "Bring back",
                            variant = TmButtonVariant.GHOST,
                            onClick = { statuses = statuses + status.copy(retired = false) }
                        )
                    }
                )
            }
        }

        Text(
            text = "Taking a status off retires it: it can't be picked any more, but tickets in it keep its name. " +
                "Customers see a status's name only when it's shared with them, otherwise the built-in one it counts as.",
            style = TmTheme.typography.caption,
            color = TmTheme.colors.textMuted
        )
    }
}

@Composable
private fun StatusEditor(
    status: CustomStatus,
    isNew: Boolean,
    onChange: (CustomStatus) -> Unit,
    onRemove: () -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TmTextField(
                modifier = Modifier.weight(1f),
                value = status.name,
                onValueChange = { onChange(status.copy(name = it.take(CustomStatusRules.MAX_NAME_LENGTH))) },
                label = "Name",
                placeholder = "Awaiting parts"
            )

            TmIconButton(
                icon = Res.drawable.trash_2,
                contentDescription = "Take off ${status.name.ifBlank { "this status" }}",
                onClick = onRemove
            )
        }

        TmFieldPair(
            {
                TmDropdown(
                    modifier = it,
                    label = "Counts as",
                    options = TicketStatusType.selectable,
                    selected = status.actsAs,
                    onSelect = { type -> onChange(status.copy(actsAs = type)) },
                    optionLabel = { type -> type.label },
                    optionLeading = { type -> TmDot(colors.tone(type).color) },
                    enabled = isNew,
                    supportingText = if (isNew) "Can't be changed once saved" else null
                )
            },
            {
                // Wrapped, since the dropdown reads a null selection as nothing picked
                TmDropdown(
                    modifier = it,
                    label = "Color",
                    options = listOf(ToneOption(null)) + StatusTone.entries.map { tone -> ToneOption(tone) },
                    selected = ToneOption(status.tone),
                    onSelect = { option -> onChange(status.copy(tone = option.tone)) },
                    optionLabel = { option -> option.tone?.let { tone -> formatEnumName(tone.name) } ?: "Same as ${status.actsAs.label.lowercase()}" },
                    optionLeading = { option -> TmDot(colors.tone(option.tone ?: status.actsAs.tone).color) }
                )
            }
        )

        TmCheckbox(
            checked = status.showCustomer,
            onCheckedChange = { onChange(status.copy(showCustomer = it)) },
            label = "Customers see this name"
        )
    }
}

/** A color to pick, null for the one of the built-in status it counts as */
private data class ToneOption(val tone: StatusTone?)
