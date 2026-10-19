package org.mr0btain.ticketman.ui

import org.mr0btain.ticketman.components.network.UntrustedCertificate
import org.mr0btain.ticketman.ui.screens.login.ServerTrustDialog
import org.mr0btain.ticketman.components.data.ticket.TicketScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.invoice.Charge
import org.mr0btain.ticketman.data.invoice.ChargeType
import org.mr0btain.ticketman.data.invoice.Invoice
import org.mr0btain.ticketman.data.invoice.InvoiceNumber
import org.mr0btain.ticketman.data.invoice.Payment
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.station.StaffMember
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketStatusChange
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import org.mr0btain.ticketman.server.response.BackupManifest
import org.mr0btain.ticketman.server.response.RestoreRecord
import org.mr0btain.ticketman.server.response.ScheduledBackupInfo
import org.mr0btain.ticketman.server.response.ServerCertificateInfo
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import org.mr0btain.ticketman.ui.screens.admin.server.AdminServerTab
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import org.mr0btain.ticketman.server.response.DailyReport
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.foundation.TmBackdrop
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.glassSource
import org.mr0btain.ticketman.ui.kit.navigation.TmNavItem
import org.mr0btain.ticketman.ui.kit.navigation.TmNavRail
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.admin.overview.AdminOverviewTab
import org.mr0btain.ticketman.ui.screens.counter.CheckInWizard
import org.mr0btain.ticketman.ui.screens.counter.CounterLayout
import org.mr0btain.ticketman.ui.screens.counter.PickupScreen
import org.mr0btain.ticketman.ui.screens.counter.TicketCard
import org.mr0btain.ticketman.ui.screens.counter.components.TicketDetail
import org.mr0btain.ticketman.ui.screens.kiosk.LockScreen
import org.mr0btain.ticketman.ui.screens.records.components.customer.CustomerPanel
import org.mr0btain.ticketman.ui.screens.records.components.ticket.TicketPanel
import org.mr0btain.ticketman.ui.screens.records.components.inventory.InventoryDialog
import org.mr0btain.ticketman.ui.screens.records.components.inventory.InventoryPanel
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.data.inventory.Inventory
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.archive
import ticketman.composeapp.generated.resources.home
import ticketman.composeapp.generated.resources.monitor
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.settings
import java.io.File
import org.mr0btain.ticketman.components.data.user.UserSeed
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.screens.admin.users.AdminUsersTab
import org.mr0btain.ticketman.ui.screens.settings.SettingsLayout
import org.mr0btain.ticketman.ui.screens.settings.components.AccountPanel
import org.mr0btain.ticketman.ui.screens.settings.components.AppearanceSettings
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItem
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItemGroup
import org.mr0btain.ticketman.ui.screens.settings.components.SettingType
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsGroup
import org.mr0btain.ticketman.ui.kit.theme.TmFonts
import org.mr0btain.ticketman.ui.kit.theme.TmPalettes
import org.jetbrains.compose.resources.painterResource
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Draws the real screens with made up shop data to PNGs in build/screen-renders, in dark and light,
 * so layout changes can be looked over without a server
 */
class ScreenRenderTest {
    private val outputDir = File("build/screen-renders").apply { mkdirs() }

    /** About a phone held upright */
    private val NARROW_WIDTH = 420
    private val now = Clock.System.now().toEpochMilliseconds()

    // ------------------------------------------ Sample data ------------------------------------------

    private val dana = Customer(name = "Dana Whitfield", phone = "555 0142", email = "dana@example.com", address = Address("41 Mill Rd", "Ashford", "CT", "06278"))
    private val marcus = Customer(name = "Marcus Oyelaran", phone = "555 0199", email = "marcus@example.com")

    private fun ticket(seq: Long, device: Device, status: TicketStatusType, priority: TicketPriorityType, hoursAgo: Int = 2, problem: String = "") =
        Ticket(
            device = device,
            number = TicketNumber(2026, seq),
            status = TicketStatus(status),
            priority = TicketPriority(priority),
            problem = problem,
            estimateCents = 14999,
            promisedAt = now + 24.hours.inWholeMilliseconds - hoursAgo
        )

    private val iphone = ticket(41, Device("Apple", "iPhone 13", "F2LXK3QHN7"), TicketStatusType.IN_PROGRESS, TicketPriorityType.HIGH,
        problem = "Screen cracked in the top corner after a drop, touch still works. Wants the old screen back.")
    private val pixel = ticket(42, Device("Google", "Pixel 7"), TicketStatusType.ON_HOLD, TicketPriorityType.NORMAL)
    private val thinkpad = ticket(43, Device("Lenovo", "ThinkPad T14"), TicketStatusType.DONE, TicketPriorityType.LOW)
    private val ps5 = ticket(44, Device("Sony", "PS5"), TicketStatusType.IN_PROGRESS, TicketPriorityType.URGENT)

