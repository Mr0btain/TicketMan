package org.mr0btain.ticketman.ui

import org.mr0btain.ticketman.data.estimate.Estimate
import org.mr0btain.ticketman.data.notify.*
import org.mr0btain.ticketman.ui.screens.admin.messages.AdminMessagesTab
import org.mr0btain.ticketman.ui.screens.counter.EstimateActions
import org.mr0btain.ticketman.ui.screens.counter.EstimateDialog
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
import org.mr0btain.ticketman.data.invoice.InvoiceStatus
import org.mr0btain.ticketman.data.invoice.Payment
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.station.StaffMember
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketStatusChange
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.ticket.CustomStatus
import org.mr0btain.ticketman.data.ticket.StatusTone
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import org.mr0btain.ticketman.server.response.BackupManifest
import org.mr0btain.ticketman.server.response.PendingRestore
import org.mr0btain.ticketman.server.response.RestoreRecord
import org.mr0btain.ticketman.server.response.ScheduledBackupInfo
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import org.mr0btain.ticketman.server.response.LoginLockKind
import org.mr0btain.ticketman.server.response.LoginLock
import org.mr0btain.ticketman.server.response.LoginLimitsResponse
import org.mr0btain.ticketman.server.response.SessionKind
import org.mr0btain.ticketman.server.response.SessionResponse
import org.mr0btain.ticketman.ui.screens.admin.sessions.AdminSessionsTab
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
import org.mr0btain.ticketman.ui.screens.admin.invoices.RefundDialog
import org.mr0btain.ticketman.ui.screens.counter.SaleScreen
import org.mr0btain.ticketman.ui.screens.counter.TicketCard
import org.mr0btain.ticketman.ui.screens.counter.components.TicketDetail
import org.mr0btain.ticketman.ui.screens.kiosk.LockScreen
import org.mr0btain.ticketman.ui.screens.records.components.customer.CustomerPanel
import org.mr0btain.ticketman.ui.screens.records.components.customer.CustomerDialog
import org.mr0btain.ticketman.ui.screens.admin.invoices.InvoiceExportDialog
import org.mr0btain.ticketman.ui.screens.admin.server.ImportDialog
import org.mr0btain.ticketman.ui.screens.admin.server.ImportCard
import org.mr0btain.ticketman.ui.screens.admin.components.ImportPreview
import org.mr0btain.ticketman.ui.screens.admin.components.ImportPreviewRow
import org.mr0btain.ticketman.ui.screens.admin.components.PreviewDetails
import org.mr0btain.ticketman.components.data.importing.ImportKind
import org.mr0btain.ticketman.components.data.importing.ImportSheet
import org.mr0btain.ticketman.server.response.ImportOutcome
import org.mr0btain.ticketman.ui.screens.records.components.ticket.TicketDialog
import org.mr0btain.ticketman.ui.screens.records.components.ticket.TicketInfoPane
import org.mr0btain.ticketman.ui.screens.records.components.ticket.TicketWorkSection
import org.mr0btain.ticketman.ui.screens.records.components.ticket.PartLine
import org.mr0btain.ticketman.ui.screens.records.components.ticket.WorkDialog
import org.mr0btain.ticketman.data.ticket.WorkEntry
import org.mr0btain.ticketman.data.ticket.WorkType
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.error.TmResult
import androidx.compose.runtime.LaunchedEffect
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
import org.mr0btain.ticketman.components.network.WrongServerName
import org.mr0btain.ticketman.ui.screens.settings.components.AppearanceSettings
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItem
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItemGroup
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsGroup
import org.mr0btain.ticketman.ui.kit.theme.TmFonts
import org.mr0btain.ticketman.ui.kit.theme.TmPalettes
import org.jetbrains.compose.resources.painterResource
import kotlin.uuid.Uuid
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
        Note(author = "Priya", message = "Ordered the screen, arrives Thursday.", customerVisible = true),
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
        signature = null,
        messages = listOf(
            OutboxMessage(
                channel = NotificationChannel.SMS, event = NotificationEvent.STATUS_CHANGED, recipient = "+15550142",
                body = "Hi Dana, your Apple iPhone 13 (ticket 26-00000041) is now: Awaiting parts. Follow it at https://repairs.example.com/status/0123",
                status = OutboxStatus.SENT, createdAt = now - 3_500_000
            ),
            OutboxMessage(
                channel = NotificationChannel.EMAIL, event = NotificationEvent.ESTIMATE_READY, recipient = "dana@example.com",
                body = "Hi Dana, the estimate for your Apple iPhone 13 is $159.36.", status = OutboxStatus.PENDING,
                lastError = "Connection refused", createdAt = now - 600_000
            )
        ),
        estimates = listOf(
            Estimate(
                ticketIdentifier = iphone.identifier,
                lines = listOf(
                    Charge(type = ChargeType.LABOR, description = "Screen replacement", quantity = 1.0, unitPriceCents = 6000, taxable = false),
                    Charge(type = ChargeType.PART, description = "iPhone 13 screen, OEM", quantity = 1.0, unitPriceCents = 8999)
                ),
                taxRateBasisPoints = 625,
                note = "The frame is bent too, the screen won't sit right without straightening it.",
                createdAt = now - 600_000,
                createdBy = "priya"
            )
        )
    )

    private val estimateActions = EstimateActions(
        laborCents = 6000, taxRateBasisPoints = 625, onSearchParts = { emptyList() },
        onSend = { _, _, _, _ -> }, onAnswer = { _, _, _, _ -> }, onWithdraw = {}
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
            onSell = {},
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
                statusLink = "https://repairs.example.com/status/0123456789abcdef0123456789abcdef",
                estimates = estimateActions,
                onSendStatusLink = {},
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
    fun sale() = bothModes("counter-sale") { Shell("Counter") { Sale() } }

    @Test
    fun saleNarrow() = bothModes("counter-sale-narrow", width = NARROW_WIDTH, height = 860) { Phone { Sale() } }

    @Composable
    private fun Sale() {
        SaleScreen(
            taxRateBasisPoints = 825,
            laborCents = 6_000,
            startingCustomer = dana,
            onSearchParts = { emptyList() },
            onSearchCustomers = { emptyList() },
            onFinish = { _, _ -> },
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

    /** Asking to trust a proxy's private CA, the first time and after it changed */
    @Test
    fun serverTrust() {
        val fingerprint = (0 until 32).joinToString(":") { ((it * 37 + 11) % 256).toString(16).padStart(2, '0').uppercase() }

        listOf(null, "AA:BB").forEach { before ->
            bothModes(if (before == null) "server-trust" else "server-trust-changed") {
                Shell("Records") {
                    ServerTrustDialog(
                        certificate = UntrustedCertificate(
                            address = "tickets.local:443",
                            fingerprint = fingerprint,
                            subject = "CN=Caddy Local Authority - 2026 ECC Root",
                            isAuthority = true,
                            trustedBefore = before
                        ),
                        onTrust = {},
                        onDismiss = {}
                    )
                }
            }
        }
    }

    /** The Messages tab with both channels set up and a few messages, one that failed */
    @Test
    fun adminMessages() {
        val now = System.currentTimeMillis()
        val sampleStatuses = listOf(
            CustomStatus(name = "Diagnosing", actsAs = TicketStatusType.IN_PROGRESS, tone = StatusTone.ACCENT),
            CustomStatus(name = "Awaiting parts", actsAs = TicketStatusType.ON_HOLD)
        )
        val settings = NotificationSettings(
            emailEnabled = true,
            smtp = SmtpSettings(host = "smtp.example.com", username = "repairs@example.com", fromAddress = "repairs@example.com", fromName = "Fix It Shop"),
            smtpPasswordSet = true,
            smsEnabled = true,
            sms = SmsSettings(accountSid = "AC0123456789", fromNumber = "+15550100000"),
            smsTokenSet = true,
            events = setOf(NotificationEvent.READY_FOR_PICKUP, NotificationEvent.ESTIMATE_READY, NotificationEvent.STATUS_CHANGED, NotificationEvent.PICKUP_REMINDER),
            statusChangeStatuses = listOf(sampleStatuses[0].choice)
        )
        val outbox = listOf(
            OutboxMessage(ticketLabel = "26-00000041", channel = NotificationChannel.SMS, event = NotificationEvent.READY_FOR_PICKUP, recipient = "+15550142",
                body = "Hi Dana, your Apple iPhone 13 is ready to pick up.", status = OutboxStatus.SENT, createdAt = now - 60_000),
            OutboxMessage(ticketLabel = "26-00000040", channel = NotificationChannel.EMAIL, event = NotificationEvent.PICKUP_REMINDER, recipient = "marcus@example.com",
                body = "Hi Marcus, a reminder that your Lenovo ThinkPad T14 is ready.", status = OutboxStatus.FAILED, attempts = 1,
                lastError = "The mail server refused the address", createdAt = now - 3_600_000),
            OutboxMessage(ticketLabel = "26-00000039", channel = NotificationChannel.EMAIL, event = NotificationEvent.STATUS_LINK, recipient = "ana@example.com",
                body = "Hi Ana, follow your Sony PS5 at https://repairs.example.com/status/abc", status = OutboxStatus.PENDING,
                lastError = "Connection timed out", createdAt = now - 7_200_000, createdBy = "priya")
        )

        bothModes("admin-messages", height = 2000) {
            Shell("Admin") {
                AdminMessagesTab(
                    settings = settings, statuses = sampleStatuses, outbox = outbox, outboxEnd = true, isBusy = false,
                    onSave = {}, onTest = { _, _, _ -> }, onRetry = {}, onCancel = {}, onLoadMore = {}
                )
            }
        }
    }

    @Test
    fun estimateDialog() = bothModes("counter-estimate-dialog") {
        Shell("Counter") {
            EstimateDialog(
                start = detail.estimates!!.first().lines, laborCents = 6000, taxRateBasisPoints = 625, canNotify = true,
                onSearchParts = { emptyList() }, onSend = { _, _, _ -> }, onDismiss = {}
            )
        }
    }

    /** A paid invoice with its screen part, refunding one */
    @Test
    fun refundDialog() = bothModes("admin-refund-dialog") {
        Shell("Admin") {
            val part = Inventory(name = "iPhone 13 screen, OEM")
            RefundDialog(
                invoice = invoice.copy(
                    status = InvoiceStatus.PAID,
                    charges = invoice.charges.map { if (it.type == ChargeType.PART) it.copy(inventoryIdentifier = part.identifier) else it },
                    payments = invoice.payments + Payment(method = PaymentMethod.CASH, amountCents = 11936)
                ),
                onRefund = { _, _ -> },
                onDismissRequest = {}
            )
        }
    }

    /** The Server tab with a daily backup and a restore without photos */
    @Test
    fun adminServer() {
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val manifest = BackupManifest(serverVersion = "1.0.0", createdAt = now - 3 * day, includesImages = false, imageCount = 0, imageBytes = 0)

        bothModes("admin-server", height = 2200) {
            Shell("Admin") {
                AdminServerTab(
                    serverInfo = ServerInfoResponse(
                        name = "Front shop", version = "1.0.0", startedAt = now - day, uptimeMillis = day,
                        dataPath = "/srv/ticketman", databaseSizeBytes = 48_000_000, imagesSizeBytes = 2_400_000_000,
                        javaVersion = "21.0.4", operatingSystem = "Linux 6.8", developmentMode = false,
                        scheduledBackup = ScheduledBackupInfo(folder = "/mnt/usb/ticketman", keep = 7, includesImages = true, lastBackupAt = now - day / 2, lastSizeBytes = 2_300_000_000),
                        lastRestore = RestoreRecord(manifest, now - day, "pre-restore-${now - day}")
                    ),
                    serverUrl = "https://192.168.1.20:8443",
                    shopProfile = ShopProfile(
                        name = "Fix It Shop", phone = "555-0100", publicUrl = "https://repairs.example.com", taxRate = "8.25",
                        statuses = listOf(
                            CustomStatus(name = "Diagnosing", actsAs = TicketStatusType.IN_PROGRESS, tone = StatusTone.ACCENT),
                            CustomStatus(name = "Awaiting parts", actsAs = TicketStatusType.ON_HOLD),
                            CustomStatus(name = "Waiting on insurer", actsAs = TicketStatusType.ON_HOLD, retired = true)
                        )
                    ),
                    isBusy = false,
                    backupProgress = null,
                    pendingRestore = null,
                    onSaveShopProfile = {},
                    onBackup = {},
                    onRestore = {},
                    onCancelRestore = {},
                    onRestart = {},
                    onSeed = {},
                    importCard = { ImportCard(modifier = Modifier.width(440.dp), isBusy = false, onPicked = { _, _ -> }) }
                )
            }
        }
    }

    /**
     * The Server tab behind a reverse proxy, one that forwards and one the server doesn't trust. The forwarding one is
     * in the Docker image, so it can restart itself to finish the restore waiting
     */
    @Test
    fun adminServerBehindProxy() {
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val proxied = ServerInfoResponse(
            name = "Front shop", version = "1.0.0", startedAt = now - day, uptimeMillis = day,
            dataPath = "/data", databaseSizeBytes = 48_000_000, imagesSizeBytes = 2_400_000_000,
            javaVersion = "21.0.4", operatingSystem = "Linux 6.8", developmentMode = false,
            trustedProxies = listOf("127.0.0.1", "::1", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "fc00::/7"),
            clientAddress = "192.168.1.42", forwarded = true, timeZone = "America/New_York", canRestart = true
        )
        val waiting = PendingRestore(
            manifest = BackupManifest(serverVersion = "1.0.0", createdAt = now - 2 * day, includesImages = true, imageCount = 1800, imageBytes = 2_300_000_000),
            sizeBytes = 2_350_000_000,
            stagedAt = now
        )

        listOf("admin-server-proxy" to proxied, "admin-server-proxy-untrusted" to proxied.copy(clientAddress = "172.18.0.3", forwarded = false, trustedProxies = listOf("127.0.0.1"), canRestart = false)).forEach { (name, info) ->
            bothModes(name, height = 1100) {
                Shell("Admin") {
                    AdminServerTab(
                        serverInfo = info,
                        serverUrl = "https://tickets.myshop.com",
                        shopProfile = null,
                        isBusy = false,
                        backupProgress = null,
                        pendingRestore = waiting.takeIf { info.canRestart },
                        onSaveShopProfile = {},
                        onBackup = {},
                        onRestore = {},
                        onCancelRestore = {},
                        onRestart = {},
                        onSeed = {}
                    )
                }
            }
        }
    }

    /** Logged in devices, with a username and a device locked out during a surge of wrong passwords */
    @Test
    fun adminSessions() {
        val now = System.currentTimeMillis()
        val dana = Uuid.random()

        bothModes("admin-sessions", height = 1100) {
            Shell("Admin") {
                AdminSessionsTab(
                    sessions = listOf(
                        SessionResponse("s1", dana, "dana", "Dana Whitfield", AccessLevel.ADMIN, "Front desk PC", SessionKind.PASSWORD, now - 3_600_000, now - 60_000, now + 86_400_000),
                        SessionResponse("s2", dana, "dana", "Dana Whitfield", AccessLevel.ADMIN, "Counter", SessionKind.PIN, now - 600_000, now, now + 43_200_000)
                    ),
                    currentUserIdentifier = dana,
                    currentSessionId = "s1",
                    onRevoke = {},
                    loginLimits = LoginLimitsResponse(
                        locks = listOf(
                            LoginLock(LoginLockKind.USERNAME, "priya", 8, now + 120_000),
                            LoginLock(LoginLockKind.ADDRESS, "203.0.113.24", 20, now + 600_000)
                        ),
                        recentFailures = 134,
                        surge = true
                    )
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

    /** A ticket's work log in its details, and logging a part */
    @Test
    fun ticketWork() {
        val hour = 60L * 60 * 1000
        val now = System.currentTimeMillis()
        val entries = listOf(
            WorkEntry(type = WorkType.LABOR, description = "Diagnosed, swollen battery", quantity = 0.5, unitPriceCents = 8_000, userName = "priya", workedAt = now - 3 * hour),
            WorkEntry(type = WorkType.PART, description = "iPhone 13 Battery", quantity = 1.0, unitPriceCents = 5_500, userName = "priya", workedAt = now - 2 * hour),
            WorkEntry(type = WorkType.LABOR, description = "Replaced the battery, tested charging", quantity = 1.25, unitPriceCents = 8_000, userName = "priya", workedAt = now - hour)
        )
        val ticketSeed = TicketSeed().apply {
            setTicket(iphone, sampleNotes, EntityReference(dana.identifier, dana.name), EntityReference(dana.identifier, "Priya Raman"))
        }

        bothModes("records-ticket-work", height = 1100) {
            Shell("Records") {
                TicketInfoPane(
                    seed = ticketSeed,
                    onEditClick = {},
                    onImageClick = { _, _ -> },
                    work = { TicketWorkSection(entries = entries, loadError = null, canChange = true, onLog = {}, onRemove = {}) }
                )
            }
        }

        bothModes("records-ticket-work-dialog") {
            Shell("Records") {
                WorkDialog(laborRateCents = 8_000, onPartSearch = { emptyList() }, onFindPart = { null }, onSave = { _, _ -> }, onDismissRequest = {})
            }
        }

        bothModes("records-ticket-work-dialog-parts") {
            Shell("Records") {
                WorkDialog(
                    laborRateCents = 8_000,
                    onPartSearch = { emptyList() },
                    onFindPart = { null },
                    onSave = { _, _ -> },
                    onDismissRequest = {},
                    initialParts = listOf(
                        PartLine(Inventory(name = "iPhone 13 Screen", stock = 4, priceCents = 12_000)),
                        PartLine(Inventory(name = "Adhesive strip", stock = 1, priceCents = 300), count = "2"),
                        PartLine(Inventory(name = "Screw kit", priceCents = 150))
                    )
                )
            }
        }
    }

    /** Importing customers: the columns matched from the headers and what each row would do */
    @Test
    fun importCustomers() {
        val sheet = ImportSheet.read(
            ImportKind.CUSTOMERS,
            "First Name,Last Name,Mobile,Email,Address,City\nAmos,Burton,555-0101,amos@example.com,,Tycho\n" +
                "Dana,Whitfield,555-0142,,41 Mill Rd,Ashford\n,,555-0111,,,\nNaomi,Nagata,,naomi@example.com,,Ceres\nAlex,Kamal,555-0101,,,Mars"
        )!!
        val preview = ImportPreview(
            listOf(
                ImportPreviewRow(2, PreviewDetails("Amos Burton", "555-0101, amos@example.com"), ImportOutcome.ADD, ""),
                ImportPreviewRow(3, PreviewDetails("Dana Whitfield", "555-0142"), ImportOutcome.MATCH, "Dana Whitfield has that phone number"),
                ImportPreviewRow(4, PreviewDetails(""), ImportOutcome.ERROR, "No name"),
                ImportPreviewRow(5, PreviewDetails("Naomi Nagata", "naomi@example.com"), ImportOutcome.ADD, ""),
                ImportPreviewRow(6, PreviewDetails("Alex Kamal", "555-0101"), ImportOutcome.MATCH, "Same phone number as row 1")
            )
        )

        bothModes("admin-import", height = 1000) {
            Shell("Admin") {
                ImportDialog(
                    sheet = sheet, mapping = sheet.mapping, preview = preview, problem = null, importing = false,
                    onMap = { _, _ -> }, onImport = {}, onDismiss = {}
                )
            }
        }
    }

    /** Choosing the days for the invoice CSV */
    @Test
    fun invoiceExport() = bothModes("admin-invoice-export") {
        Shell("Admin") {
            InvoiceExportDialog(onExport = { _, _, _ -> }, onDismissRequest = {})
        }
    }

    /** Editing a customer with one ticket linked already and one picked in the dialog, waiting for Save */
    @Test
    fun customerDialog() {
        // Remembered from what's linked when the dialog opens, so the pixel is added after it's drawn once
        val seed = CustomerSeed().apply { setCustomer(dana, sampleNotes, listOf(iphone)) }
        bothModes("records-customer-dialog", height = 1100) {
            Shell("Records") {
                CustomerDialog(
                    seed = seed,
                    isVisible = true,
                    onDismiss = {},
                    onContinue = {},
                    onImageClick = { _, _ -> },
                    onTicketSearchQuery = { emptyList() },
                    onFindTicket = { TmResult.Error(ServerError(NetworkError.NOT_FOUND)) }
                )
                LaunchedEffect(Unit) { seed.addTicket(pixel) }
            }
        }
    }

    /** Editing a ticket, with New customer next to the customer search */
    @Test
    fun ticketDialog() {
        val seed = TicketSeed().apply {
            setTicket(iphone, sampleNotes, EntityReference(dana.identifier, dana.name), EntityReference(dana.identifier, "Priya Raman"))
        }
        bothModes("records-ticket-dialog", height = 1000) {
            Shell("Records") {
                TicketDialog(
                    seed = seed,
                    isVisible = true,
                    onCustomerSearchQuery = { emptyList() },
                    onUserSearchQuery = { emptyList() },
                    onAddCustomer = { _, _ -> },
                    onContinue = {},
                    onDismiss = {}
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
                            items = listOf(SettingItem.Switch(icon, "Remember login", "Save username and password after logging in", checked = true, onToggle = {}))
                        )
                    )
                    SettingsGroup(
                        settingItemGroup = SettingItemGroup(
                            label = "Records",
                            items = listOf(
                                SettingItem.Choice(icon, "Start tab", "Records tab shown on start", selected = "Tickets", options = tabs, onSelect = {}),
                                SettingItem.Text(icon, "Page size", "Number of items loaded per page (5-100)", value = "50", onChange = { true })
                            )
                        )
                    )
                }
            )
        }
    }

    @Test
    fun settingsAccount() = bothModes("settings-account", height = 480) {
        Shell("Settings") {
            Column(Modifier.width(720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountPanel(user = User(username = "dana", name = "Dana Whitfield", accessLevel = AccessLevel.ADMIN), onLogin = {}, onLogout = {})
                AccountPanel(
                    user = null, onLogin = {}, onLogout = {},
                    connectionProblem = WrongServerName("10.0.0.5:443", listOf("tickets.myshop.com")).message
                )
            }
        }
    }
}
