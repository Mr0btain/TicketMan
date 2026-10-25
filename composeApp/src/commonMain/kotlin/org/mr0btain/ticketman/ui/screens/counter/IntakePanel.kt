package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.data.ticket.CheckResult
import org.mr0btain.ticketman.data.ticket.IntakeChecklists
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmPasswordField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.check_square

/**
 * What the ticket card needs for the passcode
 * @param onShow Fetches it, which the server writes down; gives it back, or what went wrong as a failure
 * @param onSet Saves a new one, calls back with what went wrong or null
 */
class PasscodeActions(
    val onShow: suspend () -> Result<String>,
    val onSet: (String, (String?) -> Unit) -> Unit,
    val onClear: () -> Unit
)

/**
 * What was checked when it came in, and the passcode, shown only when asked for and hidden again on its own
 * @param passcode Null for anyone who can't see passcodes
 */
@Composable
fun IntakePanel(modifier: Modifier, ticket: Ticket, passcode: PasscodeActions?) {
    val colors = TmTheme.colors
    val scope = rememberCoroutineScope()
    var shown by remember(ticket.identifier, ticket.passcodeSet) { mutableStateOf<String?>(null) }
    var problem by remember(ticket.identifier) { mutableStateOf<String?>(null) }
    var setting by remember(ticket.identifier) { mutableStateOf(false) }

    if (setting && passcode != null) PasscodeDialog(onSet = passcode.onSet, onDismiss = { setting = false })

    TmPanel(modifier = modifier, title = "Intake", icon = Res.drawable.check_square) {
        if (ticket.checklist.isEmpty()) {
            Text("Nothing was checked at check in", style = TmTheme.typography.body, color = colors.textMuted)
        }

        Column {
            ticket.checklist.forEach { item ->
                TmKeyValueRow(label = item.name) {
                    TmBadge(
                        text = item.result.label,
                        tone = when (item.result) {
                            CheckResult.WORKS -> colors.success
                            CheckResult.FAULTY -> colors.danger
                            CheckResult.NOT_TESTED -> colors.neutral
                        },
                        dot = false
                    )
                }
            }
        }

        TmKeyValueRow(label = "Passcode") {
            Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                when {
                    shown != null -> SelectionContainer { Text(shown!!, style = TmTheme.typography.numericLarge, color = colors.text) }
                    ticket.passcodeSet -> Text("On file", style = TmTheme.typography.body, color = colors.textMuted)
                    else -> Text("None", style = TmTheme.typography.body, color = colors.textMuted)
                }

                if (passcode != null && ticket.passcodeSet) {
                    if (shown == null) {
                        TmButton(text = "Show", variant = TmButtonVariant.SECONDARY, size = TmControlSize.SMALL, onClick = {
                            scope.launch {
                                passcode.onShow()
                                    .onSuccess {
                                        shown = it
                                        problem = null
                                        // Hidden again after a minute, so it isn't left up on the screen
                                        kotlinx.coroutines.delay(SHOW_MILLIS)
                                        shown = null
                                    }
                                    .onFailure { problem = it.message }
                            }
                        })
                    } else {
                        TmButton(text = "Hide", variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL, onClick = { shown = null })
                    }
                }
            }
        }

        if (passcode != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                TmButton(text = if (ticket.passcodeSet) "Change" else "Add passcode", variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL, onClick = { setting = true })
                if (ticket.passcodeSet) TmButton(text = "Clear", variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL, onClick = passcode.onClear)
            }
            Text("Each look is in the audit log. It's cleared at pickup", style = TmTheme.typography.caption, color = colors.textMuted)
        }

        problem?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
    }
}

@Composable
private fun PasscodeDialog(onSet: (String, (String?) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    TmDialog(
        title = "Device passcode",
        description = "Kept encrypted on the server, shown only to staff who can edit.",
        onDismissRequest = onDismiss,
        actions = {
            TmButtonRow(
                confirmText = "Save passcode",
                confirmEnabled = value.isNotBlank(),
                loading = busy,
                onCancel = onDismiss,
                onConfirm = {
                    busy = true
                    error = null
                    onSet(value.trim()) { failed ->
                        busy = false
                        error = failed
                        if (failed == null) onDismiss()
                    }
                }
            )
        }
    ) {
        TmPasswordField(value = value, onValueChange = { value = it.take(IntakeChecklists.MAX_PASSCODE) }, label = "Passcode or pattern")
        error?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
    }
}

private const val SHOW_MILLIS = 60_000L
