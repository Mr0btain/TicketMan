package org.mr0btain.ticketman.ui

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
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import org.mr0btain.ticketman.server.response.DailyReport
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.foundation.TmBackdrop
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.glassSource
import org.mr0btain.ticketman.ui.kit.navigation.TmNavItem
import org.mr0btain.ticketman.ui.kit.navigation.TmNavRail
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.admin.overview.AdminOverviewTab
import org.mr0btain.ticketman.ui.screens.counter.CheckInWizard
import org.mr0btain.ticketman.ui.screens.counter.PickupScreen
import org.mr0btain.ticketman.ui.screens.counter.QueuePanel
import org.mr0btain.ticketman.ui.screens.counter.TicketCard
import org.mr0btain.ticketman.ui.screens.counter.components.TicketDetail
import org.mr0btain.ticketman.ui.screens.kiosk.LockScreen
import org.mr0btain.ticketman.ui.screens.dashboard.components.customer.CustomerPanel
import org.mr0btain.ticketman.ui.screens.dashboard.components.ticket.TicketPanel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.archive
import ticketman.composeapp.generated.resources.file_text
import ticketman.composeapp.generated.resources.home
import ticketman.composeapp.generated.resources.monitor
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.settings
import java.io.File
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
    private val now = Clock.System.now().toEpochMilliseconds()

    // ------------------------------------------ Sample data ------------------------------------------

    private val dana = Customer(name = "dana:whitfield", phone = "555 0142", email = "dana@example.com", address = Address("41 Mill Rd", "Ashford", "CT", "06278"))
    private val marcus = Customer(name = "marcus:oyelaran", phone = "555 0199", email = "marcus@example.com")

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
        technician = EntityReference(dana.identifier, "priya:raman"),
        notes = sampleNotes,
        history = listOf(
            TicketStatusChange(TicketStatusType.IN_PROGRESS, now - 7_200_000, "dana"),
            TicketStatusChange(TicketStatusType.ON_HOLD, now - 3_600_000, "priya")
        ),
        invoice = invoice,
        signature = null
    )

    private val navItems = listOf(
        TmNavItem("Admin", "Admin", Res.drawable.archive),
        TmNavItem("Dash", "Counter", Res.drawable.monitor),
        TmNavItem("Invoice", "Invoices", Res.drawable.file_text),
        TmNavItem("Main", "Main", Res.drawable.home),
        TmNavItem("Settings", "Settings", Res.drawable.settings)
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

    private fun bothModes(name: String, content: @Composable () -> Unit) {
        listOf(true, false).forEach { dark ->
            val file = render(name, dark, content = content)
            assertTrue(file.length() > 0, "${file.name} should have been drawn")
        }
    }

    // ------------------------------------------ Screens ------------------------------------------

    @Test
    fun counterTicket() = bothModes("counter-ticket") {
        Shell("Dash") {
            Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                    TmSearchField(
                        modifier = Modifier.weight(1f),
                        query = "",
                        onQueryChange = {},
                        placeholder = "Search tickets and customers  (F2)",
                        size = TmControlSize.LARGE
                    )
                    TmButton(text = "Check in  (F3)", onClick = {}, size = TmControlSize.LARGE, leadingIcon = Res.drawable.plus)
                    TmButton(text = "Price check  (F4)", onClick = {}, size = TmControlSize.LARGE, variant = TmButtonVariant.SECONDARY)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                    Box(Modifier.weight(1f)) {
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

                    QueuePanel(modifier = Modifier.width(360.dp).fillMaxHeight(), queue = queue, onTicketClick = {})
                }
            }
        }
    }

    @Test
    fun pickup() = bothModes("pickup") {
        Shell("Dash") {
            PickupScreen(
                detail = detail,
                onSearchParts = { emptyList() },
                onSave = { _, _, _ -> },
                onRemovePayment = {},
                onFinish = { _, _, _, _ -> },
                onBack = {}
            )
        }
    }

    @Test
    fun checkIn() = bothModes("check-in") {
        Shell("Dash") {
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
    }

    @Test
    fun kioskLock() = bothModes("kiosk-lock") {
        TmBackdrop(Modifier.fillMaxSize()) {
            LockScreen(
                stationName = "Front counter",
                staff = listOf("dana:whitfield", "priya:raman", "marcus:oyelaran", "june:kowalski").map { StaffMember(kotlin.uuid.Uuid.random(), it.substringBefore(":"), it) },
                onRefresh = {},
                onUnlock = { _, _, _ -> },
                onExit = { _, _, _ -> }
            )
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
    fun records() {
        val customerSeed = CustomerSeed().apply { setCustomer(dana, sampleNotes, listOf(iphone, pixel)) }
        val ticketSeed = TicketSeed().apply {
            setTicket(iphone, sampleNotes, EntityReference(dana.identifier, dana.name), EntityReference(dana.identifier, "priya:raman"))
        }

        bothModes("records-customers") {
            Shell("Main") {
                CustomerPanel(
                    customerList = listOf(dana, marcus, Customer(name = "priya:raman", phone = "555 0110"), Customer(name = "tomás:herrera", email = "tomas@example.com")),
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

        bothModes("records-tickets") {
            Shell("Main") {
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
}
