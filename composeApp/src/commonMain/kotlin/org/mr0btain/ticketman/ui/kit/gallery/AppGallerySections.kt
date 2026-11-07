package org.mr0btain.ticketman.ui.kit.gallery

import org.mr0btain.uikit.gallery.GallerySection
import org.mr0btain.uikit.gallery.KitGallery
import org.mr0btain.uikit.gallery.Specimen
import org.mr0btain.ticketman.ui.kit.data.TmPrintPreview
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.components.print.PrintDocument
import org.mr0btain.ticketman.components.print.PrintBlock
import org.mr0btain.ticketman.components.print.AmountRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.search.TmSearchResult
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.CustomStatus
import org.mr0btain.ticketman.data.ticket.StatusChoice
import org.mr0btain.ticketman.data.ticket.StatusTone
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.uikit.buttons.TmButton
import org.mr0btain.uikit.buttons.TmButtonRow
import org.mr0btain.uikit.buttons.TmButtonVariant
import org.mr0btain.uikit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmAdjustBalanceDialog
import org.mr0btain.uikit.containers.TmDialog
import org.mr0btain.uikit.data.TmColumn
import org.mr0btain.uikit.data.TmTableHeader
import org.mr0btain.uikit.data.TmTableRow
import org.mr0btain.uikit.containers.TmListRow
import org.mr0btain.uikit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.images.TmImagePager
import org.mr0btain.uikit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmQrCode
import org.mr0btain.ticketman.ui.kit.data.TmScheduleBlock
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.images.TmImage
import org.mr0btain.ticketman.ui.kit.images.TmAvatar
import org.mr0btain.ticketman.ui.kit.feedback.TmAccessBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmPriorityBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmStatusBadge
import org.mr0btain.ticketman.ui.kit.feedback.LocalTicketStatuses
import org.mr0btain.ticketman.ui.kit.feedback.LocalLocations
import org.mr0btain.ticketman.ui.kit.feedback.TmLocations
import org.mr0btain.ticketman.ui.kit.feedback.TmLocationBadge
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.uikit.feedback.TmDot
import org.mr0btain.ticketman.ui.kit.feedback.TmUserStatusBadge
import org.mr0btain.ticketman.ui.kit.feedback.tone
import org.mr0btain.uikit.foundation.TmDivider
import org.mr0btain.uikit.foundation.TmSurface
import org.mr0btain.uikit.foundation.TmSurfaceLevel
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.uikit.inputs.TmPasswordField
import org.mr0btain.ticketman.ui.kit.inputs.TmPinField
import org.mr0btain.uikit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.inputs.TmCustomerForm
import org.mr0btain.ticketman.ui.kit.inputs.TmMessageConsent
import org.mr0btain.ticketman.ui.kit.inputs.TmCustomerDialog
import org.mr0btain.ticketman.ui.kit.search.TmSearchBar
import org.mr0btain.uikit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.selection.TmStatusPicker
import org.mr0btain.uikit.theme.TmTheme
import ticketman.composeapp.generated.resources.*
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.ui.kit.images.TmPhotoEditor
import org.mr0btain.ticketman.ui.kit.navigation.TmPanelTopBar
import org.mr0btain.ticketman.ui.kit.notes.TmEditNoteDialog
import org.mr0btain.ticketman.ui.kit.notes.TmNoteBlock
import kotlin.uuid.Uuid

/** TicketMan's own components, shown after the kit's in the gallery (KitGallery's extraSections) */
fun appGallerySections(): List<@Composable () -> Unit> = listOf(
    { CounterSampleSection() },
    { TicketFieldsSection() },
    { TicketChoosingSection() },
    { SearchBarSection() },
    { StateBadgesSection() },
    { TicketTableSection() },
    { PrintSection() },
    { PhotosSection() },
    { NotesSection() },
    { TicketDialogsSection() },
    { PanelTopBarSection() }
)

// Made up shop data for the samples
private val sampleCustomers = listOf(
    "Dana Whitfield", "Marcus Oyelaran", "Priya Raman", "Tomás Herrera", "June Kowalski", "Dan Hollis"
).map { TmSearchResult(Uuid.random(), it) }

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

// Statuses a shop might add, for the badges and the status picker
private val sampleStatuses = listOf(
    CustomStatus(name = "Diagnosing", actsAs = TicketStatusType.IN_PROGRESS, tone = StatusTone.ACCENT),
    CustomStatus(name = "Awaiting parts", actsAs = TicketStatusType.ON_HOLD),
    CustomStatus(name = "Awaiting customer", actsAs = TicketStatusType.ON_HOLD, tone = StatusTone.NEUTRAL, showCustomer = false)
)

