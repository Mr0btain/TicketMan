package org.mr0btain.ticketman.ui.screens.kiosk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.station.StaffMember
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmSurface
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.components.cleanName
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.users

/**
 * The kiosk's signed out screen: pick your name, type your PIN
 * @param onUnlock Signs someone in, calls back with what went wrong or null if it worked
 * @param onExit Leaves kiosk mode with an admin's PIN, calls back the same way
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockScreen(
    stationName: String,
    staff: List<StaffMember>,
    onRefresh: () -> Unit,
    onUnlock: (StaffMember, String, (String?) -> Unit) -> Unit,
    onExit: (StaffMember, String, (String?) -> Unit) -> Unit
) {
    var selected by remember { mutableStateOf<StaffMember?>(null) }
    var exiting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    LaunchedEffect(Unit) { onRefresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        Spacer(Modifier.height(spacing.xxl))

        Text(text = stationName, style = TmTheme.typography.label, color = colors.textMuted)
        Text(
            text = when {
                selected == null && exiting -> "Admin, pick your name to leave kiosk mode"
                selected == null -> "Who's at the counter?"
                else -> "Hi ${cleanName(selected!!.name).ifBlank { selected!!.username }}, enter your PIN"
            },
            style = TmTheme.typography.display,
            color = colors.text,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(spacing.xl))

        val member = selected

        if (member == null) {
            if (staff.isEmpty()) {
                TmEmptyState(
                    icon = Res.drawable.users,
                    title = "Nobody can sign in here yet",
                    message = "Staff set a PIN from Settings, then show up here.",
                    actionText = "Check again",
                    onAction = onRefresh
                )
            }

            FlowRow(
                modifier = Modifier.widthIn(max = 900.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                staff.forEach { person ->
                    TmSurface(
                        modifier = Modifier.size(width = 200.dp, height = 88.dp),
                        onClick = {
                            selected = person
                            error = null
                        }
                    ) {
                        Text(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(horizontal = spacing.lg),
                            text = cleanName(person.name).ifBlank { person.username },
                            style = TmTheme.typography.title,
                            color = colors.text
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.xl))

            TmButton(
                text = if (exiting) "Never mind" else "Leave kiosk mode",
                onClick = { exiting = !exiting },
                variant = TmButtonVariant.GHOST
            )
        } else {
            PinPad(
                error = error,
                busy = busy,
                onSubmit = { pin ->
                    busy = true
                    error = null

                    val done: (String?) -> Unit = { problem ->
                        busy = false
                        error = problem
                        if (problem == null) {
                            selected = null
                            exiting = false
                        }
                    }

                    if (exiting) onExit(member, pin, done) else onUnlock(member, pin, done)
                }
            )

            Spacer(Modifier.height(spacing.lg))

            TmButton(
                text = "Not me",
                onClick = {
                    selected = null
                    error = null
                },
                variant = TmButtonVariant.SECONDARY,
                size = TmControlSize.LARGE
            )
        }
    }
}
