package org.mr0btain.ticketman.ui.screens.admin.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.server.response.SessionKind
import org.mr0btain.ticketman.server.response.SessionResponse
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatTimeAgo
import org.mr0btain.ticketman.ui.composables.SectionCard
import org.mr0btain.ticketman.ui.composables.EmptyMessage
import org.mr0btain.ticketman.ui.screens.admin.components.*
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        SectionCard(
            modifier = Modifier.widthIn(max = 900.dp),
            title = "Logged In Devices (${sessions.size})"
        ) {
            Text(
                text = "Each device a user logs in on has its own session. Logging one out doesn't affect their other devices.",
                fontSize = 13.sp
            )

            Spacer(Modifier.padding(4.dp))

            if (sessions.isEmpty()) {
                EmptyMessage("Nobody is logged in")
            }

            byUser.values.forEach { userSessions ->
                val first = userSessions.first()
                val isSelf = first.userIdentifier == currentUserIdentifier

                Row(
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = first.username + if (isSelf) " (you)" else "",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Spacer(Modifier.padding(4.dp))

                    AccessLevelChip(first.accessLevel)
                }

                userSessions.forEach { session ->
                    val isThisDevice = session.sessionId == currentSessionId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = session.deviceName.ifBlank { "Unnamed device" } + if (isThisDevice) " (this device)" else "",
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = listOfNotNull(
                                    if (session.kind == SessionKind.PIN) "PIN" else "Password",
                                    session.createdAt?.let { "logged in ${formatTimeAgo(it)}" },
                                    session.lastUsedAt?.let { "last active ${formatTimeAgo(it)}" },
                                    session.expiresAt?.let { "expires ${formatDateTime(it)}" }
                                ).joinToString(" - "),
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            enabled = !isThisDevice,
                            onClick = { onRevoke(session) }
                        ) {
                            Text("Log Out")
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(top = 4.dp))
            }
        }
    }
}