private val sampleLocations = TmLocations(
    all = listOf(Location(name = "Main Street", main = true), Location(code = "DT", name = "Downtown"), Location(code = "NS", name = "Northside"))
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

// --------------------------------------- Fields ---------------------------------------

@Composable
private fun TicketFieldsSection() {
    var price by remember { mutableLongStateOf(12999L) }
    var pin by remember { mutableStateOf("1234") }
    var customerDialog by remember { mutableStateOf(false) }

    GallerySection(title = "TicketMan fields", description = "Money, PINs and customers") {
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md), verticalAlignment = Alignment.Top) {
            TmMoneyField(modifier = Modifier.weight(1f), cents = price, onCentsChange = { price = it }, label = "Price", supportingText = "Type digits, cents fill in first")
            TmPinField(modifier = Modifier.weight(1f), value = pin, onValueChange = { pin = it })
        }

        Specimen("New customer, on a panel or as TmCustomerDialog") {
            TmCustomerForm(
                modifier = Modifier.width(560.dp),
                onSave = { _, onResult -> onResult(null) },
                onCancel = {}
            )
            TmButton(text = "Open as a dialog", onClick = { customerDialog = true }, variant = TmButtonVariant.SECONDARY)
        }
    }

    if (customerDialog) {
        TmCustomerDialog(
            onSave = { _, onResult ->
                onResult(null)
                customerDialog = false
            },
            onDismissRequest = { customerDialog = false }
        )
    }
}

// --------------------------------------- Selection ---------------------------------------

@Composable
private fun TicketChoosingSection() {
    var status by remember { mutableStateOf<TicketStatusType?>(null) }
    var consentEmail by remember { mutableStateOf(true) }
    var consentSms by remember { mutableStateOf(false) }
    var builtIn by remember { mutableStateOf(StatusChoice(TicketStatusType.IN_PROGRESS)) }
    var custom by remember { mutableStateOf(sampleStatuses[1].choice) }

    GallerySection(title = "Ticket choices", description = "Statuses and message consent") {
        TmDropdown(
            label = "Status",
            options = TicketStatusType.entries,
            selected = status,
            onSelect = { status = it },
            optionLabel = { formatEnumName(it.name) },
            optionLeading = { TmDot(TmTheme.colors.tone(it).color) }
        )

        Specimen("Ticket status, built-in only and with the shop's own") {
            TmStatusPicker(selected = builtIn, onSelect = { builtIn = it })
            CompositionLocalProvider(LocalTicketStatuses provides sampleStatuses) {
                TmStatusPicker(modifier = Modifier.width(240.dp), selected = custom, onSelect = { custom = it })
            }
        }

        Specimen("Message consent") {
            TmMessageConsent(email = consentEmail, sms = consentSms, onEmailChange = { consentEmail = it }, onSmsChange = { consentSms = it }, hasEmail = true, hasPhone = true)
        }
    }
}

// --------------------------------------- Search ---------------------------------------

@Composable
private fun SearchBarSection() {
    GallerySection(title = "Record search", description = "Matches show as you type, the matching letters are highlighted") {
        TmSearchBar(
            label = "Customer",
            placeholder = "Name, phone or email",
            onQuery = { text ->
                delay(250)
                sampleCustomers.filter { it.text.contains(text, ignoreCase = true) }
            },
            onPick = {}
        )
    }
}

// --------------------------------------- Badges ---------------------------------------

@Composable
private fun StateBadgesSection() {
    GallerySection(title = "State badges", description = "Each state keeps its color in every theme") {
        Specimen("Ticket status") { TicketStatusType.entries.forEach { TmStatusBadge(it) } }
        Specimen("Ticket status, solid") { TicketStatusType.entries.forEach { TmStatusBadge(it, solid = true) } }
        Specimen("Ticket status, the shop's own") {
            CompositionLocalProvider(LocalTicketStatuses provides sampleStatuses) {
                sampleStatuses.forEach { TmStatusBadge(it.choice) }
            }
        }
        Specimen("Priority") { TicketPriorityType.entries.forEach { TmPriorityBadge(it) } }
        Specimen("Access level") { AccessLevel.entries.forEach { TmAccessBadge(it) } }
        Specimen("Account") { UserStatus.entries.forEach { TmUserStatusBadge(it) } }
        Specimen("Location, with more than one") {
            CompositionLocalProvider(LocalLocations provides sampleLocations) {
                sampleLocations.all.forEach { TmLocationBadge(it.identifier, always = true) }
            }
        }
    }
}

