package org.mr0btain.ticketman.ui.kit.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableHeader
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.navigation.TmStepper
import org.mr0btain.ticketman.ui.kit.containers.TmDialogSurface
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.containers.TmSwitchRow
import org.mr0btain.ticketman.ui.kit.containers.TmTabs
import org.mr0btain.ticketman.ui.kit.images.TmImagePager
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.data.TmCountBar
import org.mr0btain.ticketman.ui.kit.images.TmImage
import org.mr0btain.ticketman.ui.kit.data.TmStatTile
import org.mr0btain.ticketman.ui.kit.feedback.TmAccessBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmPriorityBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmStatusBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmDot
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.feedback.TmLoading
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.feedback.TmSnackbarContent
import org.mr0btain.ticketman.ui.kit.feedback.TmUserStatusBadge
import org.mr0btain.ticketman.ui.kit.feedback.tone
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.foundation.TmSurface
import org.mr0btain.ticketman.ui.kit.foundation.TmSurfaceLevel
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmPasswordField
import org.mr0btain.ticketman.ui.kit.inputs.TmPinField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.navigation.TmBottomNav
import org.mr0btain.ticketman.ui.kit.navigation.TmNavItem
import org.mr0btain.ticketman.ui.kit.navigation.TmNavRail
import org.mr0btain.ticketman.ui.kit.navigation.TmTopBar
import org.mr0btain.ticketman.ui.kit.search.TmSearchBar
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.selection.TmCheckbox
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.selection.TmFilterChip
import org.mr0btain.ticketman.ui.kit.selection.TmRadio
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.selection.TmSwitch
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.theme.TmTone
import ticketman.composeapp.generated.resources.*
import androidx.compose.ui.text.input.KeyboardType
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDeleteDialog
import org.mr0btain.ticketman.ui.kit.containers.TmTextEditDialog
import org.mr0btain.ticketman.ui.kit.images.TmPhotoEditor
import org.mr0btain.ticketman.ui.kit.navigation.TmPanelTopBar
import org.mr0btain.ticketman.ui.kit.notes.TmEditNoteDialog
import org.mr0btain.ticketman.ui.kit.notes.TmNoteBlock
import kotlin.uuid.Uuid

/** The gallery's sections in order. Two column layouts alternate them left and right */
internal fun gallerySections(): List<@Composable () -> Unit> = listOf(
    { CounterSampleSection() },
    { ButtonsSection() },
    { FieldsSection() },
    { SelectionSection() },
    { SearchSection() },
    { BadgesSection() },
    { ListsSection() },
    { TablesSection() },
    { FeedbackSection() },
    { DataSection() },
    { PhotosSection() },
    { NotesSection() },
    { DialogSection() },
    { NavigationSection() },
    { TypeSection() },
    { ColorsSection() }
)

// Made up shop data for the samples
private val sampleCustomers = listOf(
    "Dana Whitfield", "Marcus Oyelaran", "Priya Raman", "Tomás Herrera", "June Kowalski", "Dan Hollis"
).map { SearchResult(Uuid.random(), it) }

private data class SampleTicket(
    val number: String,
    val device: String,
    val customer: String,
    val checkedIn: String,
    val status: TicketStatusType,
    val priority: TicketPriorityType
)

private val sampleQueue = listOf(
    SampleTicket("26-00000041", "iPhone 13, cracked screen", "Dana Whitfield", "2h ago", TicketStatusType.IN_PROGRESS, TicketPriorityType.HIGH),
    SampleTicket("26-00000042", "Pixel 7, won't charge", "Marcus Oyelaran", "1h ago", TicketStatusType.ON_HOLD, TicketPriorityType.NORMAL),
    SampleTicket("26-00000043", "ThinkPad T14, keyboard", "Priya Raman", "40m ago", TicketStatusType.DONE, TicketPriorityType.LOW),
    SampleTicket("26-00000044", "PS5, HDMI port", "Tomás Herrera", "Yesterday", TicketStatusType.PICKED_UP, TicketPriorityType.URGENT)
)

