package org.mr0btain.ticketman.ui.screens.admin.users

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.components.data.user.UserSeed
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.composables.CustomSurface
import org.mr0btain.ticketman.ui.composables.SimpleTextItem
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumn
import org.mr0btain.ticketman.ui.composables.shared.PanelTopBar
import org.mr0btain.ticketman.ui.screens.admin.components.UserStateChip
import org.mr0btain.ticketman.ui.screens.admin.components.EmptyMessage
import org.mr0btain.ticketman.ui.screens.admin.components.formatDateTime
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors
import kotlin.uuid.Uuid

/**
 * User management, a list of users with the selected one's details beside it
 * @param currentUserIdentifier The logged in admin, some actions are blocked on yourself
 */
@Composable
fun AdminUsersTab(
    userList: List<User>,
    seed: UserSeed,
    currentUserIdentifier: Uuid?,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    onSelect: (Uuid) -> Unit,
    onSearchQuery: suspend (String) -> List<SearchResult>,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onResetPassword: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onLogOutEverywhere: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        PanelTopBar(
            label = "User",
            onSearchClick = onSelect,
            onSearchQuery = onSearchQuery,
            onAddClick = onAdd,
            onDeleteClick = onDelete
        )

        Spacer(Modifier.padding(4.dp))

        Row(
            modifier = Modifier.sizeIn(maxHeight = 560.dp)
        ) {
            UserListPane(
                userList = userList,
                selected = if (seed.isNew) null else seed.identifier,
                onClick = onSelect,
                state = listState,
                coroutineScope = coroutineScope
            )

            UserInfoPane(
                seed = seed,
                isSelf = seed.identifier == currentUserIdentifier,
                onEdit = onEdit,
                onResetPassword = onResetPassword,
                onSetActive = onSetActive,
                onLogOutEverywhere = onLogOutEverywhere
            )
        }
    }
}

@Composable
private fun UserListPane(
    userList: List<User>,
    selected: Uuid?,
    onClick: (Uuid) -> Unit,
    state: LazyListState,
    coroutineScope: CoroutineScope
) {
    val usernameWidth = 150.dp
    val nameWidth = 170.dp
    val accessWidth = 100.dp
    val emailWidth = 200.dp

    CustomSurface(
        modifier = Modifier
            .wrapContentSize()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.padding(4.dp))
                HeaderText("Username", usernameWidth)
                HeaderText("Name", nameWidth)
                HeaderText("Access", accessWidth)
                HeaderText("Email", emailWidth)
            }

            HorizontalDivider(modifier = Modifier.width(usernameWidth + nameWidth + accessWidth + emailWidth))

            if (userList.isEmpty()) {
                Box(Modifier.width(usernameWidth + nameWidth + accessWidth + emailWidth)) {
                    EmptyMessage("No users loaded")
                }
            }

            PlatformLazyColumn(
                modifier = Modifier
                    .width(usernameWidth + nameWidth + accessWidth + emailWidth + 30.dp)
                    .padding(start = 4.dp),
                state = state,
                scope = coroutineScope,
                isVisible = userList.isNotEmpty()
            ) {
                itemsIndexed(
                    items = userList,
                    key = { _, user -> user.identifier }
                ) { index, user ->
                    Surface(
                        modifier = Modifier
                            .padding(2.dp)
                            .clickable { onClick(user.identifier) },
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 4.dp,
                        color = if (!CustomMaterialColors.darkMode) {
                            if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        } else {
                            if (index % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        },
                        border = if (selected == user.identifier) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            else BorderStroke(0.dp, Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CellText(user.username, usernameWidth)
                            CellText(user.cleanName(), nameWidth)
                            Box(Modifier.width(accessWidth)) { UserStateChip(user) }
                            CellText(user.email, emailWidth)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserInfoPane(
    seed: UserSeed,
    isSelf: Boolean,
    onEdit: () -> Unit,
    onResetPassword: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onLogOutEverywhere: () -> Unit
) {
    CustomSurface(
        modifier = Modifier
            .width(360.dp)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (seed.isNew) {
                EmptyMessage("Select a user to see their details")
                return@Column
            }

            val user = seed.user
            val isActive = user.status == UserStatus.ACTIVE

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = user.cleanName().ifBlank { user.username },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                UserStateChip(user)
            }

            if (isSelf) Text(text = "This is you", fontSize = 12.sp)

            Spacer(Modifier.padding(4.dp))
            HorizontalDivider()
            Spacer(Modifier.padding(4.dp))

            SimpleTextItem("Username", user.username)
            SimpleTextItem("Email", user.email.ifBlank { "-" })
            SimpleTextItem("Phone", user.phone.ifBlank { "-" })
            SimpleTextItem("Added", formatDateTime(seed.time.toEpochMilliseconds()))

            Spacer(Modifier.padding(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onEdit) { Text("Edit") }
                OutlinedButton(onClick = onResetPassword) { Text("Reset Password") }
            }

            Spacer(Modifier.padding(4.dp))

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onLogOutEverywhere
            ) {
                Text("Log Out Everywhere")
            }

            Spacer(Modifier.padding(4.dp))

            // Deactivated users keep their history but can't log in
            if (!isSelf) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSetActive(!isActive) },
                    colors = if (isActive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        else ButtonDefaults.buttonColors()
                ) {
                    Text(if (isActive) "Deactivate" else "Reactivate")
                }
            }
        }
    }
}

@Composable
private fun HeaderText(label: String, width: Dp) {
    Text(
        modifier = Modifier.width(width),
        text = label,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    )
}

@Composable
private fun CellText(text: String, width: Dp) {
    Text(
        modifier = Modifier
            .width(width)
            .padding(end = 8.dp),
        text = text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
