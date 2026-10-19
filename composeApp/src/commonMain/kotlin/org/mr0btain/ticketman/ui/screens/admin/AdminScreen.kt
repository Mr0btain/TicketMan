package org.mr0btain.ticketman.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.pagination.offset.OffsetPagerLoader
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.response.InviteResponse
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmTabs
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.admin.audit.AdminAuditTab
import org.mr0btain.ticketman.ui.screens.admin.components.AdminTab
import org.mr0btain.ticketman.ui.screens.admin.inventory.AdminLowStockTab
import org.mr0btain.ticketman.ui.screens.admin.invoices.AdminInvoicesTab
import org.mr0btain.ticketman.ui.screens.admin.overview.AdminOverviewTab
import org.mr0btain.ticketman.ui.screens.admin.server.AdminServerTab
import org.mr0btain.ticketman.ui.screens.admin.sessions.AdminSessionsTab
import org.mr0btain.ticketman.ui.screens.admin.stations.AdminStationsTab
import org.mr0btain.ticketman.ui.screens.admin.users.AdminUsersTab
import org.mr0btain.ticketman.ui.screens.admin.users.ApproveDialog
import org.mr0btain.ticketman.ui.screens.admin.users.InviteCodeDialog
import org.mr0btain.ticketman.ui.screens.admin.users.InviteDialog
import org.mr0btain.ticketman.ui.screens.admin.users.ResetPasswordDialog
import org.mr0btain.ticketman.ui.screens.admin.users.UserDialog
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.MainViewModel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.refresh_cw
import ticketman.composeapp.generated.resources.shield

/**
 * Admin dashboard. Only usable by admins, the server enforces this as well
 * @param onNavigateToMain Switches to the main screen, used after picking a ticket or item to look at
 * @param onOpenTicketAtCounter Opens a ticket on the counter screen, from the invoice list
 */
@Composable
fun AdminScreen(
    viewModel: MainViewModel,
    snackBarState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    onNavigateToMain: () -> Unit,
    onOpenTicketAtCounter: (Uuid) -> Unit
) {
    val isAdmin by viewModel.session.isAdmin.collectAsState()

    if (isAdmin) {
        AdminContent(viewModel, onNavigateToMain, onOpenTicketAtCounter)
    } else {
        NotAdminMessage(viewModel)
    }
}

