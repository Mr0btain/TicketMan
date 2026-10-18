package org.mr0btain.ticketman.ui.screens.admin.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatBytes
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatDuration
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import org.mr0btain.ticketman.ui.screens.admin.components.BackupProgress
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.containers.TmSwitchRow
import org.mr0btain.ticketman.ui.kit.data.KeyValueRow
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.feedback.TmProgressBar
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.database
import ticketman.composeapp.generated.resources.download
import ticketman.composeapp.generated.resources.home
import ticketman.composeapp.generated.resources.server
import ticketman.composeapp.generated.resources.zap

/** Width of the panels in the left column */
private val SIDE_WIDTH = 420.dp

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
    onSaveShopProfile: (ShopProfile) -> Unit,
    onBackup: (includeImages: Boolean) -> Unit,
    onSeed: (SeedRequest) -> Unit
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

                BackupCard(isBusy, backupProgress, serverInfo, onBackup)

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
    serverUrl: String
) {
    TmPanel(modifier = Modifier.width(SIDE_WIDTH), title = "Server", icon = Res.drawable.server) {
        KeyValueRow(label = "Address", value = serverUrl.ifBlank { "Not set" })

        if (serverInfo == null) {
            TmNotice(text = "Couldn't load server info. Check the connection, then refresh.", kind = TmNoticeKind.ERROR)
            return@TmPanel
        }

        Column {
            KeyValueRow(label = "Name", value = serverInfo.name)
            KeyValueRow(label = "Version", value = serverInfo.version, numeric = true)
            KeyValueRow(label = "Uptime", value = formatDuration(serverInfo.uptimeMillis))
            KeyValueRow(label = "Started", value = formatDateTime(serverInfo.startedAt))
            KeyValueRow(label = "Database size", value = formatBytes(serverInfo.databaseSizeBytes))
            KeyValueRow(label = "Photos size", value = formatBytes(serverInfo.imagesSizeBytes))
            KeyValueRow(label = "Java", value = serverInfo.javaVersion)
            KeyValueRow(label = "OS", value = serverInfo.operatingSystem)
            KeyValueRow(label = "Mode", value = if (serverInfo.developmentMode) "Development" else "Production")
        }

        Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
            Text(text = "Data folder", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
            SelectionContainer {
                Text(text = serverInfo.dataPath, style = TmTheme.typography.numeric, color = TmTheme.colors.text)
            }
        }
    }
}

@Composable
private fun BackupCard(
    isBusy: Boolean,
    progress: BackupProgress?,
    serverInfo: ServerInfoResponse?,
    onBackup: (includeImages: Boolean) -> Unit
) {
    var includeImages by remember { mutableStateOf(true) }

    TmPanel(
        modifier = Modifier.width(SIDE_WIDTH),
        title = "Backup",
        subtitle = "A zip of the database and photos. It has customer details in it, so keep it somewhere safe.",
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
    val spacing = TmTheme.spacing

    TmPanel(
        modifier = Modifier.width(560.dp),
        title = "Shop profile",
        subtitle = "Shown on invoices and anything else customers see",
        icon = Res.drawable.home,
        actions = {
            TmButton(
                text = "Save profile",
                enabled = shopProfile != null && profile != loaded && profile.repeatWindowDays in REPEAT_WINDOW_RANGE,
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

// Days a repaired device can come back and be flagged as a repeat
private val REPEAT_WINDOW_RANGE = 1..3650