// --------------------------------------- Real use ---------------------------------------

@Composable
private fun CounterSampleSection() {
    var status by remember { mutableStateOf(TicketStatusType.IN_PROGRESS) }
    var priority by remember { mutableStateOf(TicketPriorityType.HIGH) }
    var selected by remember { mutableIntStateOf(0) }
    var deposit by remember { mutableLongStateOf(4000L) }
    var customer by remember { mutableStateOf("Dana Whitfield") }
    var phone by remember { mutableStateOf("") }
    var deviceType by remember { mutableStateOf<String?>("Phone") }

    GallerySection(title = "At the counter", description = "Kit components put together the way the counter screen will use them") {
        Specimen("Queue") {
            TmSurface(modifier = Modifier.fillMaxWidth(), level = TmSurfaceLevel.SUNKEN) {
                Column(Modifier.padding(TmTheme.spacing.xs)) {
                    sampleQueue.forEachIndexed { index, ticket ->
                        TmListRow(
                            title = ticket.device,
                            subtitle = "${ticket.customer}, checked in ${ticket.checkedIn}",
                            selected = index == selected,
                            onClick = { selected = index },
                            leading = { TmTicketIdText(ticket.number, Modifier.width(104.dp)) },
                            trailing = { TmStatusBadge(ticket.status) }
                        )
                    }
                }
            }
        }

        Specimen("Ticket card") {
            TmPanel(
                modifier = Modifier.fillMaxWidth(),
                title = "26-00000041",
                subtitle = "iPhone 13, cracked screen",
                icon = Res.drawable.smartphone,
                actions = {
                    TmIconButton(icon = Res.drawable.printer, contentDescription = "Print claim slip", onClick = {})
                    TmButton(text = "Take payment", onClick = {}, leadingIcon = Res.drawable.dollar_sign)
                }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
                    TmDropdown(
                        modifier = Modifier.weight(1f),
                        label = "Status",
                        options = TicketStatusType.selectable,
                        selected = status,
                        onSelect = { status = it },
                        optionLabel = { formatEnumName(it.name) },
                        optionLeading = { TmDot(TmTheme.colors.tone(it).color) }
                    )
                    TmDropdown(
                        modifier = Modifier.weight(1f),
                        label = "Priority",
                        options = TicketPriorityType.entries,
                        selected = priority,
                        onSelect = { priority = it },
                        optionLabel = { formatEnumName(it.name) },
                        optionLeading = { TmDot(TmTheme.colors.tone(it).color) }
                    )
                }

                TmDivider()
                TmKeyValueRow(label = "Customer", value = "Dana Whitfield")
                TmKeyValueRow(label = "Checked in", value = "Today, 9:42 AM")
                TmKeyValueRow(label = "Deposit") { TmMoneyText(4000) }
                TmKeyValueRow(label = "Balance due") { TmMoneyText(14999, style = TmTheme.typography.numericLarge) }
            }
        }

        Specimen("Check-in row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md),
                verticalAlignment = Alignment.Bottom
            ) {
                TmTextField(modifier = Modifier.weight(1.4f), value = customer, onValueChange = { customer = it }, label = "Customer")
                TmTextField(modifier = Modifier.weight(1f), value = phone, onValueChange = { phone = it }, label = "Phone", placeholder = "555 0100")
                TmDropdown(
                    modifier = Modifier.weight(1f),
                    label = "Device",
                    options = listOf("Phone", "Tablet", "Laptop", "Console", "Other"),
                    selected = deviceType,
                    onSelect = { deviceType = it },
                    optionLabel = { it }
                )
                TmMoneyField(modifier = Modifier.weight(0.8f), cents = deposit, onCentsChange = { deposit = it }, label = "Deposit")
                TmButton(text = "Check in", onClick = {})
            }
        }
    }
}

