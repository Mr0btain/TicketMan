package org.mr0btain.ticketman.ui.screens.admin.users

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.components.utils.joinName
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UsernameRules
import org.mr0btain.ticketman.server.request.InviteRequest
import org.mr0btain.ticketman.server.response.InviteResponse
import org.mr0btain.ticketman.server.response.PendingKind
import org.mr0btain.ticketman.server.response.PendingUserResponse
import org.mr0btain.ticketman.ui.composables.ConfirmCancelButtons
import org.mr0btain.ticketman.ui.composables.DialogTextRow
import org.mr0btain.ticketman.ui.screens.admin.components.*
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors

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
    SectionCard(
        modifier = Modifier.widthIn(max = 1000.dp),
        title = "Pending (${pending.size})",
        action = {
            Button(onClick = onInvite) { Text("Invite User") }
        }
    ) {
        if (pending.isEmpty()) {
            EmptyMessage("No account requests or open invites")
            return@SectionCard
        }

        pending.forEach { entry ->
            val user = entry.user

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KindChip(entry.kind)

                Spacer(Modifier.padding(4.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = listOf(user.cleanName(), user.username).filter { it.isNotBlank() }.distinct().joinToString(" - "),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = when (entry.kind) {
                            PendingKind.REQUEST -> listOf(
                                "Requested ${formatTimeAgo(user.identifier.timestampMillis())}",
                                user.email,
                                user.phone
                            ).filter { it.isNotBlank() }.joinToString(" - ")

                            PendingKind.INVITE -> "Invited as ${user.accessLevel.label}" +
                                (entry.inviteExpiresAt?.let { " - code expires ${formatDateTime(it)}" } ?: "")
                        },
                        fontSize = 12.sp
                    )
                }

                when (entry.kind) {
                    PendingKind.REQUEST -> {
                        Button(onClick = { onApprove(user) }) { Text("Approve") }
                        Spacer(Modifier.padding(4.dp))
                        OutlinedButton(onClick = { onReject(user) }) { Text("Reject") }
                    }

                    PendingKind.INVITE -> {
                        OutlinedButton(onClick = { onRenew(user) }) { Text("New Code") }
                        Spacer(Modifier.padding(4.dp))
                        OutlinedButton(onClick = { onReject(user) }) { Text("Cancel Invite") }
                    }
                }
            }

            HorizontalDivider()
        }
    }
}

@Composable
private fun KindChip(kind: PendingKind) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (kind == PendingKind.REQUEST) CustomMaterialColors.inProgress else MaterialTheme.colorScheme.secondary
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            text = if (kind == PendingKind.REQUEST) "Request" else "Invite",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary
        )
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(10.dp), shadowElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(380.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Approve ${user.username}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

                Text(
                    modifier = Modifier.padding(vertical = 8.dp),
                    text = "Make sure this request really came from them. They finish setting up the next time they log in.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                AccessLevelDropDown(
                    accessLevel = accessLevel,
                    onSelect = { accessLevel = it }
                )

                Spacer(Modifier.padding(8.dp))

                ConfirmCancelButtons(
                    onCancelClick = onDismiss,
                    onConfirmClick = { onApprove(accessLevel) }
                )
            }
        }
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(10.dp), shadowElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Invite User", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

                Text(
                    modifier = Modifier.padding(vertical = 8.dp),
                    text = "They log in with this username and the setup code you'll get next, then choose their own password.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                DialogTextRow(
                    firstLabel = "Username",
                    firstValue = username,
                    onFirstValueChange = { username = it.trim().lowercase() },
                    secondLabel = "Email",
                    secondValue = email,
                    onSecondValueChange = { email = it }
                )

                DialogTextRow(
                    firstLabel = "First Name",
                    firstValue = firstName,
                    onFirstValueChange = { firstName = it },
                    secondLabel = "Last Name",
                    secondValue = lastName,
                    onSecondValueChange = { lastName = it }
                )

                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(0.5f),
                        label = { Text("Phone") },
                        value = phone,
                        onValueChange = { phone = it },
                        singleLine = true
                    )

                    Spacer(Modifier.padding(8.dp))

                    AccessLevelDropDown(
                        modifier = Modifier.weight(0.5f),
                        accessLevel = accessLevel,
                        onSelect = { accessLevel = it }
                    )
                }

                if (problem != null && username.isNotEmpty()) {
                    Text(text = problem, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.padding(8.dp))

                ConfirmCancelButtons(
                    onCancelClick = onDismiss,
                    onConfirmClick = {
                        if (problem == null) {
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
                    }
                )
            }
        }
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(10.dp), shadowElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(420.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Setup Code for ${invite.user.username}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

                SelectionContainer {
                    Text(
                        modifier = Modifier.padding(vertical = 16.dp),
                        text = invite.code.chunked(4).joinToString("-"),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "Give this to ${invite.user.username}. They enter it as their password on the login screen. " +
                        "It works once and expires ${formatDateTime(invite.expiresAt)}. It won't be shown again, " +
                        "use New Code if it gets lost.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.padding(8.dp))

                Button(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}
