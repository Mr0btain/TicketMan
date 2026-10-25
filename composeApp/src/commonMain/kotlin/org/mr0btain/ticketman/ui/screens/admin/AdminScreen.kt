package org.mr0btain.ticketman.ui.screens.admin

import org.mr0btain.ticketman.ui.screens.admin.locations.AdminLocationsTab
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.uuid.Uuid
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
import org.mr0btain.ticketman.ui.screens.admin.messages.AdminMessagesTab
import org.mr0btain.ticketman.ui.screens.admin.payments.AdminPaymentsTab
import org.mr0btain.ticketman.ui.screens.admin.components.AdminTab
import org.mr0btain.ticketman.ui.screens.admin.inventory.AdminLowStockTab
import org.mr0btain.ticketman.ui.screens.admin.invoices.AdminInvoicesTab
import org.mr0btain.ticketman.ui.screens.admin.overview.AdminOverviewTab
import org.mr0btain.ticketman.ui.screens.admin.reports.AdminReportsTab
import org.mr0btain.ticketman.ui.screens.admin.timesheets.AdminTimesheetsTab
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
import org.mr0btain.ticketman.ui.screens.CheckForUpdates
import org.mr0btain.ticketman.components.data.local.UpdateInterval
import org.mr0btain.ticketman.ui.screens.admin.server.ImportCard
import org.mr0btain.ticketman.ui.screens.admin.server.ImportDialog
import ticketman.composeapp.generated.resources.shield

/**
 * Admin dashboard. Only usable by admins, the server enforces this as well
 * @param onNavigateToMain Switches to the main screen, used after picking a ticket or item to look at
 * @param onOpenTicketAtCounter Opens a ticket on the counter screen, from the invoice list
 */
