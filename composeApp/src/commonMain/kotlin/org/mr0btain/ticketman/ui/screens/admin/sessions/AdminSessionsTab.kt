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
import org.mr0btain.ticketman.server.response.SessionResponse
import org.mr0btain.ticketman.ui.screens.admin.components.*
import kotlin.uuid.Uuid

/**
 * Who is logged in, with a button to log each of them out
 */
@Composable
fun AdminSessionsTab(
    sessions: List<SessionResponse>,
    currentUserIdentifier: Uuid?,
    onRevoke: (SessionResponse) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        SectionCard(
            modifier = Modifier.widthIn(max = 900.dp),
            title = "Logged In Users (${sessions.size})"
        ) {
            Text(
                text = "Each user has one session. Logging someone out stops their app working until they log in again.",
                fontSize = 13.sp
            )

            Spacer(Modifier.padding(4.dp))

            if (sessions.isEmpty()) {
                EmptyMessage("Nobody is logged in")
            }

            sessions.forEach { session ->
                val isSelf = session.userIdentifier == currentUserIdentifier

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = session.username + if (isSelf) " (you)" else "",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = session.issuedAt?.let { "Logged in ${formatTimeAgo(it)} (${formatDateTime(it)})" } ?: "Login time unknown",
                            fontSize = 12.sp
                        )

                        session.expiresAt?.let {
                            Text(text = "Expires ${formatDateTime(it)}", fontSize = 12.sp)
                        }
                    }

                    AccessLevelChip(session.accessLevel)

                    Spacer(Modifier.padding(8.dp))

                    // Logging yourself out here would just get you logged straight back in, use Log Out instead
                    OutlinedButton(
                        enabled = !isSelf,
                        onClick = { onRevoke(session) }
                    ) {
                        Text("Log Out")
                    }
                }

                HorizontalDivider()
            }
        }
    }
}
