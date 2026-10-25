package org.mr0btain.ticketman.ui.screens.admin.server

import org.mr0btain.ticketman.data.ticket.CustomStatus
import org.mr0btain.ticketman.data.ticket.StatusChoice
import org.mr0btain.ticketman.data.ticket.label
import org.mr0btain.ticketman.data.ticket.selectable
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import org.mr0btain.ticketman.components.utils.formatBytes
import org.mr0btain.ticketman.components.network.isSecureServerAddress
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatDuration
import org.mr0btain.ticketman.data.invoice.TaxClasses
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.response.PendingRestore
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import org.mr0btain.ticketman.ui.screens.admin.components.BackupProgress
import org.mr0btain.ticketman.ui.screens.admin.components.tooBigToRestoreMessage
import org.mr0btain.ticketman.components.network.transfer.isTooBigToSend
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.containers.TmSwitchRow
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.feedback.TmProgressBar
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.database
import ticketman.composeapp.generated.resources.download
import ticketman.composeapp.generated.resources.home
import ticketman.composeapp.generated.resources.server
import ticketman.composeapp.generated.resources.upload
import ticketman.composeapp.generated.resources.zap

/** Width of the panels in the left column */
private val SIDE_WIDTH = 440.dp

/**
 * Server details, the shop profile, backups and dev tools
 * @param isBusy Disables the buttons that kick off long running work
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminServerTab(
    serverInfo: ServerInfoResponse?,
    serverUrl: String,
    shopProfile: ShopProfile?,
    isBusy: Boolean,
    backupProgress: BackupProgress?,
    pendingRestore: PendingRestore?,
    onSaveShopProfile: (ShopProfile) -> Unit,
    onBackup: (includeImages: Boolean) -> Unit,
    onRestore: (PlatformFile) -> Unit,
    onCancelRestore: () -> Unit,
    onRestart: () -> Unit,
    onSeed: (SeedRequest) -> Unit,
    importCard: @Composable () -> Unit = {}
) {
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                ServerInfoCard(serverInfo, serverUrl)

                BackupCard(isBusy, backupProgress, pendingRestore, serverInfo, onBackup, onRestore, onCancelRestore, onRestart)

                importCard()

                // Mock data is only allowed on servers started with -Pdevelopment
                if (serverInfo?.developmentMode == true) {
                    SeedCard(isBusy, onSeed)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                ShopProfileCard(shopProfile, onSaveShopProfile)

                TicketStatusesCard(shopProfile?.statuses) { statuses -> shopProfile?.let { onSaveShopProfile(it.copy(statuses = statuses)) } }
            }
        }
    }
}

@Composable
private fun ServerInfoCard(
    serverInfo: ServerInfoResponse?,
    serverUrl: String
) {
    TmPanel(modifier = Modifier.widthIn(max = SIDE_WIDTH).fillMaxWidth(), title = "Server", icon = Res.drawable.server) {
        TmKeyValueRow(label = "Address") {
            Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                // Passwords and tokens go over the network readable on plain HTTP
                if (!isSecureServerAddress(serverUrl)) TmBadge(text = "Not secure", tone = TmTheme.colors.warning)
                Text(text = serverUrl.ifBlank { "Not set" }, style = TmTheme.typography.body, color = TmTheme.colors.text)
            }
        }

        if (serverInfo == null) {
            TmNotice(text = "Couldn't load server info. Check the connection, then refresh.", kind = TmNoticeKind.ERROR)
            return@TmPanel
        }

        Column {
            TmKeyValueRow(label = "Name", value = serverInfo.name)
            TmKeyValueRow(label = "Version", value = serverInfo.version, numeric = true)
            TmKeyValueRow(label = "Uptime", value = formatDuration(serverInfo.uptimeMillis))
            TmKeyValueRow(label = "Started", value = formatDateTime(serverInfo.startedAt))
            TmKeyValueRow(label = "Database size", value = formatBytes(serverInfo.databaseSizeBytes))
            TmKeyValueRow(label = "Photos size", value = formatBytes(serverInfo.imagesSizeBytes))
            TmKeyValueRow(label = "Java", value = serverInfo.javaVersion)
            TmKeyValueRow(label = "OS", value = serverInfo.operatingSystem)
            TmKeyValueRow(label = "Mode", value = if (serverInfo.developmentMode) "Development" else "Production")
            if (serverInfo.timeZone.isNotBlank()) TmKeyValueRow(label = "Time zone", value = serverInfo.timeZone)
        }

        Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
            Text(text = "Data folder", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
            SelectionContainer {
                Text(text = serverInfo.dataPath, style = TmTheme.typography.numeric, color = TmTheme.colors.text)
            }
        }

        TmDivider()
        ProxyRows(serverInfo)
    }
}

/**
 * The reverse proxy in front: whether it says who each device is. A proxy the server doesn't trust makes every
 * device look like the proxy, so wrong password limits count them all together
 */
