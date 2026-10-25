package org.mr0btain.ticketman.ui.screens.admin.users

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.components.data.user.UserSeed
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.server.response.PendingUserResponse
import org.mr0btain.ticketman.ui.kit.navigation.TmPanelTopBar
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.images.TmImageView
import org.mr0btain.ticketman.ui.kit.notes.TmEditNoteDialog
import org.mr0btain.ticketman.ui.kit.notes.TmNoteBlock
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.admin.components.UserStateChip
import org.mr0btain.ticketman.ui.screens.records.components.DetailSection
import org.mr0btain.ticketman.ui.screens.records.components.RecordDetail
import org.mr0btain.ticketman.ui.screens.records.components.RecordsLayout
import org.mr0btain.ticketman.ui.screens.records.components.NARROW_LIST_WIDTH
import org.mr0btain.ticketman.ui.screens.records.components.RecordNameCell
import org.mr0btain.ticketman.ui.screens.records.components.RecordsList
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.user
import ticketman.composeapp.generated.resources.users
import org.mr0btain.ticketman.ui.kit.images.TmAvatar
import org.mr0btain.ticketman.ui.kit.images.TmPhotoEditor
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import ticketman.composeapp.generated.resources.printer
import kotlin.uuid.Uuid

private val NARROW_COLUMNS = listOf(
    TmColumn("User"),
    TmColumn("Access", width = 120.dp, end = true)
)

private val COLUMNS = listOf(
    TmColumn("Username", weight = 1f),
    TmColumn("Name", weight = 1.1f),
    TmColumn("Access", width = 120.dp),
    TmColumn("Email", weight = 1.3f)
)