@Composable
private fun AdminContent(
    viewModel: MainViewModel,
    onNavigateToMain: () -> Unit,
    onOpenTicketAtCounter: (Uuid) -> Unit
) {
    val admin = viewModel.admin

    var selectedTab by rememberSaveable { mutableStateOf(AdminTab.OVERVIEW) }
    val scope = rememberCoroutineScope()

    val isLoading by admin.isLoading.collectAsState()
    val pendingUser by viewModel.users.selection.pending.collectAsState()
    val stats by admin.stats.collectAsState()
    val dailyReport by admin.dailyReport.collectAsState()
    val lowStock by admin.lowStock.collectAsState()
    val lowStockThreshold by admin.lowStockThreshold.collectAsState()
    val sessions by admin.sessions.collectAsState()
    val pending by admin.pending.collectAsState()
    val stations by admin.stations.collectAsState()
    val serverInfo by admin.serverInfo.collectAsState()
    val backupProgress by admin.backupProgress.collectAsState()
    val pendingRestore by admin.pendingRestore.collectAsState()
    val shopProfile by admin.shopProfile.collectAsState()
    val auditEntries by admin.auditEntries.collectAsState()
    val auditEndReached by admin.auditEndReached.collectAsState()
    val serverUrl by viewModel.settings.serverUrl.collectAsState()
    val currentUser by viewModel.session.currentUser.collectAsState()

    // Users list uses the same pager as everywhere else
    val userSeed = viewModel.users.seed
    val users = viewModel.users.pager.items.collectAsState()
    val userListState = rememberLazyListState()
    OffsetPagerLoader(viewModel.users.pager, userListState)

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

    // Fresh numbers whenever a tab is opened, only for that tab
    LaunchedEffect(selectedTab) {
        admin.refresh(selectedTab)
        if (selectedTab == AdminTab.USERS) viewModel.users.pager.refresh()
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
                viewModel.users.select(user.identifier, force = true)
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
        TmConfirmDialog(
            title = "Delete ${userSeed.username}?",
            message = "Their account goes for good. Deactivating keeps their history and stops them logging in.",
            confirmText = "Delete user",
            destructive = true,
            onConfirm = {
                admin.deleteUser(userSeed.user)
                showDeleteDialog = false
            },
            onDismissRequest = { showDeleteDialog = false }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)
    ) {
        // ------------------------------------------ Tabs ------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            TmTabs(
                modifier = Modifier.weight(1f),
                tabs = AdminTab.entries.map { it.label },
                selectedIndex = selectedTab.ordinal,
                onSelect = { selectedTab = AdminTab.entries[it] },
                badges = if (pending.isNotEmpty()) mapOf(AdminTab.USERS.ordinal to pending.size) else emptyMap()
            )

            TmButton(
                text = "Refresh",
                enabled = !isLoading,
                loading = isLoading,
                variant = TmButtonVariant.SECONDARY,
                leadingIcon = Res.drawable.refresh_cw,
                onClick = {
                    admin.refresh(selectedTab)
                    when (selectedTab) {
                        AdminTab.USERS -> scope.launch { viewModel.users.pager.refresh() }
                        AdminTab.INVOICES -> viewModel.counter.loadInvoices(reset = true)
                        else -> Unit
                    }
                }
            )
        }

        // ------------------------------------------ Content ------------------------------------------
        when (selectedTab) {
            AdminTab.OVERVIEW -> AdminOverviewTab(
                stats = stats,
                dailyReport = dailyReport,
                defaultPaper = shopProfile?.defaultPaper ?: PaperSize.RECEIPT_80MM,
                onMoveReport = { admin.moveDailyReport(it) },
                onPrintReport = { paper, onResult -> admin.printDailyReport(paper, onResult) },
                onTicketClick = { identifier ->
                    viewModel.tickets.select(identifier)
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
                userList = users.value,
                seed = userSeed,
                pendingId = pendingUser,
                currentUserIdentifier = currentUser?.identifier,
                listState = userListState,
                onSelect = { identifier -> viewModel.users.select(identifier) },
                onSearchQuery = { query -> viewModel.users.search(query) },
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
                onSaveNote = { note -> viewModel.users.saveNote(note) },
                onRemoveNote = { identifier -> viewModel.users.removeNote(identifier) },
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
                onKiosk = { station, settings -> admin.setKiosk(station, settings) },
                onRemove = { admin.removeStation(it) }
            )

            AdminTab.LOW_STOCK -> AdminLowStockTab(
                items = lowStock,
                threshold = lowStockThreshold,
                onThresholdChange = { admin.setLowStockThreshold(it) },
                onItemClick = { identifier ->
                    viewModel.inventory.select(identifier)
                    viewModel.changeMainTabIndex(INVENTORY_TAB)
                    onNavigateToMain()
                }
            )

            AdminTab.INVOICES -> AdminInvoicesTab(
                viewModel = viewModel,
                onOpenTicket = onOpenTicketAtCounter
            )

            AdminTab.SERVER -> AdminServerTab(
                serverInfo = serverInfo,
                serverUrl = serverUrl,
                shopProfile = shopProfile,
                isBusy = isLoading,
                backupProgress = backupProgress,
                pendingRestore = pendingRestore,
                onSaveShopProfile = { admin.saveShopProfile(it) },
                onBackup = { includeImages -> admin.downloadBackup(includeImages) },
                onRestore = { file -> admin.restoreFrom(file) },
                onCancelRestore = { admin.cancelRestore() },
                onRenewCertificate = { admin.renewCertificate() },
                onCancelCertificateRenewal = { admin.cancelCertificateRenewal() },
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
    val loginState by viewModel.session.loginState.collectAsState()
    val loggedIn = loginState == LoginState.LOGGED_IN

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TmEmptyState(
            icon = Res.drawable.shield,
            title = "Admin access required",
            message = if (loggedIn) "Your account doesn't have admin access." else "Log in with an admin account to use this screen.",
            actionText = if (loggedIn) null else "Log in",
            onAction = { if (loginState != LoginState.LOADING) viewModel.session.changeShowLogin(true) }
        )
    }
}

// Main screen tab indexes
private const val TICKET_TAB = 1
private const val INVENTORY_TAB = 2
