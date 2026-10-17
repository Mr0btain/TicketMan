package org.mr0btain.ticketman.ui.screens.admin.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatTimeAgo
import org.mr0btain.ticketman.server.response.SessionKind
import org.mr0btain.ticketman.server.response.SessionResponse
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.feedback.AccessBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.key
import ticketman.composeapp.generated.resources.lock
import ticketman.composeapp.generated.resources.monitor
import kotlin.uuid.Uuid

/**
 * Logged in devices, grouped by user, with a button to log each one out
 * @param currentSessionId This device's session, logging it out here would just log straight back in
 */
@Composable
fun AdminSessionsTab(
    sessions: List<SessionResponse>,
    currentUserIdentifier: Uuid?,
    currentSessionId: String?,
    onRevoke: (SessionResponse) -> Unit
) {
    val byUser = sessions.groupBy { it.userIdentifier }
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        TmPanel(
            modifier = Modifier.widthIn(max = 900.dp),
            title = "Logged in devices",
            subtitle = "Each device has its own session, logging one out leaves the user's other devices alone",
            icon = Res.drawable.monitor
        ) {
            if (sessions.isEmpty()) {
                TmEmptyState(icon = Res.drawable.lock, title = "Nobody is logged in")
            }

            byUser.values.forEach { userSessions ->
                val first = userSessions.first()
                val isSelf = first.userIdentifier == currentUserIdentifier

                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = first.username + if (isSelf) " (you)" else "", style = TmTheme.typography.subtitle, color = colors.text)
                    AccessBadge(first.accessLevel)
                }

                Column {
                    userSessions.forEach { session ->
                        val isThisDevice = session.sessionId == currentSessionId

                        TmListRow(
                            title = session.deviceName.ifBlank { "Unnamed device" } + if (isThisDevice) " (this device)" else "",
                            subtitle = listOfNotNull(
                                if (session.kind == SessionKind.PIN) "PIN" else "Password",
                                session.createdAt?.let { "logged in ${formatTimeAgo(it)}" },
                                session.lastUsedAt?.let { "last active ${formatTimeAgo(it)}" },
                                session.expiresAt?.let { "expires ${formatDateTime(it)}" }
                            ).joinToString(", "),
                            icon = if (session.kind == SessionKind.PIN) Res.drawable.key else Res.drawable.monitor,
                            trailing = {
                                TmButton(
                                    text = "Log out",
                                    enabled = !isThisDevice,
                                    variant = TmButtonVariant.SECONDARY,
                                    size = TmControlSize.SMALL,
                                    onClick = { onRevoke(session) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
