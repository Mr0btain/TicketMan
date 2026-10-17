package org.mr0btain.ticketman.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.components.pagination.offset.offsetPagerHandler
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.dialog.SimpleConfirmDeleteDialog
import org.mr0btain.ticketman.ui.screens.admin.audit.AdminAuditTab
import org.mr0btain.ticketman.ui.screens.admin.inventory.AdminLowStockTab
import org.mr0btain.ticketman.ui.screens.admin.overview.AdminOverviewTab
import org.mr0btain.ticketman.ui.screens.admin.server.AdminServerTab
import org.mr0btain.ticketman.ui.screens.admin.sessions.AdminSessionsTab
import org.mr0btain.ticketman.ui.screens.admin.stations.AdminStationsTab
import org.mr0btain.ticketman.server.response.InviteResponse
import org.mr0btain.ticketman.ui.screens.admin.users.AdminUsersTab
import org.mr0btain.ticketman.ui.screens.admin.users.ApproveDialog
import org.mr0btain.ticketman.ui.screens.admin.users.InviteCodeDialog
import org.mr0btain.ticketman.ui.screens.admin.users.InviteDialog
import org.mr0btain.ticketman.ui.screens.admin.users.ResetPasswordDialog
import org.mr0btain.ticketman.ui.screens.admin.users.UserDialog
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors

private enum class AdminTab(val label: String) {
    OVERVIEW("Overview"),
    USERS("Users"),
    SESSIONS("Sessions"),
    STATIONS("Stations"),
    LOW_STOCK("Low Stock"),
    SERVER("Server & Data"),
    AUDIT("Audit Log")
}

/**
 * Admin dashboard. Only usable by admins, the server enforces this as well
 * @param onNavigateToMain Switches to the main screen, used after picking a ticket or item to look at
 */
@Composable
fun AdminScreen(
    viewModel: MainViewModel,
    snackBarState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    onNavigateToMain: () -> Unit
) {
    val isAdmin by viewModel.isAdmin.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (CustomMaterialColors.darkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background
    ) {
        if (isAdmin) {
            AdminContent(viewModel, coroutineScope, onNavigateToMain)
        } else {
            NotAdminMessage(viewModel)
        }
    }
}