// --------------------------------------- Buttons ---------------------------------------

@Composable
private fun ButtonsSection() {
    GallerySection(title = "Buttons", description = "Primary for the one main action, ghost for the quiet ones") {
        TmControlSize.entries.forEach { size ->
            Specimen(formatEnumName(size.name)) {
                TmButtonVariant.entries.forEach { variant ->
                    TmButton(text = formatEnumName(variant.name), onClick = {}, variant = variant, size = size)
                }
            }
        }

        Specimen("With icons, loading and disabled") {
            TmButton(text = "New ticket", onClick = {}, leadingIcon = Res.drawable.plus)
            TmButton(text = "Print", onClick = {}, variant = TmButtonVariant.SECONDARY, leadingIcon = Res.drawable.printer)
            TmButton(text = "Saving", onClick = {}, loading = true)
            TmButton(text = "Delete ticket", onClick = {}, variant = TmButtonVariant.DANGER, leadingIcon = Res.drawable.trash_2)
            TmButton(text = "Disabled", onClick = {}, enabled = false)
        }

        Specimen("Icon buttons") {
            TmIconButton(icon = Res.drawable.edit_2, contentDescription = "Edit", onClick = {})
            TmIconButton(icon = Res.drawable.printer, contentDescription = "Print", onClick = {}, variant = TmButtonVariant.SECONDARY)
            TmIconButton(icon = Res.drawable.plus, contentDescription = "Add", onClick = {}, variant = TmButtonVariant.PRIMARY)
            TmIconButton(icon = Res.drawable.trash_2, contentDescription = "Delete", onClick = {}, variant = TmButtonVariant.DANGER)
            TmIconButton(icon = Res.drawable.refresh_cw, contentDescription = "Refresh", onClick = {}, size = TmControlSize.SMALL)
        }

        Specimen("Dialog actions") {
            TmButtonRow(confirmText = "Save changes", onConfirm = {}, onCancel = {})
        }
    }
}

// --------------------------------------- Fields ---------------------------------------

@Composable
private fun FieldsSection() {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("dana@example.com") }
    var notes by remember { mutableStateOf("Screen cracked in the top corner, touch still works. Customer wants the old screen back.") }
    var price by remember { mutableLongStateOf(12999L) }
    var pin by remember { mutableStateOf("1234") }
    var password by remember { mutableStateOf("bench") }

    GallerySection(title = "Fields", description = "Label above, help or error below") {
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
            TmTextField(modifier = Modifier.weight(1f), value = name, onValueChange = { name = it }, label = "Name", placeholder = "First and last name")
            TmTextField(
                modifier = Modifier.weight(1f),
                value = email,
                onValueChange = { email = it },
                label = "Email",
                leadingIcon = Res.drawable.mail,
                supportingText = "Receipts go here"
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
            TmTextField(
                modifier = Modifier.weight(1f),
                value = "555 01",
                onValueChange = {},
                label = "Phone",
                error = "Phone numbers need 10 digits"
            )
            TmTextField(modifier = Modifier.weight(1f), value = "Locked by admin", onValueChange = {}, label = "Disabled", enabled = false)
        }

        TmTextField(value = notes, onValueChange = { notes = it }, label = "Notes", singleLine = false, minLines = 3)

        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md), verticalAlignment = Alignment.Top) {
            TmMoneyField(modifier = Modifier.weight(1f), cents = price, onCentsChange = { price = it }, label = "Price", supportingText = "Type digits, cents fill in first")
            TmPinField(modifier = Modifier.weight(1f), value = pin, onValueChange = { pin = it })
            TmPasswordField(modifier = Modifier.weight(1f), value = password, onValueChange = { password = it }, showStrength = true)
        }

        Specimen("Large, for touch screens") {
            TmTextField(
                modifier = Modifier.width(320.dp),
                value = "",
                onValueChange = {},
                placeholder = "Customer name",
                size = TmControlSize.LARGE
            )
            TmButton(text = "Find customer", onClick = {}, size = TmControlSize.LARGE)
        }
    }
}

