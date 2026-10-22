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
import org.mr0btain.ticketman.server.response.LoginLimitsResponse
import org.mr0btain.ticketman.server.response.LoginLock
import org.mr0btain.ticketman.server.response.LoginLockKind
import org.mr0btain.ticketman.components.utils.formatTimeAgo
import org.mr0btain.ticketman.server.response.SessionKind
import org.mr0btain.ticketman.server.response.SessionResponse
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.feedback.TmAccessBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.key
import ticketman.composeapp.generated.resources.lock
import ticketman.composeapp.generated.resources.monitor
import ticketman.composeapp.generated.resources.shield
import ticketman.composeapp.generated.resources.unlock
import ticketman.composeapp.generated.resources.user_x
import kotlin.uuid.Uuid

/**
 * Logged in devices, grouped by user, with a button to log each one out, and the usernames and devices locked out
 * for wrong passwords
 * @param currentSessionId This device's session, logging it out here would just log straight back in
 * @param loginLimits Null when they couldn't be loaded
 */
@Composable
fun AdminSessionsTab(
    sessions: List<SessionResponse>,
    currentUserIdentifier: Uuid?,
    currentSessionId: String?,
    onRevoke: (SessionResponse) -> Unit,
    loginLimits: LoginLimitsResponse? = null,
    onUnlockLogin: (LoginLock) -> Unit = {}
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
                    TmAccessBadge(first.accessLevel)
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

        BlockedLogins(loginLimits, onUnlockLogin)
    }
}

/**
 * Who can't log in with a password for now. Every wrong password is in the audit log too, this is just what's
 * still in effect
 */
@Composable
private fun BlockedLogins(limits: LoginLimitsResponse?, onUnlock: (LoginLock) -> Unit) {
    TmPanel(
        modifier = Modifier.widthIn(max = 900.dp),
        title = "Blocked logins",
        subtitle = "Usernames and devices waiting after too many wrong passwords",
        icon = Res.drawable.shield
    ) {
        if (limits == null) {
            TmNotice(kind = TmNoticeKind.ERROR, text = "Couldn't load the login limits. Refresh to try again.")
            return@TmPanel
        }

        if (limits.surge) {
            TmNotice(
                kind = TmNoticeKind.WARNING,
                title = "Lots of wrong passwords",
                text = "${limits.recentFailures} in the last ${limits.windowMinutes} minutes, from all over. Until that " +
                    "dies down, a device that gets a password wrong waits up to ${limits.windowMinutes} minutes before " +
                    "trying again. Staff who type theirs right aren't affected."
            )
        }

        if (limits.locks.isEmpty()) {
            TmEmptyState(
                icon = Res.drawable.unlock,
                title = "Nobody is blocked",
                message = "${limits.recentFailures} wrong password${if (limits.recentFailures == 1) "" else "s"} in the last ${limits.windowMinutes} minutes"
            )
            return@TmPanel
        }

        Column {
            limits.locks.forEach { lock ->
                val isUser = lock.kind == LoginLockKind.USERNAME

                TmListRow(
                    title = lock.key,
                    subtitle = listOf(
                        if (isUser) "Username" else "Device address",
                        "${lock.failures} wrong passwords",
                        "until ${formatDateTime(lock.lockedUntil)}"
                    ).joinToString(", "),
                    icon = if (isUser) Res.drawable.user_x else Res.drawable.monitor,
                    trailing = {
                        TmButton(
                            text = "Let them try again",
                            variant = TmButtonVariant.SECONDARY,
                            size = TmControlSize.SMALL,
                            onClick = { onUnlock(lock) }
                        )
                    }
                )
            }
        }
    }
}