@Composable
private fun ProxyRows(serverInfo: ServerInfoResponse) {
    Column {
        TmKeyValueRow(label = "HTTPS", value = "Handled by reverse proxy")
        TmKeyValueRow(label = "This device") {
            Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                if (serverInfo.forwarded) TmBadge(text = "Forwarded", tone = TmTheme.colors.success)
                else TmBadge(text = "Not forwarded", tone = TmTheme.colors.warning)
                Text(text = serverInfo.clientAddress.ifBlank { "Unknown" }, style = TmTheme.typography.numeric, color = TmTheme.colors.text)
            }
        }
    }

    // Can be a long list, so under its label rather than beside it
    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
        Text(text = "Trusted proxies (TRUSTED_PROXIES)", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
        SelectionContainer {
            Text(
                text = serverInfo.trustedProxies.joinToString(", ").ifBlank { "None" },
                style = TmTheme.typography.numeric,
                color = TmTheme.colors.text
            )
        }
    }

    if (!serverInfo.forwarded) {
        TmNotice(
            kind = TmNoticeKind.WARNING,
            title = "The proxy isn't saying who devices are",
            text = "The server sees ${serverInfo.clientAddress.ifBlank { "this device" }} and no forwarded address from a trusted " +
                "proxy, so every device may count as one for wrong password limits. If that's the proxy's address, add it " +
                "to TRUSTED_PROXIES and check the proxy sends X-Forwarded-For."
        )
    }
}