// --------------------------------------- Selection ---------------------------------------

@Composable
private fun SelectionSection() {
    var status by remember { mutableStateOf<TicketStatusType?>(null) }
    var view by remember { mutableStateOf("Queue") }
    var openOnly by remember { mutableStateOf(true) }
    var mine by remember { mutableStateOf(false) }
    var urgent by remember { mutableStateOf(false) }
    var kiosk by remember { mutableStateOf(true) }
    var tax by remember { mutableStateOf(true) }
    var payment by remember { mutableStateOf("Card") }

    GallerySection(title = "Choosing", description = "Dropdowns, segments, chips and toggles") {
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
            TmDropdown(
                modifier = Modifier.weight(1f),
                label = "Status",
                options = TicketStatusType.entries,
                selected = status,
                onSelect = { status = it },
                optionLabel = { formatEnumName(it.name) },
                optionLeading = { TmDot(TmTheme.colors.tone(it).color) }
            )
            TmDropdown(
                modifier = Modifier.weight(1f),
                label = "Disabled",
                options = listOf("Only option"),
                selected = "Only option",
                onSelect = {},
                optionLabel = { it },
                enabled = false
            )
        }

        Specimen("Segmented") {
            TmSegmented(options = listOf("Queue", "Ready", "All"), selected = view, onSelect = { view = it }, optionLabel = { it })
        }

        Specimen("Filter chips") {
            TmFilterChip(text = "Open only", selected = openOnly, onClick = { openOnly = !openOnly })
            TmFilterChip(text = "Mine", selected = mine, onClick = { mine = !mine }, leadingIcon = Res.drawable.user)
            TmFilterChip(text = "Urgent", selected = urgent, onClick = { urgent = !urgent }, leadingIcon = Res.drawable.alert_triangle)
        }

        Specimen("Toggles") {
            TmSwitch(checked = kiosk, onCheckedChange = { kiosk = it }, contentDescription = "Kiosk mode")
            TmSwitch(checked = false, onCheckedChange = {}, enabled = false)
            TmCheckbox(checked = tax, onCheckedChange = { tax = it }, label = "Charge tax")
            TmCheckbox(checked = false, onCheckedChange = {}, label = "Disabled", enabled = false)
        }

        Specimen("Radio") {
            listOf("Card", "Cash", "Store credit").forEach { option ->
                TmRadio(selected = payment == option, onClick = { payment = option }, label = option)
            }
        }
    }
}

// --------------------------------------- Search ---------------------------------------

@Composable
private fun SearchSection() {
    var query by remember { mutableStateOf("") }

    GallerySection(title = "Search", description = "Matches show as you type, the matching letters are highlighted") {
        TmSearchBar(
            label = "Customer",
            placeholder = "Name, phone or email",
            onQuery = { text ->
                delay(250)
                sampleCustomers.filter { it.text.contains(text, ignoreCase = true) }
            },
            onPick = {}
        )

        TmSearchField(query = query, onQueryChange = { query = it }, placeholder = "Search tickets")
        TmSearchField(query = "iphone", onQueryChange = {}, placeholder = "Search", loading = true)
    }
}

// --------------------------------------- Badges ---------------------------------------

@Composable
private fun BadgesSection() {
    GallerySection(title = "Badges", description = "Each state keeps its color in every theme") {
        Specimen("Ticket status") { TicketStatusType.entries.forEach { TmStatusBadge(it) } }
        Specimen("Ticket status, solid") { TicketStatusType.entries.forEach { TmStatusBadge(it, solid = true) } }
        Specimen("Priority") { TicketPriorityType.entries.forEach { TmPriorityBadge(it) } }
        Specimen("Access level") { AccessLevel.entries.forEach { TmAccessBadge(it) } }
        Specimen("Account") { UserStatus.entries.forEach { TmUserStatusBadge(it) } }
    }
}

