package org.mr0btain.ticketman.ui.screens.admin.users

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatTimeAgo
import org.mr0btain.ticketman.components.utils.joinName
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UsernameRules
import org.mr0btain.ticketman.server.request.InviteRequest
import org.mr0btain.ticketman.server.response.InviteResponse
import org.mr0btain.ticketman.server.response.PendingKind
import org.mr0btain.ticketman.server.response.PendingUserResponse
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.admin.components.AccessLevelDropDown
import org.mr0btain.ticketman.ui.screens.admin.components.label
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.user_plus

/**
 * Account requests and invites waiting on someone
 */
@Composable
fun PendingUsersCard(
    pending: List<PendingUserResponse>,
    onInvite: () -> Unit,
    onApprove: (User) -> Unit,
    onReject: (User) -> Unit,
    onRenew: (User) -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    TmPanel(
        modifier = Modifier.widthIn(max = 1000.dp),
        title = "Waiting on someone",
        subtitle = if (pending.isEmpty()) "No account requests or open invites" else "${pending.size} requests and invites",
        icon = Res.drawable.user_plus,
        actions = { TmButton(text = "Invite user", onClick = onInvite, leadingIcon = Res.drawable.user_plus) }
    ) {
        if (pending.isEmpty()) return@TmPanel

        Column {
            pending.forEach { entry ->
                val user = entry.user
                val isRequest = entry.kind == PendingKind.REQUEST

                TmListRow(
                    title = listOf(user.cleanName(), user.username).filter { it.isNotBlank() }.distinct().joinToString(", "),
                    subtitle = if (isRequest) {
                        listOf("Requested ${formatTimeAgo(user.identifier.timestampMillis())}", user.email, user.phone)
                            .filter { it.isNotBlank() }.joinToString(", ")
                    } else {
                        "Invited as ${user.accessLevel.label}" + (entry.inviteExpiresAt?.let { ", code expires ${formatDateTime(it)}" } ?: "")
                    },
                    leading = {
                        TmBadge(
                            modifier = Modifier.width(76.dp),
                            text = if (isRequest) "Request" else "Invite",
                            tone = if (isRequest) colors.warning else colors.neutral
                        )
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            if (isRequest) {
                                TmButton(text = "Approve", onClick = { onApprove(user) }, size = TmControlSize.SMALL)
                                TmButton(text = "Reject", onClick = { onReject(user) }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                            } else {
                                TmButton(text = "New code", onClick = { onRenew(user) }, variant = TmButtonVariant.SECONDARY, size = TmControlSize.SMALL)
                                TmButton(text = "Cancel invite", onClick = { onReject(user) }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Picks the access level for an approved request
 */
@Composable
fun ApproveDialog(
    user: User?,
    onDismiss: () -> Unit,
    onApprove: (AccessLevel) -> Unit
) {
    if (user == null) return

    var accessLevel by remember(user) { mutableStateOf(AccessLevel.RESTRICTED) }

    TmDialog(
        title = "Approve ${user.username}?",
        description = "Make sure this request really came from them. They finish setting up the next time they log in.",
        onDismissRequest = onDismiss,
        maxWidth = 420.dp,
        actions = { TmButtonRow(confirmText = "Approve", onConfirm = { onApprove(accessLevel) }, onCancel = onDismiss) }
    ) {
        AccessLevelDropDown(accessLevel = accessLevel, onSelect = { accessLevel = it })
    }
}

/**
 * Makes an invite for someone. They get a setup code to use in place of a password
 */
@Composable
fun InviteDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onInvite: (InviteRequest) -> Unit
) {
    if (!isVisible) return

    var username by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var accessLevel by remember { mutableStateOf(AccessLevel.RESTRICTED) }

    val problem = UsernameRules.problem(username)

    TmDialog(
        title = "Invite a user",
        description = "They log in with this username and the setup code you'll get next, then choose their own password.",
        onDismissRequest = onDismiss,
        maxWidth = 560.dp,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = "Make invite",
                confirmEnabled = problem == null,
                onCancel = onDismiss,
                onConfirm = {
                    val name = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }

                    onInvite(
                        InviteRequest(
                            username = username,
                            name = joinName(name).lowercase(),
                            email = email.trim(),
                            phone = phone.trim(),
                            accessLevel = accessLevel
                        )
                    )
                }
            )
        }
    ) {
        TmFieldPair(
            {
                TmTextField(
                    modifier = it,
                    value = username,
                    onValueChange = { v -> username = v.trim().lowercase() },
                    label = "Username",
                    error = problem.takeIf { username.isNotEmpty() }
                )
            },
            { AccessLevelDropDown(modifier = it, accessLevel = accessLevel, onSelect = { level -> accessLevel = level }) }
        )
        TmFieldPair(
            { TmTextField(modifier = it, value = firstName, onValueChange = { v -> firstName = v }, label = "First name") },
            { TmTextField(modifier = it, value = lastName, onValueChange = { v -> lastName = v }, label = "Last name") }
        )
        TmFieldPair(
            { TmTextField(modifier = it, value = email, onValueChange = { v -> email = v }, label = "Email") },
            { TmTextField(modifier = it, value = phone, onValueChange = { v -> phone = v }, label = "Phone") }
        )
    }
}

/**
 * Shows a new setup code. It can't be looked up again, only replaced
 */
@Composable
fun InviteCodeDialog(
    invite: InviteResponse?,
    onDismiss: () -> Unit
) {
    if (invite == null) return

    TmDialog(
        title = "Setup code for ${invite.user.username}",
        onDismissRequest = onDismiss,
        maxWidth = 440.dp,
        actions = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TmButton(text = "Done", onClick = onDismiss)
            }
        }
    ) {
        SelectionContainer {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = invite.code.chunked(4).joinToString("-"),
                style = TmTheme.typography.numericHero,
                color = TmTheme.colors.accent.color,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "Give this to ${invite.user.username}. They enter it as their password on the login screen. " +
                "It works once and expires ${formatDateTime(invite.expiresAt)}. It won't be shown again, " +
                "use New code if it gets lost.",
            style = TmTheme.typography.body,
            color = TmTheme.colors.textMuted
        )
    }
}