@Composable
private fun BackupCard(
    isBusy: Boolean,
    progress: BackupProgress?,
    pendingRestore: PendingRestore?,
    serverInfo: ServerInfoResponse?,
    onBackup: (includeImages: Boolean) -> Unit,
    onRestore: (PlatformFile) -> Unit,
    onCancelRestore: () -> Unit,
    onRestart: () -> Unit
) {
    var includeImages by remember { mutableStateOf(true) }
    var restoreFile by remember { mutableStateOf<PlatformFile?>(null) }
    var confirmRestart by remember { mutableStateOf(false) }
    val picker = rememberFilePickerLauncher(type = FileKitType.File("zip")) { file -> restoreFile = file }

    restoreFile?.takeIf { isTooBigToSend(it) }?.let { file ->
        TmConfirmDialog(
            title = "Too big for the browser",
            message = tooBigToRestoreMessage(file),
            confirmText = "OK",
            onConfirm = { restoreFile = null },
            onDismissRequest = { restoreFile = null }
        )
    }

    restoreFile?.takeUnless { isTooBigToSend(it) }?.let { file ->
        TmConfirmDialog(
            title = "Restore from ${file.name}?",
            message = "Next time the server starts, everything on it is replaced by this backup: tickets, " +
                "customers, users and photos. Anything changed since the backup was made is set aside in a " +
                "pre-restore folder on the server, not deleted." +
                // Database only backups are named that way when they're made
                if (file.name.endsWith(DATABASE_ONLY_SUFFIX)) "\n\nThis looks like a backup without photos. Photos on " +
                    "records won't load after restoring it." else "",
            confirmText = "Upload and restore",
            destructive = true,
            onConfirm = {
                onRestore(file)
                restoreFile = null
            },
            onDismissRequest = { restoreFile = null }
        )
    }

    if (confirmRestart) {
        TmConfirmDialog(
            title = "Restart the server?",
            message = "Everyone is disconnected while it restarts and restores the backup, which can take a few " +
                "minutes with lots of photos. Devices may need to log in again afterwards.",
            confirmText = "Restart",
            destructive = true,
            onConfirm = {
                onRestart()
                confirmRestart = false
            },
            onDismissRequest = { confirmRestart = false }
        )
    }

    TmPanel(
        modifier = Modifier.widthIn(max = SIDE_WIDTH).fillMaxWidth(),
        title = "Backup and restore",
        subtitle = "A zip of the database and photos, and putting one back. Backups have customer details in them, so keep them somewhere safe.",
        icon = Res.drawable.database
    ) {
        TmSwitchRow(
            title = "Include photos",
            subtitle = serverInfo?.let { "About ${formatBytes(it.imagesSizeBytes)} more" }
                ?: "Off for a quick copy of just the database",
            checked = includeImages,
            onCheckedChange = { includeImages = it },
            enabled = progress == null
        )

        if (progress != null) {
            Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
                Text(text = progress.text, style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
                TmProgressBar(progress = progress.fraction)
            }
        }

        TmButton(
            text = "Download backup",
            enabled = !isBusy,
            onClick = { onBackup(includeImages) },
            leadingIcon = Res.drawable.download
        )

        ScheduledBackupRows(serverInfo)

        serverInfo?.lastRestore?.let { restored ->
            TmNotice(
                kind = if (restored.manifest.includesImages) TmNoticeKind.INFO else TmNoticeKind.WARNING,
                title = "Restored ${formatDateTime(restored.restoredAt)}",
                text = "From the backup of ${formatDateTime(restored.manifest.createdAt)}. The data it replaced is in " +
                    "${restored.setAsideFolder} on the server for now." +
                    if (restored.manifest.includesImages) "" else " That backup had no photos, so photos on records won't load."
            )
        }

        if (pendingRestore != null) {
            TmNotice(
                kind = TmNoticeKind.WARNING,
                title = "Restore waiting",
                text = "The backup from ${formatDateTime(pendingRestore.manifest.createdAt)} " +
                    "(${formatBytes(pendingRestore.sizeBytes)}) replaces this server's data when it next starts. " +
                    "Restart the server to finish." +
                    if (pendingRestore.manifest.includesImages) "" else " It has no photos in it, so photos on " +
                        "records won't load afterwards."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                // Only when something starts the server again after it exits (RESTART_ON_EXIT, set in the Docker image)
                if (serverInfo?.canRestart == true) {
                    TmButton(text = "Restart now", enabled = !isBusy, onClick = { confirmRestart = true })
                }
                TmButton(text = "Cancel restore", variant = TmButtonVariant.SECONDARY, enabled = !isBusy, onClick = onCancelRestore)
            }
        } else {
            TmButton(
                text = "Restore from backup",
                variant = TmButtonVariant.SECONDARY,
                enabled = !isBusy,
                onClick = { picker.launch() },
                leadingIcon = Res.drawable.upload
            )
        }
    }
}