// --------------------------------------- Lists ---------------------------------------

@Composable
private fun ListsSection() {
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(1) }
    var remember by remember { mutableStateOf(true) }

    GallerySection(title = "Lists and tabs") {
        TmTabs(tabs = listOf("Customers", "Tickets", "Inventory"), selectedIndex = tab, onSelect = { tab = it }, badges = mapOf(1 to 12))

        Column {
            listOf("Dana Whitfield", "Marcus Oyelaran", "Priya Raman").forEachIndexed { index, name ->
                TmListRow(
                    title = name,
                    subtitle = "${index + 2} tickets, last visit ${index + 1}w ago",
                    icon = Res.drawable.user,
                    selected = index == selected,
                    onClick = { selected = index },
                    trailing = { Text("${index + 1} open", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted) }
                )
            }
        }

        TmDivider()

        TmSwitchRow(
            title = "Remember login",
            subtitle = "Save username and password after logging in",
            icon = Res.drawable.lock,
            checked = remember,
            onCheckedChange = { remember = it }
        )
        TmListRow(title = "Start tab", subtitle = "Main screen tab shown on start", icon = Res.drawable.home, onClick = {}, trailing = {
            Text("Customers", style = TmTheme.typography.body, color = TmTheme.colors.textMuted)
        })
    }
}

// --------------------------------------- Tables and steps ---------------------------------------

private val sampleColumns = listOf(
    TmColumn("Ticket", width = 112.dp),
    TmColumn("Device"),
    TmColumn("Status", width = 136.dp),
    TmColumn("Total", width = 88.dp, end = true)
)

@Composable
private fun TablesSection() {
    var selected by remember { mutableIntStateOf(0) }
    var step by remember { mutableIntStateOf(1) }
    var confirming by remember { mutableStateOf(false) }

    GallerySection(title = "Tables and steps", description = "Lists of records, and forms filled in order") {
        TmPageHeader(
            title = "Invoices",
            subtitle = "Page header, with an optional back button and actions",
            onBack = {},
            actions = { TmButton(text = "Refresh", onClick = {}, variant = TmButtonVariant.SECONDARY, leadingIcon = Res.drawable.refresh_cw) }
        )

        TmSurface(level = TmSurfaceLevel.SUNKEN, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(TmTheme.spacing.sm)) {
                TmTableHeader(sampleColumns)
                sampleQueue.forEachIndexed { index, ticket ->
                    TmTableRow(sampleColumns, selected = index == selected, onClick = { selected = index }) { column ->
                        when (column) {
                            0 -> TmTicketIdText(ticket.number)
                            1 -> Text(ticket.device, style = TmTheme.typography.label, color = TmTheme.colors.text)
                            2 -> TmStatusBadge(ticket.status)
                            3 -> TmMoneyText(14999L - index * 2500)
                        }
                    }
                }
            }
        }

        TmStepper(
            steps = listOf("Customer", "Device", "Repair", "Sign", "Review"),
            currentIndex = step,
            onSelect = { step = it },
            canOpen = { it <= step + 1 }
        )

        TmFieldPair(
            { TmTextField(modifier = it, value = "", onValueChange = {}, label = "First name") },
            { TmTextField(modifier = it, value = "", onValueChange = {}, label = "Last name") }
        )

        TmButton(text = "Ask before deleting", onClick = { confirming = true }, variant = TmButtonVariant.SECONDARY)

        if (confirming) {
            TmConfirmDialog(
                title = "Delete this customer?",
                message = "This can't be undone.",
                confirmText = "Delete customer",
                destructive = true,
                onConfirm = { confirming = false },
                onDismissRequest = { confirming = false }
            )
        }
    }
}

// --------------------------------------- Feedback ---------------------------------------