    private fun summary(ticket: Ticket, customer: Customer) = TicketSummary(ticket, EntityReference(customer.identifier, customer.name))

    private val queue = CounterQueueResponse(
        readyForPickup = listOf(summary(thinkpad, marcus)),
        dueToday = listOf(summary(iphone, dana), summary(ps5, marcus)),
        overdue = listOf(summary(pixel, dana)),
        recentlyCheckedIn = listOf(summary(iphone, dana))
    )

    private val invoice = Invoice(
        number = InvoiceNumber(2026, 41),
        ticketIdentifier = iphone.identifier,
        charges = listOf(
            Charge(type = ChargeType.LABOR, description = "Screen replacement", quantity = 1.0, unitPriceCents = 6000),
            Charge(type = ChargeType.PART, description = "iPhone 13 screen, OEM", quantity = 1.0, unitPriceCents = 8999)
        ),
        payments = listOf(Payment(method = PaymentMethod.CARD, amountCents = 4000, receivedAt = now - 3_600_000, note = "Deposit")),
        taxRateBasisPoints = 625,
        subtotalCents = 14999,
        taxCents = 937,
        totalCents = 15936
    )

    private val sampleNotes = listOf(
        Note(author = "Priya", message = "Ordered the screen, arrives Thursday."),
        Note(author = "Dana W.", message = "Customer called to check on it, told them Friday.")
    )

    private val detail = TicketDetail(
        ticket = iphone,
        customer = dana,
        technician = EntityReference(dana.identifier, "Priya Raman"),
        notes = sampleNotes,
        history = listOf(
            TicketStatusChange(TicketStatusType.IN_PROGRESS, now - 7_200_000, "dana"),
            TicketStatusChange(TicketStatusType.ON_HOLD, now - 3_600_000, "priya")
        ),
        invoice = invoice,
        signature = null
    )

    private val navItems = listOf(
        TmNavItem("Records", "Records", Res.drawable.home),
        TmNavItem("Counter", "Counter", Res.drawable.monitor),
        TmNavItem("Settings", "Settings", Res.drawable.settings),
        TmNavItem("Admin", "Admin", Res.drawable.archive)
    )

    // ------------------------------------------ Frames ------------------------------------------