/** The daily backup set up on the server with BACKUP_DIR */
@Composable
private fun ScheduledBackupRows(serverInfo: ServerInfoResponse?) {
    if (serverInfo == null) return
    val schedule = serverInfo.scheduledBackup

    if (schedule == null) {
        Text(
            text = "No daily backup. Set BACKUP_DIR on the server to have it make one every day.",
            style = TmTheme.typography.caption,
            color = TmTheme.colors.textMuted
        )
        return
    }

    TmDivider()

    Column {
        TmKeyValueRow(label = "Daily backup", value = schedule.lastBackupAt?.let { formatDateTime(it) } ?: "Not made yet")
        schedule.lastSizeBytes?.let { TmKeyValueRow(label = "Size", value = formatBytes(it)) }
        TmKeyValueRow(label = "Contents", value = if (schedule.includesImages) "Database and photos" else "Database only")
        TmKeyValueRow(label = "Keeps", value = "The newest ${schedule.keep}")
    }

    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
        Text(text = "Backup folder", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
        SelectionContainer {
            Text(text = schedule.folder, style = TmTheme.typography.numeric, color = TmTheme.colors.text)
        }
    }

    schedule.lastFailedAt?.let { failed ->
        TmNotice(
            kind = TmNoticeKind.ERROR,
            text = "The daily backup failed at ${formatDateTime(failed)}. Check the server log and that the folder can be written to."
        )
    }
}

@Composable
private fun SeedCard(
    isBusy: Boolean,
    onSeed: (SeedRequest) -> Unit
) {
    val defaults = remember { SeedRequest() }
    var tickets by remember { mutableStateOf(defaults.tickets.toString()) }
    var customers by remember { mutableStateOf(defaults.customers.toString()) }
    var inventory by remember { mutableStateOf(defaults.inventory.toString()) }
    var users by remember { mutableStateOf(defaults.users.toString()) }

    val request = listOf(tickets, customers, inventory, users).map { it.toIntOrNull() }
        .takeIf { counts -> counts.all { it != null } }
        ?.let { (t, c, i, u) -> SeedRequest(tickets = t!!, customers = c!!, inventory = i!!, users = u!!) }

    TmPanel(
        modifier = Modifier.widthIn(max = SIDE_WIDTH).fillMaxWidth(),
        title = "Mock data",
        subtitle = "Development server only. Adds random tickets, customers, inventory and users.",
        icon = Res.drawable.zap
    ) {
        TmFieldPair(
            { CountField("Tickets", tickets, it) { v -> tickets = v } },
            { CountField("Customers", customers, it) { v -> customers = v } }
        )
        TmFieldPair(
            { CountField("Inventory", inventory, it) { v -> inventory = v } },
            { CountField("Users", users, it) { v -> users = v } }
        )

        TmButton(
            text = "Add mock data",
            variant = TmButtonVariant.SECONDARY,
            enabled = !isBusy && request != null,
            onClick = { request?.let(onSeed) }
        )
    }
}