@Composable
private fun FeedbackSection() {
    GallerySection(title = "Messages", description = "Say what happened and what to do next") {
        TmNotice(kind = TmNoticeKind.INFO, text = "This station is waiting for an admin to approve it.")
        TmNotice(kind = TmNoticeKind.SUCCESS, text = "Payment of $149.99 taken, ticket closed.")
        TmNotice(kind = TmNoticeKind.WARNING, title = "Parts low", text = "Only 2 iPhone 13 screens left in stock.")
        TmNotice(kind = TmNoticeKind.ERROR, title = "Couldn't reach the server", text = "Check the server address in Settings, then try again.")

        Specimen("Snackbar") {
            TmSnackbarContent(message = "Ticket 26-00000041 saved", actionLabel = "Undo")
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
            TmSurface(Modifier.weight(1f), level = TmSurfaceLevel.SUNKEN) {
                TmEmptyState(
                    icon = Res.drawable.inbox,
                    title = "No tickets waiting",
                    message = "Checked in devices show up here.",
                    actionText = "Check in a device"
                )
            }
            TmSurface(Modifier.weight(1f), level = TmSurfaceLevel.SUNKEN) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TmLoading(text = "Loading tickets")
                }
            }
        }
    }
}

// --------------------------------------- Data ---------------------------------------

@Composable
private fun DataSection() {
    GallerySection(title = "Numbers", description = "Amounts and ticket numbers use the monospace face") {
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
            TmStatTile(modifier = Modifier.weight(1f), label = "Open tickets", value = "18", icon = Res.drawable.tool, caption = "4 waiting on parts")
            TmStatTile(modifier = Modifier.weight(1f), label = "Taken today", value = "$1,284", icon = Res.drawable.dollar_sign, caption = "11 payments")
            TmStatTile(modifier = Modifier.weight(1f), label = "Low stock", value = "3", icon = Res.drawable.package_icon, caption = "Needs ordering", attention = true)
        }

        Column {
            TmCountBar(label = "In progress", count = 9, total = 18, color = TmTheme.colors.warning.color)
            TmCountBar(label = "On hold", count = 4, total = 18, color = TmTheme.colors.danger.color)
            TmCountBar(label = "Done", count = 5, total = 18, color = TmTheme.colors.success.color)
        }

        TmDivider()

        TmKeyValueRow(label = "Ticket") { TmTicketIdText("26-00000041") }
        TmKeyValueRow(label = "Invoice") { TmTicketIdText("INV-26-00000041") }
        TmKeyValueRow(label = "Refund") { TmMoneyText(-2500, signed = true) }
        TmKeyValueRow(label = "SKU", value = "SCR-IP13-BLK", numeric = true)
    }
}

// --------------------------------------- Photos ---------------------------------------

@Composable
private fun PhotosSection() {
    val photos = remember {
        listOf(
            StoredImage(description = "Cracked back glass", width = 4032, height = 3024),
            StoredImage(description = "Water damage indicator", width = 3024, height = 4032),
            StoredImage(description = "Serial number", width = 1600, height = 1200)
        )
    }

    GallerySection(
        title = "Photos",
        description = "Loaded from the server through the cache. Here, with no server, they show their placeholder"
    ) {
        Specimen("Thumbnails keep the photo's shape") {
            photos.forEach { photo -> TmImage(image = photo, modifier = Modifier.height(96.dp), keepAspectRatio = true) }
        }

        Specimen("Pager, with remove for editing") {
            TmImagePager(modifier = Modifier.fillMaxWidth().height(160.dp), storedImages = photos, onRemove = {})
        }

        Specimen("Editor, picked photos upload straight away") {
            TmPhotoEditor(modifier = Modifier.fillMaxWidth(), images = photos.take(2), onAdd = {}, onRemove = {})
        }
    }
}

// --------------------------------------- Notes ---------------------------------------