@Composable
fun AdminScreen(
    viewModel: MainViewModel,
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
    val sessions by admin.devices.sessions.collectAsState()
    val loginLimits by admin.devices.loginLimits.collectAsState()
    val pending by admin.accounts.pending.collectAsState()
    val stations by admin.devices.stations.collectAsState()
    val serverInfo by admin.server.serverInfo.collectAsState()
    val backupProgress by admin.server.backupProgress.collectAsState()
    val pendingRestore by admin.server.pendingRestore.collectAsState()
    val shopProfile by admin.server.shopProfile.collectAsState()
    val messageSettings by admin.messages.settings.collectAsState()
    val outbox by admin.messages.outbox.collectAsState()
    val outboxEnd by admin.messages.outboxEnd.collectAsState()
    val paymentSettings by admin.payments.settings.collectAsState()
    val allLocations by viewModel.locations.all.collectAsState()
    val activeLocations by viewModel.locations.active.collectAsState()
    val accountReaders by admin.payments.accountReaders.collectAsState()
    val cardCharges by admin.payments.charges.collectAsState()
    val cardChargesEnd by admin.payments.chargesEnd.collectAsState()
    val report by admin.reports.report.collectAsState()
    val reportRange by admin.reports.range.collectAsState()
    val commissions by admin.reports.commissions.collectAsState()
    val reportUsers by admin.reports.users.collectAsState()
    val timesheet by admin.timesheets.sheet.collectAsState()
    val onTheClock by admin.timesheets.onNow.collectAsState()
    val timesheetUsers by admin.timesheets.users.collectAsState()
    val timesheetRange by admin.timesheets.range.collectAsState()
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
            admin.accounts.invite(request) { invite ->
                showInviteDialog = false
                shownInvite = invite
            }
        }
    )

    ApproveDialog(
        user = approving,
        onDismiss = { approving = null },
        onApprove = { level -> approving?.let { user -> admin.accounts.approve(user, level) { approving = null } } }
    )

    InviteCodeDialog(
        invite = shownInvite,
        onDismiss = { shownInvite = null }
    )

    val importSheet by admin.import.sheet.collectAsState()
    val importMapping by admin.import.mapping.collectAsState()
    val importPreview by admin.import.preview.collectAsState()
    val importProblem by admin.import.problem.collectAsState()
    val importing by admin.import.importing.collectAsState()

    importSheet?.let { sheet ->
        ImportDialog(
            sheet = sheet,
            mapping = importMapping,
            preview = importPreview,
            problem = importProblem,
            importing = importing,
            onMap = { field, column -> admin.import.map(field, column) },
            onImport = { admin.import.import {} },
            onDismiss = { admin.import.close() }
        )
    }

    // Fresh numbers whenever a tab is opened, only for that tab, and when the device starts working somewhere else
    val workingLocation by viewModel.locations.working.collectAsState()
    LaunchedEffect(selectedTab, workingLocation) {
        admin.refresh(selectedTab)
        if (selectedTab == AdminTab.USERS) viewModel.users.pager.refresh()
    }

    // Then changes from other devices, on the interval set in Settings, unless a dialog here is open
    val updateInterval by viewModel.settings.updateInterval.collectAsState()
    val dialogOpen = importSheet != null || showUserDialog || showResetPasswordDialog || showDeleteDialog || showInviteDialog || approving != null || shownInvite != null
    CheckForUpdates(interval = updateInterval, enabled = !dialogOpen && !isLoading) {
        admin.checkForUpdates(selectedTab)
        if (selectedTab == AdminTab.USERS) viewModel.users.pager.refreshCurrentData()
        if (selectedTab == AdminTab.INVOICES) viewModel.counter.invoices.checkForUpdates()
    }

    UserDialog(
        isVisible = showUserDialog,
        user = if (isNewUser) remember(showUserDialog) { User() } else userSeed.user,
        isNew = isNewUser,
        isSelf = !isNewUser && userSeed.identifier == currentUser?.identifier,
        onDismiss = { showUserDialog = false },
        onConfirm = { user, password ->
            admin.accounts.saveUser(user, isNewUser, password, onSuccess = {
                showUserDialog = false
                viewModel.users.select(user.identifier, force = true)
            })
        },
        locations = activeLocations,
        defaultLocation = viewModel.locations.working.value.identifier
    )

    ResetPasswordDialog(
        isVisible = showResetPasswordDialog,
        user = userSeed.user,
        onDismiss = { showResetPasswordDialog = false },
        onConfirm = { password ->
            admin.accounts.resetPassword(userSeed.user, password, onSuccess = { showResetPasswordDialog = false })
        }
    )

    if (showDeleteDialog) {
        TmConfirmDialog(
            title = "Delete ${userSeed.username}?",
            message = "Their account goes for good. Deactivating keeps their history and stops them logging in.",
            confirmText = "Delete user",
            destructive = true,
            onConfirm = {
                admin.accounts.deleteUser(userSeed.user)
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

            // Tabs check for changes on their own; this is only for when that's turned off in Settings
            if (updateInterval == UpdateInterval.OFF) {
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
                            AdminTab.INVOICES -> {
                                viewModel.counter.invoices.load(reset = true)
                                viewModel.counter.invoices.loadGiftCards()
                            }
                            else -> Unit
                        }
                    }
                )
            }
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
                onReject = { user -> admin.accounts.reject(user) },
                onRenewInvite = { user -> admin.accounts.renewInvite(user) { invite -> shownInvite = invite } },
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
                onSetActive = { active -> admin.accounts.setUserActive(userSeed.user, active) },
                onLogOutEverywhere = { admin.devices.revokeAllSessions(userSeed.user) },
                onUnlockPin = { admin.accounts.unlockPin(userSeed.user) },
                onResetPin = { admin.accounts.resetPin(userSeed.user) },
                onSaveNote = { note -> viewModel.users.saveNote(note) },
                onRemoveNote = { identifier -> viewModel.users.removeNote(identifier) },
                onSetPhoto = { photo -> admin.accounts.setPhoto(userSeed.user, photo) },
                onPrintIdCard = { admin.accounts.printIdCard(users.value.firstOrNull { it.identifier == userSeed.identifier } ?: userSeed.user) },
                onNewBadge = { admin.accounts.newBadge(userSeed.user) },
                onDelete = { if (!userSeed.isNew) showDeleteDialog = true }
            )

            AdminTab.SESSIONS -> AdminSessionsTab(
                sessions = sessions,
                currentUserIdentifier = currentUser?.identifier,
                currentSessionId = admin.devices.currentSessionId(),
                onRevoke = { session -> admin.devices.revokeSession(session) },
                loginLimits = loginLimits,
                onUnlockLogin = { lock -> admin.devices.unlockLogin(lock) }
            )

            AdminTab.STATIONS -> AdminStationsTab(
                stations = stations,
                onApprove = { admin.devices.approveStation(it) },
                onKiosk = { station, settings -> admin.devices.setKiosk(station, settings) },
                onRemove = { admin.devices.removeStation(it) },
                locations = activeLocations,
                onLocation = { station, location -> admin.devices.setStationLocation(station, location) }
            )

            AdminTab.LOCATIONS -> AdminLocationsTab(
                locations = allLocations,
                taxClasses = shopProfile?.taxClasses.orEmpty(),
                onSave = { location, isNew, onResult -> viewModel.locations.save(location, isNew, onResult) }
            )

            AdminTab.LOW_STOCK -> AdminLowStockTab(
                items = lowStock,
                threshold = lowStockThreshold,
                onThresholdChange = { admin.setLowStockThreshold(it) },
                onDraftOrders = if (viewModel.session.canEditNow()) {
                    { onResult -> viewModel.purchasing.draftForLowStock(lowStock, lowStockThreshold, onResult) }
                } else null,
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

            AdminTab.REPORTS -> AdminReportsTab(
                report = report,
                range = reportRange,
                days = admin.reports.days(),
                commissions = commissions,
                users = reportUsers,
                defaultPaper = shopProfile?.defaultPaper ?: PaperSize.RECEIPT_80MM,
                isBusy = isLoading,
                onPick = { admin.reports.pick(it) },
                onShowDays = { from, to -> admin.reports.showDays(from, to) },
                onPrint = { paper, onResult -> admin.reports.print(paper, onResult) },
                onSaveCommissions = { rates, onSaved -> admin.reports.saveCommissions(rates, onSaved) }
            )

            AdminTab.TIMESHEETS -> AdminTimesheetsTab(
                sheet = timesheet,
                onNow = onTheClock,
                users = timesheetUsers,
                range = timesheetRange,
                days = admin.timesheets.days(),
                onPick = { admin.timesheets.pick(it) },
                onShowDays = { from, to -> admin.timesheets.showDays(from, to) },
                onSave = { entry, isNew, onResult -> admin.timesheets.save(entry, isNew, onResult) },
                onRemove = { admin.timesheets.remove(it) }
            )

            AdminTab.SERVER -> AdminServerTab(
                serverInfo = serverInfo,
                serverUrl = serverUrl,
                shopProfile = shopProfile,
                isBusy = isLoading,
                backupProgress = backupProgress,
                pendingRestore = pendingRestore,
                onSaveShopProfile = { admin.server.saveShopProfile(it) },
                onBackup = { includeImages -> admin.server.downloadBackup(includeImages) },
                onRestore = { file -> admin.server.restoreFrom(file) },
                onCancelRestore = { admin.server.cancelRestore() },
                onRestart = { admin.server.restartServer() },
                onSeed = { request -> admin.server.seed(request, onSuccess = { viewModel.refreshPagers() }) },
                importCard = {
                    ImportCard(
                        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                        isBusy = isLoading,
                        onPicked = { kind, file -> admin.import.openFile(kind, file) }
                    )
                }
            )

            AdminTab.MESSAGES -> AdminMessagesTab(
                settings = messageSettings,
                statuses = shopProfile?.statuses.orEmpty(),
                outbox = outbox,
                outboxEnd = outboxEnd,
                isBusy = isLoading,
                onSave = { admin.messages.save(it) },
                onTest = { settings, channel, recipient -> admin.messages.test(settings, channel, recipient) },
                onRetry = { admin.messages.retry(it) },
                onCancel = { admin.messages.cancel(it) },
                onLoadMore = { admin.messages.loadMore() }
            )

            AdminTab.PAYMENTS -> AdminPaymentsTab(
                settings = paymentSettings,
                publicUrl = shopProfile?.publicUrl.orEmpty(),
                accountReaders = accountReaders,
                charges = cardCharges,
                chargesEnd = cardChargesEnd,
                isBusy = isLoading,
                onSave = { admin.payments.save(it) },
                onFindReaders = { admin.payments.findReaders() },
                onRefund = { charge, reason -> admin.payments.refund(charge, reason) },
                onLoadMore = { admin.payments.loadMore() }
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
