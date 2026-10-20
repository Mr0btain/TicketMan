package org.mr0btain.ticketman.ui.screens.admin.server

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
import org.mr0btain.ticketman.components.utils.formatDay
import org.mr0btain.ticketman.components.utils.formatDuration
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.response.PendingRestore
import org.mr0btain.ticketman.server.response.ServerCertificateInfo
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
import kotlin.time.Clock

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
    onRenewCertificate: () -> Unit,
    onCancelCertificateRenewal: () -> Unit,
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
                ServerInfoCard(serverInfo, serverUrl, isBusy, onRenewCertificate, onCancelCertificateRenewal)

                BackupCard(isBusy, backupProgress, pendingRestore, serverInfo, onBackup, onRestore, onCancelRestore, onRestart)

                importCard()

                // Mock data is only allowed on servers started with -Pdevelopment
                if (serverInfo?.developmentMode == true) {
                    SeedCard(isBusy, onSeed)
                }
            }

            ShopProfileCard(shopProfile, onSaveShopProfile)
        }
    }
}

@Composable
private fun ServerInfoCard(
    serverInfo: ServerInfoResponse?,
    serverUrl: String,
    isBusy: Boolean,
    onRenewCertificate: () -> Unit,
    onCancelCertificateRenewal: () -> Unit
) {
    TmPanel(modifier = Modifier.width(SIDE_WIDTH), title = "Server", icon = Res.drawable.server) {
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
        // Behind a proxy devices only ever see the proxy's certificate, so the server's own isn't worth showing
        if (serverInfo.proxy) ProxyRows(serverInfo)
        else CertificateRows(serverInfo.certificate, isBusy, onRenewCertificate, onCancelCertificateRenewal)
    }
}

/**
 * Behind a reverse proxy: who does HTTPS, and whether the proxy says who each device is. A proxy the server doesn't
 * trust makes every device look like the proxy, so wrong password limits count them all together
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
        // A proxy can still talk HTTPS to the server
        serverInfo.certificate?.let { TmKeyValueRow(label = "Server's own HTTPS port", value = it.httpsPort.toString(), numeric = true) }
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

/**
 * HTTPS settings and the certificate's fingerprint, which devices ask staff to check before trusting the server.
 * A certificate the server made can be replaced here, ahead of it running out
 */
@Composable
private fun CertificateRows(
    certificate: ServerCertificateInfo?,
    isBusy: Boolean,
    onRenew: () -> Unit,
    onCancelRenewal: () -> Unit
) {
    if (certificate == null) {
        TmKeyValueRow(label = "HTTPS", value = "Off")
        return
    }

    var confirmRenew by remember { mutableStateOf(false) }

    if (confirmRenew) {
        TmConfirmDialog(
            title = "Make a new certificate?",
            message = "The server switches to it the next time it starts. After that every device will warn that " +
                "the certificate changed and ask staff to trust the new one, so tell them to expect it.",
            confirmText = "Make certificate",
            onConfirm = {
                onRenew()
                confirmRenew = false
            },
            onDismissRequest = { confirmRenew = false }
        )
    }

    Column {
        TmKeyValueRow(label = "HTTPS port", value = certificate.httpsPort.toString(), numeric = true)
        TmKeyValueRow(
            label = "Plain HTTP",
            value = when (certificate.httpMode) {
                "redirect" -> "Sent to HTTPS"
                "off" -> "Off"
                else -> "On"
            }
        )
        TmKeyValueRow(label = "Certificate", value = if (certificate.generated) "Made by the server" else "Supplied")
        TmKeyValueRow(label = "Expires", value = formatDay(certificate.expiresAt))
    }

    FingerprintText(label = "Fingerprint", fingerprint = certificate.fingerprint)

    if (certificate.expiresSoon(Clock.System.now().toEpochMilliseconds())) {
        TmNotice(
            kind = TmNoticeKind.WARNING,
            title = "Certificate runs out soon",
            text = "It stops working on ${formatDay(certificate.expiresAt)}. " +
                if (certificate.generated) "Make a new one below and restart the server before then."
                else "Replace the SSL_KEYSTORE file before then."
        )
    }

    // A supplied certificate is replaced on the server, not from here
    if (!certificate.generated) return

    val pending = certificate.pendingFingerprint
    if (pending != null) {
        TmNotice(
            kind = TmNoticeKind.INFO,
            title = "New certificate waiting",
            text = "Used from the next time the server starts" +
                (certificate.pendingExpiresAt?.let { ", until ${formatDay(it)}" } ?: "") +
                ". Devices will ask to trust it; check they show this fingerprint."
        )
        FingerprintText(label = "New fingerprint", fingerprint = pending)
        TmButton(text = "Keep the current certificate", variant = TmButtonVariant.SECONDARY, enabled = !isBusy, onClick = onCancelRenewal)
    } else {
        TmButton(text = "Make a new certificate", variant = TmButtonVariant.SECONDARY, enabled = !isBusy, onClick = { confirmRenew = true })
    }
}

@Composable
private fun FingerprintText(label: String, fingerprint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
        Text(text = label, style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
        SelectionContainer {
            Column {
                // Split the same way as on the device asking, so the two can be read off line by line
                fingerprint.split(":").chunked(16).forEach { line ->
                    Text(text = line.joinToString(":"), style = TmTheme.typography.numeric, color = TmTheme.colors.text)
                }
            }
        }
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
        modifier = Modifier.width(SIDE_WIDTH),
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
        modifier = Modifier.width(SIDE_WIDTH),
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
    // Edits are kept here until saved, and reset when a new profile comes from the server
    val loaded = shopProfile ?: ShopProfile()
    var profile by remember(loaded) { mutableStateOf(loaded) }
    // Kept as text while typing so the field can be cleared, saved as a number
    var repeatDays by remember(loaded) { mutableStateOf(profile.repeatWindowDays.toString()) }
    // The server checks this too
    val symbolProblem = profile.currencySymbol.trim().let { symbol ->
        if (symbol.isEmpty() || symbol.length > Money.MAX_SYMBOL_LENGTH || symbol.any { it.isDigit() }) "1 to ${Money.MAX_SYMBOL_LENGTH} characters, no digits" else null
    }
    val spacing = TmTheme.spacing

    TmPanel(
        modifier = Modifier.width(560.dp),
        title = "Shop profile",
        subtitle = "Shown on invoices and anything else customers see",
        icon = Res.drawable.home,
        actions = {
            TmButton(
                text = "Save profile",
                enabled = shopProfile != null && profile != loaded && profile.repeatWindowDays in REPEAT_WINDOW_RANGE && symbolProblem == null,
                onClick = { onSave(profile) }
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