@Composable
private fun NotesSection() {
    val notes = remember {
        listOf(
            Note(author = "Dana Whitfield", message = "Back glass cracked, frame is fine. Ordered a new panel."),
            Note(
                author = "Marcus Oyelaran",
                message = "Customer dropped it in water last week, indicator is tripped.",
                images = listOf(StoredImage(description = "Water damage indicator", width = 3024, height = 4032))
            )
        )
    }
    val noteSeed = remember { NoteSeed() }
    var editing by remember { mutableStateOf(false) }

    GallerySection(title = "Notes", description = "Updates on a record, newest first, with their photos") {
        TmNoteBlock(modifier = Modifier.fillMaxWidth(), notes = notes, editable = true, onEditClick = { note ->
            noteSeed.setNote(note)
            editing = true
        })

        TmNoteBlock(notes = emptyList(), emptyText = "No updates yet")

        TmButton(
            text = "Add a note",
            variant = TmButtonVariant.SECONDARY,
            onClick = {
                noteSeed.clear()
                editing = true
            }
        )

        TmEditNoteDialog(
            seed = noteSeed,
            label = if (noteSeed.isNew) "Add note" else "Edit note",
            isVisible = editing,
            onConfirm = { editing = false },
            onDismissRequest = { editing = false },
            onDelete = {}
        )
    }
}

// --------------------------------------- Dialog ---------------------------------------

@Composable
private fun DialogSection() {
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(false) }

    GallerySection(title = "Dialog", description = "Glass sheet, actions at the bottom right") {
        TmDialogSurface(
            modifier = Modifier.fillMaxWidth(),
            scrollable = false,
            title = "Delete ticket 26-00000041?",
            description = "Its notes and photos are deleted too. This can't be undone.",
            actions = {
                TmButtonRow(confirmText = "Delete ticket", confirmVariant = TmButtonVariant.DANGER, onConfirm = {}, onCancel = {})
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
            TmButton(text = "Open as a real dialog", onClick = { open = true }, variant = TmButtonVariant.SECONDARY)
            TmButton(text = "Two step delete", onClick = { confirmDelete = true }, variant = TmButtonVariant.SECONDARY)
            TmButton(text = "Edit one value", onClick = { editText = true }, variant = TmButtonVariant.SECONDARY)
        }

        TmConfirmDeleteDialog(
            itemName = "Customer",
            isTwoStep = true,
            isVisible = confirmDelete,
            onConfirm = { confirmDelete = false },
            onCancel = { confirmDelete = false }
        )

        TmTextEditDialog(
            label = "Shop name",
            value = "Fix It Shop",
            visible = editText,
            onConfirm = { editText = false; true },
            onCancel = { editText = false },
            keyboardType = KeyboardType.Text,
            hint = "Shown on receipts and invoices"
        )

        if (open) {
            TmDialog(
                title = "Change PIN",
                description = "Used to sign in at counter stations.",
                onDismissRequest = { open = false },
                actions = { TmButtonRow(confirmText = "Save PIN", onConfirm = { open = false }, onCancel = { open = false }) }
            ) {
                TmPasswordField(value = "", onValueChange = {}, label = "Current password")
                TmPinField(value = "", onValueChange = {}, label = "New PIN")
            }
        }
    }
}

// --------------------------------------- Navigation ---------------------------------------

private val sampleNav = listOf(
    TmNavItem("counter", "Counter", Res.drawable.monitor),
    TmNavItem("main", "Main", Res.drawable.home),
    TmNavItem("invoice", "Invoices", Res.drawable.file_text),
    TmNavItem("admin", "Admin", Res.drawable.archive),
    TmNavItem("settings", "Settings", Res.drawable.settings)
)

