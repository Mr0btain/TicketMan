package org.mr0btain.ticketman.ui.screens.admin.users

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.data.user.UserSeed
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.server.response.PendingUserResponse
import org.mr0btain.ticketman.ui.screens.shared.PanelTopBar
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.data.KeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.admin.components.UserStateChip
import org.mr0btain.ticketman.ui.screens.dashboard.components.DetailSection
import org.mr0btain.ticketman.ui.screens.dashboard.components.RecordDetail
import org.mr0btain.ticketman.ui.screens.dashboard.components.RecordsLayout
import org.mr0btain.ticketman.ui.screens.dashboard.components.RecordsList
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.user
import ticketman.composeapp.generated.resources.users
import kotlin.uuid.Uuid

private val COLUMNS = listOf(
    TmColumn("Username", weight = 1f),
    TmColumn("Name", weight = 1.1f),
    TmColumn("Access", width = 120.dp),
    TmColumn("Email", weight = 1.3f)
)

/**
 * User management, a list of users with the selected one's details beside it
 * @param currentUserIdentifier The logged in admin, some actions are blocked on yourself
 */
@Composable
fun AdminUsersTab(
    pending: List<PendingUserResponse>,
    onInvite: () -> Unit,
    onApprove: (User) -> Unit,
    onReject: (User) -> Unit,
    onRenewInvite: (User) -> Unit,
    userList: List<User>,
    seed: UserSeed,
    pendingId: Uuid? = null,
    currentUserIdentifier: Uuid?,
    listState: LazyListState,
    onSelect: (Uuid) -> Unit,
    onSearchQuery: suspend (String) -> List<SearchResult>,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onResetPassword: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onLogOutEverywhere: () -> Unit,
    onUnlockPin: () -> Unit,
    onResetPin: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)
    ) {
        PendingUsersCard(
            pending = pending,
            onInvite = onInvite,
            onApprove = onApprove,
            onReject = onReject,
            onRenew = onRenewInvite
        )

        Box(Modifier.weight(1f)) {
            RecordsLayout(
                toolbar = {
                    PanelTopBar(
                        label = "User",
                        onSearchClick = onSelect,
                        onSearchQuery = onSearchQuery,
                        onAddClick = onAdd,
                        onDeleteClick = onDelete,
                        canDelete = !seed.isNew
                    )
                },
                list = {
                    UserListPane(
                        userList = userList,
                        selected = pendingId ?: if (seed.isNew) null else seed.identifier,
                        onClick = onSelect,
                        state = listState
                    )
                },
                detail = {
                    UserInfoPane(
                        seed = seed,
                        listed = userList.firstOrNull { it.identifier == seed.identifier },
                        isSelf = seed.identifier == currentUserIdentifier,
                        onEdit = onEdit,
                        onResetPassword = onResetPassword,
                        onSetActive = onSetActive,
                        onLogOutEverywhere = onLogOutEverywhere,
                        onUnlockPin = onUnlockPin,
                        onResetPin = onResetPin
                    )
                }
            )
        }
    }
}

@Composable
private fun UserListPane(
    userList: List<User>,
    selected: Uuid?,
    onClick: (Uuid) -> Unit,
    state: LazyListState
) {
    val colors = TmTheme.colors

    RecordsList(
        columns = COLUMNS,
        state = state,
        isEmpty = userList.isEmpty(),
        emptyTitle = "No users loaded",
        emptyIcon = Res.drawable.users
    ) {
        items(userList, key = { it.identifier }) { user ->
            TmTableRow(COLUMNS, selected = selected == user.identifier, onClick = { onClick(user.identifier) }) { column ->
                when (column) {
                    0 -> Text(user.username, style = TmTheme.typography.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    1 -> Text(user.cleanName(), style = TmTheme.typography.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    2 -> Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        UserStateChip(user)
                        if (user.pinLocked) TmBadge(text = "PIN", tone = colors.danger, dot = false)
                    }
                    3 -> Text(user.email, style = TmTheme.typography.body, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun UserInfoPane(
    seed: UserSeed,
    listed: User?,
    isSelf: Boolean,
    onEdit: () -> Unit,
    onResetPassword: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onLogOutEverywhere: () -> Unit,
    onUnlockPin: () -> Unit,
    onResetPin: () -> Unit
) {
    val user = seed.user
    val isActive = user.status == UserStatus.ACTIVE

    // The seed doesn't carry PIN state, the listed copy does
    val hasPin = listed?.hasPin == true
    val pinLocked = listed?.pinLocked == true

    RecordDetail(
        title = user.cleanName().ifBlank { user.username },
        subtitle = if (isSelf) "This is you" else user.username,
        icon = Res.drawable.user,
        isEmpty = seed.isNew,
        emptyTitle = "No user picked",
        onEdit = onEdit
    ) {
        UserStateChip(user)

        DetailSection("Details") {
            Column {
                KeyValueRow(label = "Username", value = user.username)
                KeyValueRow(label = "Email", value = user.email.ifBlank { "None" })
                KeyValueRow(label = "Phone", value = user.phone.ifBlank { "None" })
                KeyValueRow(label = "Added", value = formatDateTime(seed.time.toEpochMilliseconds()))
                KeyValueRow(
                    label = "PIN",
                    value = when {
                        pinLocked -> "Locked after too many tries"
                        hasPin -> "Set"
                        else -> "Not set"
                    }
                )
            }
        }

        DetailSection("Access") {
            Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                    TmButton(text = "Reset password", onClick = onResetPassword, variant = TmButtonVariant.SECONDARY)
                    TmButton(text = "Log out everywhere", onClick = onLogOutEverywhere, variant = TmButtonVariant.SECONDARY)
                }

                if (pinLocked || hasPin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                        if (pinLocked) TmButton(text = "Unlock PIN", onClick = onUnlockPin)
                        if (hasPin) TmButton(text = "Clear PIN", onClick = onResetPin, variant = TmButtonVariant.SECONDARY)
                    }
                }

                // Deactivated users keep their history but can't log in
                if (!isSelf) {
                    TmButton(
                        text = if (isActive) "Deactivate user" else "Reactivate user",
                        onClick = { onSetActive(!isActive) },
                        variant = if (isActive) TmButtonVariant.DANGER else TmButtonVariant.PRIMARY
                    )
                }
            }
        }
    }
}
