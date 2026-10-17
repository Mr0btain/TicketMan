package org.mr0btain.ticketman.ui.screens.admin.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import org.mr0btain.ticketman.ui.composables.DialogTextRow
import org.mr0btain.ticketman.ui.composables.SimpleTextItem
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatDuration
import org.mr0btain.ticketman.components.utils.formatBytes
import org.mr0btain.ticketman.ui.composables.SectionCard
import org.mr0btain.ticketman.ui.composables.EmptyMessage
import org.mr0btain.ticketman.ui.screens.admin.components.*

/**
 * Server details, the shop profile, backups and dev tools
 * @param isBusy Disables the buttons that kick off long running work
 */
@Composable
fun AdminServerTab(
    serverInfo: ServerInfoResponse?,
    serverUrl: String,
    shopProfile: ShopProfile?,
    isBusy: Boolean,
    onSaveShopProfile: (ShopProfile) -> Unit,
    onBackup: () -> Unit,
    onSeed: (SeedRequest) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        FlowRow {
            Column {
                ServerInfoCard(serverInfo, serverUrl)

                BackupCard(isBusy, onBackup)

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
    SectionCard(
        modifier = Modifier.width(420.dp),
        title = "Server"
    ) {
        SimpleTextItem("Address", serverUrl.ifBlank { "-" })

        if (serverInfo == null) {
            EmptyMessage("Couldn't load server info")
            return@SectionCard
        }

        SimpleTextItem("Name", serverInfo.name)
        SimpleTextItem("Version", serverInfo.version)
        SimpleTextItem("Uptime", formatDuration(serverInfo.uptimeMillis))
        SimpleTextItem("Started", formatDateTime(serverInfo.startedAt))
        SimpleTextItem("Database size", formatBytes(serverInfo.databaseSizeBytes))
        SimpleTextItem("Java", serverInfo.javaVersion)
        SimpleTextItem("OS", serverInfo.operatingSystem)
        SimpleTextItem("Mode", if (serverInfo.developmentMode) "Development" else "Production")

        Spacer(Modifier.padding(4.dp))

        Text(text = "Data folder", fontSize = 12.sp)
        SelectableText(serverInfo.dataPath)
    }
}

@Composable
private fun SelectableText(text: String) {
    SelectionContainer {
        Text(text = text, fontSize = 13.sp)
    }
}

@Composable
private fun BackupCard(
    isBusy: Boolean,
    onBackup: () -> Unit
) {
    SectionCard(
        modifier = Modifier.width(420.dp),
        title = "Backup"
    ) {
        Text(
            text = "Downloads a zip of the whole database. Keep it somewhere safe, it has customer details in it.",
            fontSize = 13.sp
        )

        Spacer(Modifier.padding(8.dp))

        Button(
            enabled = !isBusy,
            onClick = onBackup
        ) {
            Text("Download Backup")
        }
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

    SectionCard(
        modifier = Modifier.width(420.dp),
        title = "Mock Data"
    ) {
        Text(
            text = "Development server only. Adds random tickets, customers, inventory and users.",
            fontSize = 13.sp
        )

        Row {
            CountField("Tickets", tickets, Modifier.weight(1f)) { tickets = it }
            CountField("Customers", customers, Modifier.weight(1f)) { customers = it }
        }

        Row {
            CountField("Inventory", inventory, Modifier.weight(1f)) { inventory = it }
            CountField("Users", users, Modifier.weight(1f)) { users = it }
        }

        Spacer(Modifier.padding(4.dp))

        OutlinedButton(
            enabled = !isBusy && request != null,
            onClick = { request?.let(onSeed) }
        ) {
            Text("Add Mock Data")
        }
    }
}

@Composable
private fun CountField(
    label: String,
    value: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier.padding(4.dp),
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(4)) },
        label = { Text(label) },
        singleLine = true,
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

    SectionCard(
        modifier = Modifier.width(560.dp),
        title = "Shop Profile",
        action = {
            Button(
                enabled = shopProfile != null && profile != loaded && profile.repeatWindowDays in REPEAT_WINDOW_RANGE,
                onClick = { onSave(profile) }
            ) {
                Text("Save")
            }
        }
    ) {
        Text(
            text = "Shown on invoices and anything else customers see.",
            fontSize = 13.sp
        )

        if (shopProfile == null) {
            EmptyMessage("Couldn't load the shop profile")
            return@SectionCard
        }

        DialogTextRow(
            firstLabel = "Shop Name",
            firstValue = profile.name,
            onFirstValueChange = { profile = profile.copy(name = it) },
            secondLabel = "Phone",
            secondValue = profile.phone,
            onSecondValueChange = { profile = profile.copy(phone = it) }
        )

        DialogTextRow(
            firstLabel = "Email",
            firstValue = profile.email,
            onFirstValueChange = { profile = profile.copy(email = it) },
            secondLabel = "Website",
            secondValue = profile.website,
            onSecondValueChange = { profile = profile.copy(website = it) }
        )

        DialogTextRow(
            firstLabel = "Street",
            firstValue = profile.street,
            onFirstValueChange = { profile = profile.copy(street = it) },
            secondLabel = "City",
            secondValue = profile.city,
            onSecondValueChange = { profile = profile.copy(city = it) }
        )

        DialogTextRow(
            firstLabel = "State",
            firstValue = profile.state,
            onFirstValueChange = { profile = profile.copy(state = it) },
            secondLabel = "Zip",
            secondValue = profile.zip,
            onSecondValueChange = { profile = profile.copy(zip = it) }
        )

        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier.width(160.dp),
                value = profile.taxRate,
                onValueChange = { text -> profile = profile.copy(taxRate = text.filter { it.isDigit() || it == '.' }.take(6)) },
                label = { Text("Tax Rate %") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Spacer(Modifier.padding(8.dp))

            // Kept as text while typing so the field can be cleared, saved as a number
            var repeatDays by remember(loaded) { mutableStateOf(profile.repeatWindowDays.toString()) }

            OutlinedTextField(
                modifier = Modifier.width(220.dp),
                value = repeatDays,
                onValueChange = { text ->
                    repeatDays = text.filter { it.isDigit() }.take(4)
                    repeatDays.toIntOrNull()?.let { profile = profile.copy(repeatWindowDays = it) }
                },
                label = { Text("Repeat Repair Warning (days)") },
                isError = repeatDays.toIntOrNull() !in REPEAT_WINDOW_RANGE,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            value = profile.invoiceFooter,
            onValueChange = { profile = profile.copy(invoiceFooter = it) },
            label = { Text("Invoice Footer") },
            minLines = 2,
            maxLines = 4
        )
    }
}

// Days a repaired device can come back and be flagged as a repeat
private val REPEAT_WINDOW_RANGE = 1..3650