@Composable
private fun CountField(
    label: String,
    value: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit
) {
    TmTextField(
        modifier = modifier,
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(4)) },
        label = label,
        textStyle = TmTheme.typography.numeric,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun ShopProfileCard(
    shopProfile: ShopProfile?,
    onSave: (ShopProfile) -> Unit
) {
    // Edits are kept here until saved, and reset when a new profile comes from the server. Statuses have their own
    // card, so saving those doesn't throw away edits made here
    val loaded = (shopProfile ?: ShopProfile()).copy(statuses = emptyList())
    var profile by remember(loaded) { mutableStateOf(loaded) }
    // Kept as text while typing so the field can be cleared, saved as a number
    var repeatDays by remember(loaded) { mutableStateOf(profile.repeatWindowDays.toString()) }
    // The server checks this too
    val symbolProblem = profile.currencySymbol.trim().let { symbol ->
        if (symbol.isEmpty() || symbol.length > Money.MAX_SYMBOL_LENGTH || symbol.any { it.isDigit() }) "1 to ${Money.MAX_SYMBOL_LENGTH} characters, no digits" else null
    }
    val urlProblem = ShopProfile.publicUrlProblem(profile.publicUrl)
    val spacing = TmTheme.spacing

    TmPanel(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        title = "Shop profile",
        subtitle = "Shown on invoices and anything else customers see",
        icon = Res.drawable.home,
        actions = {
            TmButton(
                text = "Save profile",
                enabled = shopProfile != null && profile != loaded && profile.repeatWindowDays in REPEAT_WINDOW_RANGE && symbolProblem == null && urlProblem == null && profile.storage.problem() == null &&
                    TaxClasses.problem(profile.taxClasses) == null,
                onClick = { onSave(profile.copy(statuses = shopProfile?.statuses.orEmpty())) }
            )
        }
    ) {
        if (shopProfile == null) {
            TmNotice(text = "Couldn't load the shop profile. Check the connection, then refresh.", kind = TmNoticeKind.ERROR)
            return@TmPanel
        }

        TmFieldPair(
            { TmTextField(modifier = it, value = profile.name, onValueChange = { v -> profile = profile.copy(name = v) }, label = "Shop name") },
            { TmTextField(modifier = it, value = profile.phone, onValueChange = { v -> profile = profile.copy(phone = v) }, label = "Phone") }
        )
        TmFieldPair(
            { TmTextField(modifier = it, value = profile.email, onValueChange = { v -> profile = profile.copy(email = v) }, label = "Email") },
            { TmTextField(modifier = it, value = profile.website, onValueChange = { v -> profile = profile.copy(website = v) }, label = "Website") }
        )
        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = profile.publicUrl,
            onValueChange = { profile = profile.copy(publicUrl = it.trim()) },
            label = "Public address",
            placeholder = "https://repairs.example.com",
            supportingText = "Where customers reach this server from outside the shop, for ticket status links",
            error = urlProblem
        )
        EstimateStatusPickers(statuses = shopProfile.statuses, profile = profile, onChange = { profile = it })

        TmTextField(modifier = Modifier.fillMaxWidth(), value = profile.street, onValueChange = { profile = profile.copy(street = it) }, label = "Street")
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            TmTextField(modifier = Modifier.weight(2f), value = profile.city, onValueChange = { profile = profile.copy(city = it) }, label = "City")
            TmTextField(modifier = Modifier.weight(1f), value = profile.state, onValueChange = { profile = profile.copy(state = it) }, label = "State")
            TmTextField(modifier = Modifier.weight(1f), value = profile.zip, onValueChange = { profile = profile.copy(zip = it) }, label = "Zip")
        }

        TmFieldPair(
            {
                TmTextField(
                    modifier = it,
                    value = profile.taxRate,
                    onValueChange = { text -> profile = profile.copy(taxRate = text.filter { c -> c.isDigit() || c == '.' }.take(6)) },
                    label = "Tax rate",
                    supportingText = "Percent, like 7.5",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            {
                TmTextField(
                    modifier = it,
                    value = profile.currencySymbol,
                    onValueChange = { text -> profile = profile.copy(currencySymbol = text.take(Money.MAX_SYMBOL_LENGTH)) },
                    label = "Currency symbol",
                    supportingText = "Put before every amount, like \$ or £",
                    error = symbolProblem,
                    textStyle = TmTheme.typography.numeric
                )
            }
        )

        TaxClassesEditor(classes = profile.taxClasses, onChange = { profile = profile.copy(taxClasses = it) })

        TmTextField(
            modifier = Modifier.width(240.dp),
            value = profile.warrantyDays.takeIf { it > 0 }?.toString().orEmpty(),
            onValueChange = { text -> profile = profile.copy(warrantyDays = text.filter { it.isDigit() }.take(4).toIntOrNull()?.coerceAtMost(3650) ?: 0) },
            label = "Repair warranty",
            placeholder = "None",
            supportingText = "Days from pickup that labor and parts are guaranteed",
            textStyle = TmTheme.typography.numeric,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        ChecklistsEditor(checklists = profile.checklists, onChange = { profile = profile.copy(checklists = it) })

        StorageTermsEditor(terms = profile.storage, onChange = { profile = profile.copy(storage = it) })

        TmFieldPair(
            {
                TmTextField(
                    modifier = it,
                    value = repeatDays,
                    onValueChange = { text ->
                        repeatDays = text.filter { c -> c.isDigit() }.take(4)
                        repeatDays.toIntOrNull()?.let { days -> profile = profile.copy(repeatWindowDays = days) }
                    },
                    label = "Repeat repair warning",
                    supportingText = "Days after pickup a device counts as a repeat",
                    error = if (repeatDays.toIntOrNull() in REPEAT_WINDOW_RANGE) null else "Use 1 to 3650 days",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            {
                TmMoneyField(
                    modifier = it,
                    cents = profile.laborRateCents,
                    onCentsChange = { cents -> profile = profile.copy(laborRateCents = cents) },
                    label = "Labor rate",
                    supportingText = "Per hour, for time logged on tickets"
                )
            }
        )

        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = profile.invoiceFooter,
            onValueChange = { profile = profile.copy(invoiceFooter = it) },
            label = "Invoice footer",
            singleLine = false,
            minLines = 2
        )

        TmSegmented(
            label = "Default paper for printing",
            options = PaperSize.entries,
            selected = profile.defaultPaper,
            onSelect = { profile = profile.copy(defaultPaper = it) },
            optionLabel = { it.label }
        )

        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = profile.repairTerms,
            onValueChange = { profile = profile.copy(repairTerms = it) },
            label = "Repair terms",
            supportingText = "Customers sign these at check in. Leave blank to skip signing.",
            singleLine = false,
            minLines = 4
        )
    }
}

// End of the file name the server gives a backup made without photos
private const val DATABASE_ONLY_SUFFIX = "-database.zip"

// Days a repaired device can come back and be flagged as a repeat
private val REPEAT_WINDOW_RANGE = 1..3650

/** Where sending, approving and declining an estimate move its ticket, each optional */
@Composable
private fun EstimateStatusPickers(statuses: List<CustomStatus>, profile: ShopProfile, onChange: (ShopProfile) -> Unit) {
    val options: List<StatusChoice?> = listOf(null) + statuses.selectable()
    fun label(choice: StatusChoice?) = choice?.let { statuses.label(it) } ?: "Leave it"

    Text(text = "Estimates move the ticket to", style = TmTheme.typography.label, color = TmTheme.colors.text)
    Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
        TmDropdown(
            modifier = Modifier.weight(1f),
            label = "When sent",
            options = options,
            selected = profile.estimateSentStatus,
            onSelect = { onChange(profile.copy(estimateSentStatus = it)) },
            optionLabel = ::label
        )
        TmDropdown(
            modifier = Modifier.weight(1f),
            label = "Approved",
            options = options,
            selected = profile.estimateApprovedStatus,
            onSelect = { onChange(profile.copy(estimateApprovedStatus = it)) },
            optionLabel = ::label
        )
        TmDropdown(
            modifier = Modifier.weight(1f),
            label = "Declined",
            options = options,
            selected = profile.estimateDeclinedStatus,
            onSelect = { onChange(profile.copy(estimateDeclinedStatus = it)) },
            optionLabel = ::label
        )
    }

    Text(text = "Parts ordered for a ticket move it to", style = TmTheme.typography.label, color = TmTheme.colors.text)
    Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
        TmDropdown(
            modifier = Modifier.weight(1f),
            label = "When the order is placed",
            options = options,
            selected = profile.partsOrderedStatus,
            onSelect = { onChange(profile.copy(partsOrderedStatus = it)) },
            optionLabel = ::label
        )
        TmDropdown(
            modifier = Modifier.weight(1f),
            label = "When they've all arrived",
            options = options,
            selected = profile.partsArrivedStatus,
            onSelect = { onChange(profile.copy(partsArrivedStatus = it)) },
            optionLabel = ::label
        )
    }
}