/**
 * User management, a list of users with the selected one's details beside it
 * @param currentUserIdentifier The logged in admin, some actions are blocked on yourself
 * @param onSaveNote Adds or edits a staff note on the selected user
 * @param onRemoveNote Deletes a staff note on the selected user
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
    onSaveNote: (Note) -> Unit,
    onRemoveNote: (Uuid) -> Unit,
    onDelete: () -> Unit,
    onSetPhoto: (StoredImage?) -> Unit = {},
    onPrintIdCard: () -> Unit = {},
    onNewBadge: () -> Unit = {}
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
                    TmPanelTopBar(
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
                        onResetPin = onResetPin,
                        onSaveNote = onSaveNote,
                        onRemoveNote = onRemoveNote,
                        onSetPhoto = onSetPhoto,
                        onPrintIdCard = onPrintIdCard,
                        onNewBadge = onNewBadge
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

    BoxWithConstraints {
        // Narrow: the avatar, the name with the username under it, and their access
        val narrow = maxWidth < NARROW_LIST_WIDTH
        val columns = if (narrow) NARROW_COLUMNS else COLUMNS

        RecordsList(
            columns = columns,
            state = state,
            isEmpty = userList.isEmpty(),
            emptyTitle = "No users loaded",
            emptyIcon = Res.drawable.users
        ) {
            items(userList, key = { it.identifier }) { user ->
                TmTableRow(columns, selected = selected == user.identifier, onClick = { onClick(user.identifier) }) { column ->
                    when (if (narrow) listOf(0, 2)[column] else column) {
                        0 -> Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            TmAvatar(name = user.name.ifBlank { user.username }, photo = user.photo, size = if (narrow) 32.dp else 24.dp)
                            if (narrow) RecordNameCell(user.name.ifBlank { user.username }, user.username)
                            else Text(user.username, style = TmTheme.typography.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        1 -> Text(user.name, style = TmTheme.typography.body, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    onResetPin: () -> Unit,
    onSaveNote: (Note) -> Unit,
    onRemoveNote: (Uuid) -> Unit,
    onSetPhoto: (StoredImage?) -> Unit,
    onPrintIdCard: () -> Unit,
    onNewBadge: () -> Unit
) {
    val user = seed.user
    val isActive = user.status == UserStatus.ACTIVE

    // The seed doesn't carry PIN state, the listed copy does
    val hasPin = listed?.hasPin == true
    val pinLocked = listed?.pinLocked == true
    val photo = listed?.photo ?: user.photo
    val hasBadge = listed?.hasBadge == true
    var confirmNewBadge by remember { mutableStateOf(false) }

    if (confirmNewBadge) {
        TmConfirmDialog(
            title = "New card code for ${user.username}?",
            message = "Their printed ID card stops working. Print them a new one afterwards.",
            confirmText = "Make new code",
            destructive = true,
            onConfirm = {
                onNewBadge()
                confirmNewBadge = false
            },
            onDismissRequest = { confirmNewBadge = false }
        )
    }

    RecordDetail(
        title = user.name.ifBlank { user.username },
        subtitle = if (isSelf) "This is you" else user.username,
        icon = Res.drawable.user,
        isEmpty = seed.isNew,
        emptyTitle = "No user picked",
        onEdit = onEdit
    ) {
        UserStateChip(user)

        DetailSection("Details") {
            Column {
                TmKeyValueRow(label = "Username", value = user.username)
                TmKeyValueRow(label = "Email", value = user.email.ifBlank { "None" })
                TmKeyValueRow(label = "Phone", value = user.phone.ifBlank { "None" })
                TmKeyValueRow(label = "Added", value = formatDateTime(seed.time.toEpochMilliseconds()))
                TmKeyValueRow(
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

        DetailSection("Photo and ID card") {
            Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    TmAvatar(name = user.name.ifBlank { user.username }, photo = photo, size = 72.dp)
                    TmPhotoEditor(
                        modifier = Modifier.weight(1f),
                        images = listOfNotNull(photo),
                        onAdd = { onSetPhoto(it) },
                        onRemove = { onSetPhoto(null) },
                        buttonText = if (photo == null) "Add photo" else "Change photo"
                    )
                }

                Text(
                    text = if (hasBadge) "Has an ID card. Its QR will clock them in once that's built."
                        else "No ID card yet, printing one makes its code.",
                    style = TmTheme.typography.caption,
                    color = TmTheme.colors.textMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                    TmButton(text = "Print ID card", onClick = onPrintIdCard, variant = TmButtonVariant.SECONDARY, leadingIcon = Res.drawable.printer)
                    if (hasBadge) TmButton(text = "New card code", onClick = { confirmNewBadge = true }, variant = TmButtonVariant.SECONDARY)
                }
            }
        }

        StaffNotes(notes = seed.notes, onSave = onSaveNote, onRemove = onRemoveNote)
    }
}

/**
 * Notes about a member of staff, like training or when they work. Only admins see them
 */
@Composable
private fun StaffNotes(notes: List<Note>, onSave: (Note) -> Unit, onRemove: (Uuid) -> Unit) {
    val noteSeed = remember { NoteSeed() }
    var editing by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Pair<List<StoredImage>, Int>?>(null) }

    DetailSection("Staff notes") {
        Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
            TmButton(
                text = "Add note",
                leadingIcon = Res.drawable.plus,
                variant = TmButtonVariant.SECONDARY,
                size = TmControlSize.SMALL,
                onClick = {
                    noteSeed.clear()
                    editing = true
                }
            )

            TmNoteBlock(
                notes = notes,
                editable = true,
                emptyText = "No notes yet",
                onEditClick = { note ->
                    noteSeed.setNote(note)
                    editing = true
                },
                onImageClick = { noteIdentifier, page ->
                    notes.firstOrNull { it.identifier == noteIdentifier }?.let { viewing = it.images to page }
                }
            )
        }
    }

    TmEditNoteDialog(
        seed = noteSeed,
        label = if (noteSeed.isNew) "Add staff note" else "Edit staff note",
        isVisible = editing,
        onConfirm = { onSave(noteSeed.note) },
        onDelete = { onRemove(noteSeed.identifier) },
        onDismissRequest = { editing = false }
    )

    TmImageView(
        images = viewing?.first.orEmpty(),
        startPage = viewing?.second ?: 0,
        isVisible = viewing != null,
        onDismiss = { viewing = null }
    )
}