    /** The app's wide layout around [content]: backdrop, glass rail and room for the screen */
    @Composable
    private fun Shell(selected: String, content: @Composable () -> Unit) {
        TmBackdrop(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TmNavRail(items = navItems, selectedKey = selected, onSelect = {})
                Box(Modifier.weight(1f).fillMaxHeight().glassSource()) { content() }
            }
        }
    }

    private fun render(name: String, dark: Boolean, width: Int = 1440, height: Int = 900, content: @Composable () -> Unit): File {
        val bytes = ImageComposeScene(width, height, Density(1f)) {
            TmTheme(darkTheme = dark) { content() }
        }.use { scene ->
            // Fonts load in the background, so draw a few frames before keeping one
            var frame = scene.render(0)
            repeat(8) { index ->
                Thread.sleep(60)
                frame = scene.render((index + 1) * 16_000_000L)
            }
            frame.encodeToData(EncodedImageFormat.PNG)!!.bytes
        }

        return File(outputDir, "$name-${if (dark) "dark" else "light"}.png").apply { writeBytes(bytes) }
    }

    private fun bothModes(name: String, width: Int = 1440, height: Int = 900, content: @Composable () -> Unit) {
        listOf(true, false).forEach { dark ->
            val file = render(name, dark, width = width, height = height, content = content)
            assertTrue(file.length() > 0, "${file.name} should have been drawn")
        }
    }

    // ------------------------------------------ Screens ------------------------------------------

    @Test
    fun counterTicket() = bothModes("counter-ticket") { CounterTicket() }

    /** A short window, where the ticket's panels scroll under the header */
    @Test
    fun counterTicketShort() = bothModes("counter-ticket-short", height = 640) { CounterTicket() }

    /** Phone width: the buttons are icons and the queue is behind its own button */
    @Test
    fun counterTicketNarrow() = bothModes("counter-ticket-narrow", width = NARROW_WIDTH, height = 860) {
        Phone { CounterTicketLayout() }
    }

    @Test
    fun pickupNarrow() = bothModes("pickup-narrow", width = NARROW_WIDTH, height = 860) {
        Phone { Pickup() }
    }

    @Test
    fun checkInNarrow() = bothModes("check-in-narrow", width = NARROW_WIDTH, height = 860) {
        Phone { CheckIn() }
    }

    /** A phone sized frame, no rail */
    @Composable
    private fun Phone(content: @Composable () -> Unit) {
        TmBackdrop(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(8.dp).glassSource()) { content() }
        }
    }

    @Composable
    private fun CounterTicket() {
        Shell("Counter") { CounterTicketLayout() }
    }

    @Composable
    private fun CounterTicketLayout() {
        CounterLayout(
            searchField = { modifier ->
                TmSearchField(
                    modifier = modifier,
                    query = "",
                    onQueryChange = {},
                    placeholder = "Search tickets and customers  (F2)",
                    size = TmControlSize.LARGE
                )
            },
            canEdit = true,
            queue = queue,
            onCheckIn = {},
            onNewCustomer = {},
            onPriceCheck = {},
            onQueueTicketClick = {}
        ) {
            TicketCard(
                detail = detail,
                canEdit = true,
                onStatusChange = {},
                onAddNote = {},
                onCustomerClick = {},
                onBack = {},
                extraActions = { TmButton(text = "Pick up  (F5)", onClick = {}, size = TmControlSize.LARGE) }
            )
        }
    }

    @Test
    fun pickup() = bothModes("pickup") { Shell("Counter") { Pickup() } }

    @Composable
    private fun Pickup() {
        PickupScreen(
            detail = detail,
            onSearchParts = { emptyList() },
            onSave = { _, _, _ -> },
            onRemovePayment = {},
            onFinish = { _, _, _, _ -> },
            onBack = {}
        )
    }

    @Test
    fun checkIn() = bothModes("check-in") { Shell("Counter") { CheckIn() } }

    @Composable
    private fun CheckIn() {
        CheckInWizard(
            startingCustomer = dana,
            terms = "We aren't responsible for data loss.",
            onSearchCustomers = { emptyList() },
            onAddCustomer = { _, _ -> },
            onSearchTechnicians = { emptyList() },
            onRepeatCheck = { _, _, _ -> emptyList() },
            onOpenTicket = {},
            onSubmit = { _, _ -> },
            onCancel = {}
        )
    }

    @Test
    fun kioskLock() = bothModes("kiosk-lock") {
        TmBackdrop(Modifier.fillMaxSize()) {
            LockScreen(
                stationName = "Front counter",
                staff = listOf("Dana Whitfield", "Priya Raman", "Marcus Oyelaran", "June Kowalski").map { StaffMember(kotlin.uuid.Uuid.random(), it.substringBefore(" ").lowercase(), it) },
                onRefresh = {},
                onUnlock = { _, _, _ -> },
                onExit = { _, _, _ -> }
            )
        }
    }

    /** Asking to trust a server's own certificate, the first time and after it changed */
    @Test
    fun serverTrust() {
        val fingerprint = (0 until 32).joinToString(":") { ((it * 37 + 11) % 256).toString(16).padStart(2, '0').uppercase() }

        listOf(null, "AA:BB").forEach { before ->
            bothModes(if (before == null) "server-trust" else "server-trust-changed") {
                Shell("Records") {
                    ServerTrustDialog(
                        certificate = UntrustedCertificate("192.168.1.20:8443", fingerprint, "CN=TicketMan server", before),
                        onTrust = {},
                        onDismiss = {}
                    )
                }
            }
        }
    }

    /** The Server tab with a certificate close to running out, a new one waiting, a daily backup and a restore without photos */
    @Test
    fun adminServer() {
        val fingerprint = (0 until 32).joinToString(":") { ((it * 37 + 11) % 256).toString(16).padStart(2, '0').uppercase() }
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val manifest = BackupManifest(serverVersion = "1.0.0", createdAt = now - 3 * day, includesImages = false, imageCount = 0, imageBytes = 0)

        bothModes("admin-server", height = 1300) {
            Shell("Admin") {
                AdminServerTab(
                    serverInfo = ServerInfoResponse(
                        name = "Front shop", version = "1.0.0", startedAt = now - day, uptimeMillis = day,
                        dataPath = "/srv/ticketman", databaseSizeBytes = 48_000_000, imagesSizeBytes = 2_400_000_000,
                        javaVersion = "21.0.4", operatingSystem = "Linux 6.8", developmentMode = false,
                        certificate = ServerCertificateInfo(
                            fingerprint = fingerprint, subject = "CN=TicketMan server", expiresAt = now + 200 * day,
                            generated = true, httpsPort = 8443, httpMode = "redirect",
                            pendingFingerprint = fingerprint.reversed(), pendingExpiresAt = now + 3650 * day
                        ),
                        scheduledBackup = ScheduledBackupInfo(folder = "/mnt/usb/ticketman", keep = 7, includesImages = true, lastBackupAt = now - day / 2, lastSizeBytes = 2_300_000_000),
                        lastRestore = RestoreRecord(manifest, now - day, "pre-restore-${now - day}")
                    ),
                    serverUrl = "https://192.168.1.20:8443",
                    shopProfile = null,
                    isBusy = false,
                    backupProgress = null,
                    pendingRestore = null,
                    onSaveShopProfile = {},
                    onBackup = {},
                    onRestore = {},
                    onCancelRestore = {},
                    onRenewCertificate = {},
                    onCancelCertificateRenewal = {},
                    onSeed = {}
                )
            }
        }
    }

    @Test
    fun adminOverview() = bothModes("admin-overview") {
        Shell("Admin") {
            AdminOverviewTab(
                stats = AdminStatsResponse(
                    totalTickets = 214, openTickets = 18, ticketsCreatedLastWeek = 23,
                    ticketsByStatus = mapOf(TicketStatusType.IN_PROGRESS to 9, TicketStatusType.ON_HOLD to 4, TicketStatusType.DONE to 5, TicketStatusType.PICKED_UP to 196),
                    ticketsByPriority = mapOf(TicketPriorityType.LOW to 4, TicketPriorityType.NORMAL to 9, TicketPriorityType.HIGH to 3, TicketPriorityType.URGENT to 2),
                    oldestOpenTicket = pixel, recentTickets = listOf(iphone, pixel, thinkpad, ps5),
                    totalCustomers = 482, totalInventory = 96, lowStockCount = 3, lowStockThreshold = 2,
                    totalUsers = 6, usersByAccessLevel = mapOf(AccessLevel.ADMIN to 1, AccessLevel.NORMAL to 4, AccessLevel.RESTRICTED to 1)
                ),
                dailyReport = DailyReport(
                    date = "2026-10-17",
                    takenByMethod = mapOf(PaymentMethod.CARD to 98450, PaymentMethod.CASH to 30000),
                    paymentCount = 11, salesCents = 112000, invoicesPaid = 8,
                    ticketsCheckedIn = 7, ticketsPickedUp = 8, overdueNow = 1, waitingForPickupNow = 5
                ),
                defaultPaper = PaperSize.RECEIPT_80MM,
                onMoveReport = {},
                onPrintReport = { _, _ -> },
                onTicketClick = {},
                onLowStockClick = {}
            )
        }
    }

    @Test
    fun adminUsers() {
        val boss = User(username = "dana", name = "Dana Whitfield", email = "dana@example.com", accessLevel = AccessLevel.ADMIN)
        val tech = User(username = "marcus", name = "Marcus Oyelaran", phone = "5550142", accessLevel = AccessLevel.NORMAL, hasPin = true)
        val viewer = User(username = "june", name = "June Kowalski", accessLevel = AccessLevel.RESTRICTED)
        val seed = UserSeed().apply {
            setUser(tech, listOf(
                Note(author = "Dana Whitfield", message = "Trained on Apple board repair, can take micro soldering jobs."),
                Note(author = "Dana Whitfield", message = "Works Monday to Thursday, off Fridays.")
            ))
        }

        bothModes("admin-users") {
            Shell("Admin") {
                AdminUsersTab(
                    pending = emptyList(),
                    onInvite = {},
                    onApprove = {},
                    onReject = {},
                    onRenewInvite = {},
                    userList = listOf(boss, tech, viewer),
                    seed = seed,
                    currentUserIdentifier = boss.identifier,
                    listState = rememberLazyListState(),
                    onSelect = {},
                    onSearchQuery = { emptyList() },
                    onAdd = {},
                    onEdit = {},
                    onResetPassword = {},
                    onSetActive = {},
                    onLogOutEverywhere = {},
                    onUnlockPin = {},
                    onResetPin = {},
                    onSaveNote = {},
                    onRemoveNote = {},
                    onDelete = {}
                )
            }
        }
    }

    @Test
    fun records() {
        val customerSeed = CustomerSeed().apply { setCustomer(dana, sampleNotes, listOf(iphone, pixel)) }
        val ticketSeed = TicketSeed().apply {
            setTicket(iphone, sampleNotes, EntityReference(dana.identifier, dana.name), EntityReference(dana.identifier, "Priya Raman"))
        }

        bothModes("records-customers") {
            Shell("Records") {
                CustomerPanel(
                    customerList = listOf(dana, marcus, Customer(name = "Priya Raman", phone = "555 0110"), Customer(name = "Tomás Herrera", email = "tomas@example.com")),
                    seed = customerSeed,
                    onAddCustomer = {},
                    onEditCustomer = {},
                    onCustomerSelectionChange = {},
                    onSearchQuery = { emptyList() },
                    onDeleteCustomer = {},
                    onTicketClick = {},
                    onImageClick = { _, _ -> },
                    customerLazyListState = rememberLazyListState()
                )
            }
        }

        // A restricted user: no add, edit or delete
        bothModes("records-view-only") {
            Shell("Records") {
                CustomerPanel(
                    customerList = listOf(dana, marcus),
                    seed = customerSeed,
                    onAddCustomer = {},
                    onEditCustomer = {},
                    canEdit = false,
                    onCustomerSelectionChange = {},
                    onSearchQuery = { emptyList() },
                    onDeleteCustomer = {},
                    onTicketClick = {},
                    onImageClick = { _, _ -> },
                    customerLazyListState = rememberLazyListState()
                )
            }
        }

        // Untracked stock reads as such rather than as Int.MAX_VALUE
        val screen = Inventory(name = "iPhone 13 Screen", description = "OEM panel", stock = 4, costCents = 6_000, priceCents = 12_000)
        val cable = Inventory(name = "USB-C Cable", description = "1 m braided", costCents = 300, priceCents = 1_200)
        val inventorySeed = InventorySeed().apply { setInitial(cable) }
        bothModes("records-inventory") {
            Shell("Records") {
                InventoryPanel(
                    inventoryList = listOf(screen, cable, Inventory(name = "Pixel 7 Battery", stock = 0, priceCents = 5_000)),
                    selectedInventory = inventorySeed,
                    onSelectedInventoryChange = {},
                    onSearchQuery = { emptyList() },
                    onAddInventory = {},
                    onEditInventory = {},
                    onDeleteInventory = {},
                    onImageClick = {},
                    onNoteImageClick = { _, _ -> },
                    inventoryListState = rememberLazyListState()
                )
                InventoryDialog(seed = inventorySeed, isVisible = true, onDismiss = {}, onContinue = {})
            }
        }

        bothModes("records-tickets") {
            Shell("Records") {
                TicketPanel(
                    ticketScope = TicketScope.ALL,
                    onTicketScopeChange = {},
                    seed = ticketSeed,
                    ticketList = listOf(iphone, pixel, thinkpad, ps5),
                    onAddNewTicket = {},
                    onEditTicket = {},
                    onSelectedTicketChange = {},
                    onSearchQuery = { emptyList() },
                    onDeleteTicket = {},
                    onImageClick = { _, _ -> },
                    ticketListState = rememberLazyListState()
                )
            }
        }
    }

    /** Two columns on a wide window, with the start tab as a dropdown under Records */
    @Test
    fun settings() = bothModes("settings") {
        Shell("Settings") {
            SettingsLayout(
                first = {
                    AccountPanel(user = User(username = "dana", name = "Dana Whitfield", accessLevel = AccessLevel.ADMIN), onLogin = {}, onLogout = {})
                    AppearanceSettings(
                        palette = TmPalettes.default,
                        onPaletteChange = {},
                        darkTheme = true,
                        onDarkThemeChange = {},
                        font = TmFonts.default,
                        onFontChange = {},
                        reduceTransparency = false,
                        onReduceTransparencyChange = {},
                        onOpenGallery = {}
                    )
                },
                second = {
                    val tabs = listOf("Customers", "Tickets", "Inventory")
                    val icon = painterResource(Res.drawable.settings)

                    SettingsGroup(
                        settingItemGroup = SettingItemGroup(
                            label = "Login",
                            items = listOf(SettingItem(icon, "Save username and password after logging in", "Remember login", true, true, type = SettingType.SWITCH))
                        )
                    )
                    SettingsGroup(
                        settingItemGroup = SettingItemGroup(
                            label = "Records",
                            items = listOf(
                                SettingItem(icon, "Records tab shown on start", "Start tab", true, "Tickets", type = SettingType.CHOICE, options = tabs),
                                SettingItem(icon, "Number of items loaded per page (10-200)", "Page size", true, "50", type = SettingType.TEXT)
                            )
                        )
                    )
                }
            )
        }
    }

    @Test
    fun settingsAccount() = bothModes("settings-account", height = 360) {
        Shell("Settings") {
            Column(Modifier.width(720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountPanel(user = User(username = "dana", name = "Dana Whitfield", accessLevel = AccessLevel.ADMIN), onLogin = {}, onLogout = {})
                AccountPanel(user = null, onLogin = {}, onLogout = {})
            }
        }
    }
}