@Composable
private fun NavigationSection() {
    var selected by remember { mutableStateOf("counter") }

    GallerySection(title = "Navigation", description = "Glass bars that float over the screen") {
        TmTopBar(
            title = "Counter",
            subtitle = "Front desk station",
            navigation = { TmIconButton(icon = Res.drawable.sidebar, contentDescription = "Show or hide the sidebar", onClick = {}) },
            actions = { TmIconButton(icon = Res.drawable.bell, contentDescription = "Notifications", onClick = {}) }
        )

        Row(Modifier.height(400.dp), horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
            TmNavRail(items = sampleNav, selectedKey = selected, onSelect = { selected = it.key })
            TmNavRail(
                items = sampleNav,
                selectedKey = selected,
                onSelect = { selected = it.key },
                expanded = true,
                footer = { TmListRow(title = "Dana W.", subtitle = "Admin", icon = Res.drawable.user) }
            )
        }

        TmBottomNav(items = sampleNav.take(4), selectedKey = selected, onSelect = { selected = it.key })

        TmPanelTopBar(
            label = "Customer",
            onSearchClick = {},
            onSearchQuery = { query -> sampleCustomers.filter { it.text.contains(query, ignoreCase = true) } },
            onAddClick = {},
            onDeleteClick = {}
        )

        // What a restricted user sees
        TmPanelTopBar(
            label = "Customer",
            onSearchClick = {},
            onSearchQuery = { query -> sampleCustomers.filter { it.text.contains(query, ignoreCase = true) } },
            onAddClick = {},
            onDeleteClick = {},
            canChange = false
        )
    }
}

// --------------------------------------- Theme ---------------------------------------

@Composable
private fun ColorsSection() {
    val colors = TmTheme.colors

    GallerySection(title = "Colors", description = "The current theme's layers and tones") {
        Specimen("Layers, back to front") {
            Swatch("Background", colors.background)
            Swatch("Surface", colors.surface)
            Swatch("Raised", colors.surfaceRaised)
            Swatch("Sunken", colors.surfaceSunken)
            Swatch("Glass", colors.glass)
        }

        Specimen("Text") {
            Swatch("Text", colors.text)
            Swatch("Muted", colors.textMuted)
            Swatch("Faint", colors.textFaint)
        }

        Specimen("Tones") {
            ToneSwatch("Accent", colors.accent)
            ToneSwatch("Success", colors.success)
            ToneSwatch("Warning", colors.warning)
            ToneSwatch("Danger", colors.danger)
            ToneSwatch("Neutral", colors.neutral)
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
        Box(
            Modifier
                .size(width = 72.dp, height = 40.dp)
                .background(color, TmTheme.shapes.chip)
                .border(1.dp, TmTheme.colors.hairline, TmTheme.shapes.chip)
        )
        Text(name, style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
    }
}

@Composable
private fun ToneSwatch(name: String, tone: TmTone) {
    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
        Row {
            Box(Modifier.size(width = 36.dp, height = 40.dp).background(tone.color, TmTheme.shapes.chip))
            Box(
                Modifier.size(width = 36.dp, height = 40.dp).background(tone.soft, TmTheme.shapes.chip),
                contentAlignment = Alignment.Center
            ) {
                Text("Aa", style = TmTheme.typography.label, color = tone.color)
            }
        }
        Text(name, style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
    }
}

@Composable
private fun TypeSection() {
    val type = TmTheme.typography
    val color = TmTheme.colors.text

    GallerySection(title = "Type", description = "Five sizes: 28, 20, 16, 14 and 12") {
        Text("Display, 28", style = type.display, color = color)
        Text("Title, 20", style = type.title, color = color)
        Text("Subtitle, 16", style = type.subtitle, color = color)
        Text("Body large, 16. Screen replaced, tested touch and Face ID.", style = type.bodyLarge, color = color)
        Text("Body, 14. Customer will pick up after 5pm, call before closing.", style = type.body, color = color)
        Text("Label, 14", style = type.label, color = color)
        Text("Caption, 12", style = type.caption, color = TmTheme.colors.textMuted)
        TmDivider()
        Text("26-00000041   $149.99   SCR-IP13-BLK", style = type.numeric, color = color)
        Text("$1,284.50", style = type.numericDisplay, color = color)
    }
}