// --------------------------------------- Tables ---------------------------------------

private val sampleColumns = listOf(
    TmColumn("Ticket", width = 112.dp),
    TmColumn("Device"),
    TmColumn("Status", width = 136.dp),
    TmColumn("Total", width = 88.dp, end = true)
)

@Composable
private fun TicketTableSection() {
    var selected by remember { mutableIntStateOf(0) }

    GallerySection(title = "Tickets table", description = "Ticket numbers and money in the monospace face") {
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

        TmKeyValueRow(label = "Ticket") { TmTicketIdText("26-00000041") }
        TmKeyValueRow(label = "Invoice") { TmTicketIdText("INV-26-00000041") }
        TmKeyValueRow(label = "Refund") { TmMoneyText(-2500, signed = true) }
    }
}

// --------------------------------------- Print ---------------------------------------

@Composable
private fun PrintSection() {
    GallerySection(title = "Print, codes and the calendar", description = "Receipts, QR codes and schedule blocks") {
        TmPrintPreview(
            document = PrintDocument(
                title = "Sample",
                blocks = listOf(
                    PrintBlock.Heading("Bench Repair"),
                    PrintBlock.Field("Invoice", "INV-26-00000042"),
                    PrintBlock.Divider,
                    PrintBlock.Amounts(listOf(AmountRow("Screen", "$89.00"), AmountRow("Total", "$89.00", bold = true)))
                )
            ),
            paper = PaperSize.RECEIPT_80MM
        )

        TmDivider()

        // A payment link or status page, for the customer's phone at the counter
        TmQrCode(text = "https://repairs.example.com/status/0123456789abcdef0123456789abcdef", size = 120.dp)

        TmDivider()

        // Shifts and time off on the calendar, squared off where they carry on past the edge
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
            TmScheduleBlock(title = "Shift", subtitle = "9:00 to 17:00, 8h", tone = TmTheme.colors.accent, modifier = Modifier.weight(1f))
            TmScheduleBlock(title = "Time off", subtitle = "Vacation", tone = TmTheme.colors.warning, modifier = Modifier.weight(2f), continuesAfter = true)
            TmScheduleBlock(title = "Training", tone = TmTheme.colors.success, modifier = Modifier.weight(1f), continuesBefore = true)
        }
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

        Specimen("Avatars: a profile photo, or initials without one") {
            TmAvatar(name = "Dana Whitfield", photo = photos.first())
            TmAvatar(name = "Dana Whitfield", photo = null)
            TmAvatar(name = "priya", photo = null)
            TmAvatar(name = "Marcus Oyelaran", photo = null, size = 72.dp)
        }
    }
}

// --------------------------------------- Notes ---------------------------------------

@Composable
private fun NotesSection() {
    val notes = remember {
        listOf(
            Note(author = "Dana Whitfield", message = "Back glass cracked, frame is fine. Ordered a new panel.", customerVisible = true),
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
            onDelete = {},
            canShowCustomer = true
        )
    }
}

// --------------------------------------- Dialog ---------------------------------------

@Composable
private fun TicketDialogsSection() {
    var adjust by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf(false) }

    GallerySection(title = "TicketMan dialogs", description = "Balances and PINs") {
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
            TmButton(text = "Adjust a balance", onClick = { adjust = true }, variant = TmButtonVariant.SECONDARY)
            TmButton(text = "Change PIN", onClick = { pin = true }, variant = TmButtonVariant.SECONDARY)
        }

        if (adjust) {
            TmAdjustBalanceDialog(
                title = "Store credit for Dana",
                balanceCents = 2500,
                onSave = { _, _, done -> done(null) },
                onDismissRequest = { adjust = false }
            )
        }

        if (pin) {
            TmDialog(
                title = "Change PIN",
                description = "Used to sign in at counter stations.",
                onDismissRequest = { pin = false },
                actions = { TmButtonRow(confirmText = "Save PIN", onConfirm = { pin = false }, onCancel = { pin = false }) }
            ) {
                TmPasswordField(value = "", onValueChange = {}, label = "Current password")
                TmPinField(value = "", onValueChange = {}, label = "New PIN")
            }
        }
    }
}

// --------------------------------------- Navigation ---------------------------------------

@Composable
private fun PanelTopBarSection() {
    GallerySection(title = "Records toolbar", description = "Search, add and delete over a records list") {
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