@Composable
private fun AdminContent(
    viewModel: MainViewModel,
    coroutineScope: CoroutineScope,
    onNavigateToMain: () -> Unit
) {
    val admin = viewModel.admin

    var selectedTab by rememberSaveable { mutableStateOf(AdminTab.OVERVIEW) }

    val isLoading by admin.isLoading.collectAsState()
    val stats by admin.stats.collectAsState()
    val lowStock by admin.lowStock.collectAsState()
    val lowStockThreshold by admin.lowStockThreshold.collectAsState()
    val sessions by admin.sessions.collectAsState()
    val pending by admin.pending.collectAsState()
    val stations by admin.stations.collectAsState()
    val serverInfo by admin.serverInfo.collectAsState()
    val shopProfile by admin.shopProfile.collectAsState()
    val auditEntries by admin.auditEntries.collectAsState()
    val auditEndReached by admin.auditEndReached.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // Users list uses the same pager as everywhere else
    val userSeed = viewModel.userSeed
    val users = viewModel.userPager?.items?.collectAsState()
    val userListState = rememberLazyListState()
    offsetPagerHandler(viewModel.userPager, userListState)

    // User dialogs
    var showUserDialog by remember { mutableStateOf(false) }
    var isNewUser by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // New account dialogs
    var showInviteDialog by remember { mutableStateOf(false) }
    var approving by remember { mutableStateOf<User?>(null) }
    var shownInvite by remember { mutableStateOf<InviteResponse?>(null) }

    InviteDialog(
        isVisible = showInviteDialog,
        onDismiss = { showInviteDialog = false },
        onInvite = { request ->
            admin.invite(request) { invite ->
                showInviteDialog = false
                shownInvite = invite
            }
        }
    )

    ApproveDialog(
        user = approving,
        onDismiss = { approving = null },
        onApprove = { level -> approving?.let { user -> admin.approve(user, level) { approving = null } } }
    )

    InviteCodeDialog(
        invite = shownInvite,
        onDismiss = { shownInvite = null }
    )

    // Fresh numbers whenever the screen is opened
    LaunchedEffect(Unit) {
        admin.refreshAll()
        viewModel.userPager?.refresh()
    }

    UserDialog(
        isVisible = showUserDialog,
        user = if (isNewUser) remember(showUserDialog) { User() } else userSeed.user,
        isNew = isNewUser,
        isSelf = !isNewUser && userSeed.identifier == currentUser?.identifier,
        onDismiss = { showUserDialog = false },
        onConfirm = { user, password ->
            admin.saveUser(user, isNewUser, password, onSuccess = {
                showUserDialog = false
                viewModel.setSelectedUser(user.identifier, force = true)
            })
        }
    )

    ResetPasswordDialog(
        isVisible = showResetPasswordDialog,
        user = userSeed.user,
        onDismiss = { showResetPasswordDialog = false },
        onConfirm = { password ->
            admin.resetPassword(userSeed.user, password, onSuccess = { showResetPasswordDialog = false })
        }
    )

    if (showDeleteDialog) {
        SimpleConfirmDeleteDialog(
            itemName = userSeed.username,
            isDarkMode = CustomMaterialColors.darkMode,
            onDismissRequest = { confirmed ->
                if (confirmed) admin.deleteUser(userSeed.user)
                showDeleteDialog = false
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // ------------------------------------------ Tabs ------------------------------------------
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp, start = 4.dp),
                containerColor = Color.Transparent,
                edgePadding = 0.dp
            ) {
                AdminTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(24.dp),
                    strokeWidth = 3.dp
                )
            }

            Button(
                modifier = Modifier.padding(end = 8.dp),
                enabled = !isLoading,
                onClick = {
                    admin.refreshAll()
                    viewModel.refreshPagers()
                }
            ) {
                Text("Refresh")
            }
        }

        HorizontalDivider()

        // ------------------------------------------ Content ------------------------------------------
        when (selectedTab) {
            AdminTab.OVERVIEW -> AdminOverviewTab(
                stats = stats,
                onTicketClick = { identifier ->
                    viewModel.setSelectedTicket(identifier)
                    viewModel.changeMainTabIndex(TICKET_TAB)
                    onNavigateToMain()
                },
                onLowStockClick = { selectedTab = AdminTab.LOW_STOCK }
            )

            AdminTab.USERS -> AdminUsersTab(
                pending = pending,
                onInvite = { showInviteDialog = true },
                onApprove = { user -> approving = user },
                onReject = { user -> admin.reject(user) },
                onRenewInvite = { user -> admin.renewInvite(user) { invite -> shownInvite = invite } },
                userList = users?.value ?: emptyList(),
                seed = userSeed,
                currentUserIdentifier = currentUser?.identifier,
                listState = userListState,
                coroutineScope = coroutineScope,
                onSelect = { identifier -> viewModel.setSelectedUser(identifier) },
                onSearchQuery = { query -> viewModel.searchUsers(query) },
                onAdd = {
                    isNewUser = true
                    showUserDialog = true
                },
                onEdit = {
                    if (!userSeed.isNew) {
                        isNewUser = false
                        showUserDialog = true
                    }
                },
                onResetPassword = { if (!userSeed.isNew) showResetPasswordDialog = true },
                onSetActive = { active -> admin.setUserActive(userSeed.user, active) },
                onLogOutEverywhere = { admin.revokeAllSessions(userSeed.user) },
                onUnlockPin = { admin.unlockPin(userSeed.user) },
                onResetPin = { admin.resetPin(userSeed.user) },
                onDelete = { if (!userSeed.isNew) showDeleteDialog = true }
            )

            AdminTab.SESSIONS -> AdminSessionsTab(
                sessions = sessions,
                currentUserIdentifier = currentUser?.identifier,
                currentSessionId = admin.currentSessionId(),
                onRevoke = { session -> admin.revokeSession(session) }
            )

            AdminTab.STATIONS -> AdminStationsTab(
                stations = stations,
                onApprove = { admin.approveStation(it) },
                onRemove = { admin.removeStation(it) }
            )

            AdminTab.LOW_STOCK -> AdminLowStockTab(
                items = lowStock,
                threshold = lowStockThreshold,
                onThresholdChange = { admin.setLowStockThreshold(it) },
                onItemClick = { identifier ->
                    viewModel.setSelectedInventory(identifier)
                    viewModel.changeMainTabIndex(INVENTORY_TAB)
                    onNavigateToMain()
                }
            )

            AdminTab.SERVER -> AdminServerTab(
                serverInfo = serverInfo,
                serverUrl = serverUrl,
                shopProfile = shopProfile,
                isBusy = isLoading,
                onSaveShopProfile = { admin.saveShopProfile(it) },
                onBackup = { admin.downloadBackup() },
                onSeed = { request -> admin.seed(request, onSuccess = { viewModel.refreshPagers() }) }
            )

            AdminTab.AUDIT -> AdminAuditTab(
                entries = auditEntries,
                endReached = auditEndReached,
                onLoadMore = { admin.loadMoreAudit() }
            )
        }
    }
}

@Composable
private fun NotAdminMessage(viewModel: MainViewModel) {
    val loginState by viewModel.loginState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Admin access required",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.padding(8.dp))

        Text(
            text = if (loginState == LoginState.LOGGED_IN) "Your account doesn't have admin access."
                else "Log in with an admin account to use this screen.",
            fontSize = 16.sp
        )

        if (loginState != LoginState.LOGGED_IN) {
            Spacer(Modifier.padding(8.dp))

            Button(
                enabled = loginState != LoginState.LOADING,
                onClick = { viewModel.changeShowLogin(true) }
            ) {
                Text("Log In")
            }
        }
    }
}

// Main screen tab indexes
private const val TICKET_TAB = 1
private const val INVENTORY_TAB = 2
