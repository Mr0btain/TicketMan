package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.data.payment.CardExtras
import org.mr0btain.ticketman.data.payment.CardReader
import org.mr0btain.ticketman.data.payment.CardCharge
import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.components.utils.formatDate
import kotlin.time.Clock
import org.mr0btain.ticketman.data.ticket.CheckResult
import org.mr0btain.ticketman.data.ticket.ChecklistItem
import org.mr0btain.ticketman.data.ticket.IntakeChecklist
import org.mr0btain.ticketman.data.ticket.IntakeChecklists
import org.mr0btain.ticketman.ui.kit.inputs.TmPasswordField
import org.mr0btain.ticketman.data.ticket.CustomerDevice
import org.mr0btain.ticketman.data.ticket.DeviceKind
import org.mr0btain.ticketman.server.request.CheckInItem
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import ticketman.composeapp.generated.resources.plus
import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.components.utils.formatDay
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.components.utils.formatTimeAgo
import org.mr0btain.ticketman.components.utils.promiseTimeInDays
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.feedback.TmDot
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.feedback.tone
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmSignaturePad
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.navigation.TmStepper
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.selection.TmFilterChip
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.inputs.TmCustomerForm
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.alert_triangle
import ticketman.composeapp.generated.resources.check_square
import ticketman.composeapp.generated.resources.credit_card
import ticketman.composeapp.generated.resources.edit_3
import ticketman.composeapp.generated.resources.smartphone
import ticketman.composeapp.generated.resources.tool
import ticketman.composeapp.generated.resources.user
import ticketman.composeapp.generated.resources.user_plus
import org.mr0btain.ticketman.data.appointment.Appointment
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Widest the wizard's panels get, so lines stay readable on big screens */
private val FORM_WIDTH = 680.dp

/** Most devices at one check in, like the server's */
private const val MAX_DEVICES = 10

internal enum class CheckInStep(val label: String) {
    CUSTOMER("Customer"),
    DEVICE("Device"),
    REPAIR("Repair"),
    SIGN("Sign"),
    REVIEW("Review")
}

/** Everything typed into the wizard, kept here so going back a step doesn't lose it */
@OptIn(ExperimentalUuidApi::class)
internal class CheckInForm(customer: Customer?, private val checklists: List<IntakeChecklist>, val appointment: Appointment? = null) {
    // Made up front so the repeat check can leave this ticket out. A new one for each device added
    var ticketIdentifier: Uuid by mutableStateOf(Uuid.generateV7())

    var customer by mutableStateOf(customer)
    var manufacturer by mutableStateOf(appointment?.device?.manufacturer.orEmpty())
    var model by mutableStateOf(appointment?.device?.model.orEmpty())
    var serial by mutableStateOf(appointment?.device?.serial.orEmpty())
    var kind by mutableStateOf(appointment?.device?.kind?.takeIf { appointment.device.manufacturer.isNotBlank() || appointment.device.model.isNotBlank() } ?: DeviceKind.PHONE)

    /** One of the customer's devices, when it was picked rather than typed */
    var deviceIdentifier by mutableStateOf<Uuid?>(null)

    /** Devices already filled in for this visit, checked in along with the one being typed */
    val extra = mutableStateListOf<CheckInItem>()

    /** How each thing on the intake checklist was, by name. Anything not set wasn't tested */
    val checks = mutableStateMapOf<String, CheckResult>()

    /** The device's passcode or pattern, blank when they didn't give one */
    var passcode by mutableStateOf("")

    /** The earlier repair this is a warranty return of, picked from the repeat warning */
    var warrantyOf by mutableStateOf<TicketSummary?>(null)

    /** What to check on this kind of device, the shop's list or the default */
    val checklistItems: List<String>
        get() = IntakeChecklists.itemsFor(kind, checklists)
    var condition by mutableStateOf("")
    var accessories by mutableStateOf("")
    var problem by mutableStateOf(appointment?.problem.orEmpty())
    var priority by mutableStateOf(TicketPriority(TicketPriorityType.NORMAL))
    var estimateCents by mutableLongStateOf(0L)
    var promisedInDays by mutableStateOf<Int?>(3)
    var depositInput by mutableLongStateOf(0L)
    var depositMethod by mutableStateOf(PaymentMethod.CARD)

    /** The deposit taken on a card reader, paid before the check in is sent, which then claims it */
    var depositCharge by mutableStateOf<CardCharge?>(null)
    var technician by mutableStateOf(appointment?.technicianIdentifier?.let { SearchResult(it, appointment.technicianName) })
    var strokes by mutableStateOf<List<List<SignaturePoint>>>(emptyList())
    var aspectRatio by mutableStateOf(3f)
    var signedName by mutableStateOf(customer?.name.orEmpty())

    val device: Device
        get() = Device(manufacturer.trim(), model.trim(), serial.trim(), kind)

    /** Fills the device in from one of the customer's, or clears it for a new one */
    fun pick(device: CustomerDevice?) {
        deviceIdentifier = device?.identifier
        manufacturer = device?.device?.manufacturer.orEmpty()
        model = device?.device?.model.orEmpty()
        serial = device?.device?.serial.orEmpty()
        kind = device?.device?.kind ?: DeviceKind.PHONE
    }

    /** Keeps the device being typed for this visit and starts on another, keeping the customer, tech and deposit */
    fun addAnother() {
        extra += CheckInItem(ticket(), deviceIdentifier, passcode.trim())
        ticketIdentifier = Uuid.generateV7()
        pick(null)
        checks.clear()
        passcode = ""
        warrantyOf = null
        condition = ""
        accessories = ""
        problem = ""
        estimateCents = 0
    }

    private fun ticket(): Ticket =
        Ticket(
            identifier = ticketIdentifier,
            device = device,
            priority = priority,
            problem = problem.trim(),
            condition = condition.trim(),
            accessories = accessories.trim(),
            estimateCents = estimateCents.takeIf { it > 0 },
            promisedAt = promisedInDays?.let { promiseTimeInDays(it) },
            checklist = checklistItems.map { ChecklistItem(it, checks[it] ?: CheckResult.NOT_TESTED) },
            warrantyOf = warrantyOf?.ticket?.identifier
        )

    val depositCents: Long?
        get() = depositCharge?.appliedCents ?: depositInput.takeIf { it > 0 }

    fun problem(step: CheckInStep, needsSignature: Boolean): String? = when (step) {
        CheckInStep.CUSTOMER -> if (customer == null) "Pick or add a customer" else null
        CheckInStep.DEVICE -> if (manufacturer.isBlank() && model.isBlank()) "Enter the make or model" else null
        CheckInStep.REPAIR -> when {
            problem.isBlank() -> "Describe what's wrong"
            else -> null
        }
        CheckInStep.SIGN -> when {
            !needsSignature -> null
            strokes.isEmpty() -> "Have the customer sign"
            signedName.isBlank() -> "Type the name of whoever signed"
            else -> null
        }
        CheckInStep.REVIEW -> null
    }

    fun toRequest(needsSignature: Boolean): CheckInRequest =
        CheckInRequest(
            ticket = ticket(),
            customerIdentifier = customer!!.identifier,
            technicianIdentifier = technician?.identifier,
            deposit = depositCharge?.let { PaymentRequest(PaymentMethod.CARD, it.appliedCents, "Deposit", chargeIdentifier = it.identifier) }
                ?: depositCents?.let { PaymentRequest(depositMethod, it, "Deposit") },
            signature = if (needsSignature) SignatureInput(strokes, aspectRatio, signedName.trim()) else null,
            deviceIdentifier = deviceIdentifier,
            more = extra.toList(),
            passcode = passcode.trim(),
            appointmentIdentifier = appointment?.identifier
        )
}

/**
 * Checks a device in one step at a time. The signature step only shows when the shop has repair terms
 * @param terms Shop's repair terms, blank skips signing
 * @param onSubmit Sends the check in, calls back with what went wrong or null if it worked
 */
@Composable
fun CheckInWizard(
    startingCustomer: Customer?,
    appointment: Appointment? = null,
    terms: String,
    checklists: List<IntakeChecklist> = emptyList(),
    onSearchCustomers: suspend (String) -> List<Customer>,
    onAddCustomer: (Customer, (Customer?, String?) -> Unit) -> Unit,
    onSearchTechnicians: suspend (String) -> List<SearchResult>,
    onRepeatCheck: suspend (Uuid, Device, Uuid, Uuid?) -> List<TicketSummary>,
    onOpenTicket: (Uuid) -> Unit,
    onSubmit: (CheckInRequest, (String?) -> Unit) -> Unit,
    onCancel: () -> Unit,
    onLoadDevices: suspend (Uuid) -> List<CustomerDevice> = { emptyList() },
    readers: List<CardReader> = emptyList(),
    surchargePercent: String = "",
    onReaderCharge: ((CardReader, Long, (CardCharge) -> Unit) -> Unit)? = null
) {
    val form = remember(startingCustomer, checklists, appointment) { CheckInForm(startingCustomer, checklists, appointment) }
    val needsSignature = terms.isNotBlank()
    val steps = CheckInStep.entries.filter { it != CheckInStep.SIGN || needsSignature }
    val spacing = TmTheme.spacing

    var step by remember(startingCustomer) { mutableStateOf(if (startingCustomer != null) CheckInStep.DEVICE else CheckInStep.CUSTOMER) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val problem = form.problem(step, needsSignature)
    val index = steps.indexOf(step)

    // The customer's devices, to pick from instead of typing
    var devices by remember { mutableStateOf<List<CustomerDevice>>(emptyList()) }
    LaunchedEffect(form.customer) {
        devices = form.customer?.let { onLoadDevices(it.identifier) }.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        // ------------------------------------------ Steps ------------------------------------------
        TmPageHeader(title = "Check in", onBack = onCancel)

        TmStepper(
            steps = steps.map { it.label },
            currentIndex = index,
            onSelect = { step = steps[it] },
            canOpen = { position -> position <= index || steps.take(position).all { form.problem(it, needsSignature) == null } }
        )

        Column(
            modifier = Modifier.widthIn(max = FORM_WIDTH),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            when (step) {
                CheckInStep.CUSTOMER -> CustomerStep(form, onSearchCustomers, onAddCustomer)
                CheckInStep.DEVICE -> DeviceStep(form, devices, onRepeatCheck, onOpenTicket)
                CheckInStep.REPAIR -> RepairStep(form, onSearchTechnicians, readers, surchargePercent, onReaderCharge)
                CheckInStep.SIGN -> SignStep(form, terms)
                CheckInStep.REVIEW -> ReviewStep(form, needsSignature)
            }

            (error ?: problem)?.let {
                TmNotice(text = it, kind = if (error != null) TmNoticeKind.ERROR else TmNoticeKind.INFO)
            }

            // ------------------------------------------ Moving between steps ------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md, Alignment.End)
            ) {
                TmButton(text = "Cancel", onClick = onCancel, variant = TmButtonVariant.GHOST, size = TmControlSize.LARGE)

                if (index > 0) {
                    TmButton(text = "Back", onClick = { step = steps[index - 1] }, variant = TmButtonVariant.SECONDARY, size = TmControlSize.LARGE)
                }

                // Another device at the same visit: this one is kept, and the device step starts again
                if (step == CheckInStep.REVIEW && form.extra.size + 1 < MAX_DEVICES) {
                    TmButton(
                        text = "Add another device",
                        enabled = steps.all { form.problem(it, needsSignature) == null },
                        variant = TmButtonVariant.SECONDARY,
                        size = TmControlSize.LARGE,
                        leadingIcon = Res.drawable.plus,
                        onClick = {
                            form.addAnother()
                            step = CheckInStep.DEVICE
                        }
                    )
                }

                if (step != CheckInStep.REVIEW) {
                    TmButton(
                        text = "Next: ${steps[index + 1].label.lowercase()}",
                        enabled = problem == null,
                        size = TmControlSize.LARGE,
                        onClick = { step = steps[index + 1] }
                    )
                } else {
                    TmButton(
                        text = if (form.extra.isEmpty()) "Check in" else "Check in ${form.extra.size + 1} devices",
                        enabled = steps.all { form.problem(it, needsSignature) == null },
                        loading = submitting,
                        size = TmControlSize.LARGE,
                        onClick = {
                            submitting = true
                            error = null
                            onSubmit(form.toRequest(needsSignature)) { problemSaving ->
                                submitting = false
                                error = problemSaving
                            }
                        }
                    )
                }
            }
        }
    }
}

// ------------------------------------------ Steps ------------------------------------------

@Composable
private fun CustomerStep(
    form: CheckInForm,
    onSearch: suspend (String) -> List<Customer>,
    onAdd: (Customer, (Customer?, String?) -> Unit) -> Unit
) {
    // An appointment for someone not on file yet starts by looking for them
    val booked = form.appointment?.takeIf { it.customerIdentifier == null }
    var query by remember { mutableStateOf(booked?.let { it.phone.ifBlank { it.customerName } }.orEmpty()) }
    var matches by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(300)
        matches = onSearch(query)
    }

    form.customer?.let { customer ->
        TmPanel(
            title = customer.name,
            subtitle = listOf(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(", "),
            icon = Res.drawable.user,
            actions = { TmButton(text = "Change customer", onClick = { form.customer = null }, variant = TmButtonVariant.SECONDARY) }
        ) {}
        return
    }

    if (adding) {
        TmCustomerForm(
            onSave = { customer, onResult ->
                onAdd(customer) { saved, problem ->
                    onResult(problem)
                    if (saved != null) {
                        form.customer = saved
                        form.signedName = saved.name
                        adding = false
                    }
                }
            },
            onCancel = { adding = false },
            initial = booked?.let { Customer(name = it.customerName, phone = it.phone, email = it.email) }
        )
        return
    }

    TmPanel(
        title = "Find the customer",
        icon = Res.drawable.user,
        actions = { TmButton(text = "New customer", onClick = { adding = true }, variant = TmButtonVariant.SECONDARY, leadingIcon = Res.drawable.user_plus) }
    ) {
        TmSearchField(
            modifier = Modifier.fillMaxWidth(),
            query = query,
            onQueryChange = { query = it },
            placeholder = "Phone number or name",
            size = TmControlSize.LARGE
        )

        if (query.isNotBlank() && matches.isEmpty()) {
            Text(text = "Nobody matches, add them as a new customer", style = TmTheme.typography.body, color = TmTheme.colors.textMuted)
        }

        Column {
            matches.forEach { customer ->
                TmListRow(
                    title = customer.name,
                    subtitle = listOf(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                    onClick = {
                        form.customer = customer
                        form.signedName = customer.name
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceStep(
    form: CheckInForm,
    devices: List<CustomerDevice>,
    onRepeatCheck: suspend (Uuid, Device, Uuid, Uuid?) -> List<TicketSummary>,
    onOpenTicket: (Uuid) -> Unit
) {
    var repeats by remember { mutableStateOf<List<TicketSummary>>(emptyList()) }

    // Look for a repeat repair once they've stopped typing the device
    LaunchedEffect(form.customer, form.manufacturer, form.model, form.serial, form.deviceIdentifier) {
        delay(500)
        val customer = form.customer ?: return@LaunchedEffect
        repeats = onRepeatCheck(customer.identifier, form.device, form.ticketIdentifier, form.deviceIdentifier)
    }

    TmPanel(
        title = if (form.extra.isEmpty()) "Device" else "Device ${form.extra.size + 1}",
        subtitle = if (form.extra.isEmpty()) null else "Checked in with ${form.extra.joinToString(", ") { it.ticket.device.toString().trim() }}",
        icon = Res.drawable.smartphone
    ) {
        // Theirs from before, so its repairs follow it
        if (devices.isNotEmpty()) {
            Text(text = "Their devices", style = TmTheme.typography.label, color = TmTheme.colors.textMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
                devices.forEach { device ->
                    TmFilterChip(text = device.label, selected = form.deviceIdentifier == device.identifier, onClick = { form.pick(device) })
                }
                TmFilterChip(text = "Another device", selected = form.deviceIdentifier == null, onClick = { form.pick(null) })
            }
        }

        TmDropdown(
            label = "Kind",
            options = DeviceKind.entries,
            selected = form.kind,
            onSelect = { form.kind = it },
            optionLabel = { it.label }
        )

        TmFieldPair(
            { TmTextField(modifier = it, value = form.manufacturer, onValueChange = { v -> form.manufacturer = v }, label = "Make", placeholder = "Apple") },
            { TmTextField(modifier = it, value = form.model, onValueChange = { v -> form.model = v }, label = "Model", placeholder = "iPhone 13") }
        )

        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.serial,
            onValueChange = { form.serial = it },
            label = "Serial or IMEI",
            textStyle = TmTheme.typography.numeric
        )

        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.condition,
            onValueChange = { form.condition = it },
            label = "Condition",
            placeholder = "Scratches, dents, cracks",
            singleLine = false,
            minLines = 2
        )

        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.accessories,
            onValueChange = { form.accessories = it },
            label = "Left with it",
            placeholder = "Charger, case, cable"
        )

        // Written down now, so nobody argues later about what was already broken
        if (form.checklistItems.isNotEmpty()) {
            Text(text = "Check it over", style = TmTheme.typography.label, color = TmTheme.colors.textMuted)
            Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
                form.checklistItems.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                        Text(text = item, style = TmTheme.typography.body, color = TmTheme.colors.text, modifier = Modifier.weight(1f))
                        TmSegmented(
                            options = CheckResult.entries,
                            selected = form.checks[item] ?: CheckResult.NOT_TESTED,
                            onSelect = { form.checks[item] = it },
                            optionLabel = { it.label },
                            size = TmControlSize.SMALL
                        )
                    }
                }
            }
        }

        TmPasswordField(
            modifier = Modifier.fillMaxWidth(),
            value = form.passcode,
            onValueChange = { form.passcode = it.take(IntakeChecklists.MAX_PASSCODE) },
            label = "Passcode or pattern, if they'll share it"
        )
        Text(
            text = "Kept encrypted, shown only to staff who can edit, and forgotten when it's picked up",
            style = TmTheme.typography.caption,
            color = TmTheme.colors.textMuted
        )
    }

    form.warrantyOf?.let { original ->
        TmNotice(
            kind = TmNoticeKind.SUCCESS,
            text = "Warranty return for ${original.ticket.label}, covered until ${original.ticket.warrantyUntil?.let { formatDate(it) }}. Its work starts free."
        )
        TmButton(text = "Not a warranty return", onClick = { form.warrantyOf = null }, variant = TmButtonVariant.GHOST)
    }

    if (repeats.isNotEmpty() && form.warrantyOf == null) {
        val now = remember { Clock.System.now().toEpochMilliseconds() }
        TmPanel(title = "Possible repeat repair", icon = Res.drawable.alert_triangle) {
            TmNotice(
                kind = TmNoticeKind.WARNING,
                text = "This customer brought this device in recently. Check whether it's covered by the last repair."
            )

            Column {
                repeats.forEach { summary ->
                    val ticket = summary.ticket
                    val covered = ticket.underWarranty(now)
                    TmListRow(
                        title = ticket.problem.ifBlank { "No problem noted" },
                        subtitle = listOfNotNull(
                            ticket.pickedUpAt?.let { "Picked up ${formatTimeAgo(it)}" } ?: "Checked in ${formatTimeAgo(ticket.identifier.timestampMillis())}",
                            ticket.warrantyUntil?.let { if (covered) "under warranty until ${formatDate(it)}" else "warranty ended ${formatDate(it)}" }
                        ).joinToString(", "),
                        leading = { TmTicketIdText(ticket.label, Modifier.width(104.dp)) },
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
                                if (covered) TmButton(text = "Warranty return", onClick = { form.warrantyOf = summary }, variant = TmButtonVariant.SECONDARY, size = TmControlSize.SMALL)
                                TmButton(text = "Open", onClick = { onOpenTicket(ticket.identifier) }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun RepairStep(
    form: CheckInForm,
    onSearchTechnicians: suspend (String) -> List<SearchResult>,
    readers: List<CardReader> = emptyList(),
    surchargePercent: String = "",
    onReaderCharge: ((CardReader, Long, (CardCharge) -> Unit) -> Unit)? = null
) {
    var techQuery by remember { mutableStateOf("") }
    var techMatches by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    val spacing = TmTheme.spacing

    LaunchedEffect(techQuery) {
        delay(300)
        techMatches = if (techQuery.isBlank()) emptyList() else onSearchTechnicians(techQuery)
    }

    TmPanel(title = "Repair", icon = Res.drawable.tool) {
        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.problem,
            onValueChange = { form.problem = it },
            label = "What's wrong, in the customer's words",
            singleLine = false,
            minLines = 3
        )

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            TmDropdown(
                modifier = Modifier.weight(1f),
                label = "Priority",
                options = TicketPriorityType.entries,
                selected = form.priority.priority,
                onSelect = { form.priority = TicketPriority(it) },
                optionLabel = { formatEnumName(it.name) },
                optionLeading = { TmDot(TmTheme.colors.tone(it).color) }
            )

            TmMoneyField(
                modifier = Modifier.weight(1f),
                label = "Estimate",
                cents = form.estimateCents,
                onCentsChange = { form.estimateCents = it },
                supportingText = "Leave at 0.00 for no estimate"
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(text = "Promised for", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
            ChipRow {
                listOf(0, 1, 2, 3, 5, 7).forEach { days ->
                    TmFilterChip(
                        text = formatDay(promiseTimeInDays(days)),
                        selected = form.promisedInDays == days,
                        onClick = { form.promisedInDays = days }
                    )
                }

                TmFilterChip(text = "No date", selected = form.promisedInDays == null, onClick = { form.promisedInDays = null })
            }
        }

        val paidOnReader = form.depositCharge
        if (paidOnReader != null) {
            // Paid already, so it can't be changed here; the check in claims it
            TmNotice(
                text = "${formatMoney(paidOnReader.amountCents)} deposit paid on ${paidOnReader.readerName}. " +
                    "If this check in isn't finished, refund it from Admin > Payments.",
                kind = TmNoticeKind.SUCCESS
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.Bottom) {
                TmMoneyField(
                    modifier = Modifier.width(180.dp),
                    label = "Deposit",
                    cents = form.depositInput,
                    onCentsChange = { form.depositInput = it }
                )

                TmSegmented(
                    label = "Paid by",
                    // Store credit when they have some; a gift card is taken at pickup, where its balance is checked
                    options = PaymentMethod.entries.filter { !it.storedValue || (it == PaymentMethod.STORE_CREDIT && (form.customer?.creditCents ?: 0) > 0) },
                    selected = form.depositMethod,
                    onSelect = { form.depositMethod = it },
                    optionLabel = { formatEnumName(it.name) }
                )
            }

            // A card deposit can go to a reader now instead of being typed in as taken
            val deposit = form.depositInput.takeIf { it > 0 }
            if (form.depositMethod == PaymentMethod.CARD && readers.isNotEmpty() && onReaderCharge != null && deposit != null) {
                var readerIdentifier by remember { mutableStateOf<String?>(null) }
                val reader = readers.firstOrNull { it.identifier == readerIdentifier } ?: readers.first()
                if (readers.size > 1) {
                    ChipRow {
                        readers.forEach { option ->
                            TmFilterChip(text = option.name, selected = option == reader, onClick = { readerIdentifier = option.identifier })
                        }
                    }
                }
                val charged = deposit + CardExtras.surchargeCents(deposit, surchargePercent)
                TmButton(
                    text = "Charge ${formatMoney(charged)} on ${reader.name}",
                    leadingIcon = Res.drawable.credit_card,
                    variant = TmButtonVariant.SECONDARY,
                    onClick = { onReaderCharge(reader, deposit) { form.depositCharge = it } }
                )
            }
        }

        val technician = form.technician
        if (technician != null) {
            TmKeyValueRow(label = "Technician") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = technician.text, style = TmTheme.typography.label, color = TmTheme.colors.text)
                    TmButton(text = "Change", onClick = { form.technician = null }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                }
            }
        } else {
            TmSearchField(
                modifier = Modifier.fillMaxWidth(),
                label = "Technician",
                query = techQuery,
                onQueryChange = { techQuery = it },
                placeholder = "Assign to someone (optional)"
            )

            Column {
                techMatches.forEach { match ->
                    TmListRow(
                        title = match.text,
                        icon = Res.drawable.user,
                        onClick = {
                            form.technician = match
                            techQuery = ""
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)
    ) { content() }
}

@Composable
private fun SignStep(form: CheckInForm, terms: String) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    TmPanel(title = "Customer signature", subtitle = "By signing, the customer agrees to the repair terms", icon = Res.drawable.edit_3) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .background(colors.surfaceSunken, TmTheme.shapes.control)
                .border(1.dp, colors.hairline, TmTheme.shapes.control)
        ) {
            Text(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.md),
                text = terms,
                style = TmTheme.typography.body,
                color = colors.text
            )
        }

        TmSignaturePad(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            strokes = form.strokes,
            onStrokesChange = { form.strokes = it },
            onSize = { form.aspectRatio = it }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.Bottom) {
            TmTextField(
                modifier = Modifier.weight(1f),
                value = form.signedName,
                onValueChange = { form.signedName = it },
                label = "Name of whoever signed"
            )

            TmButton(text = "Clear signature", onClick = { form.strokes = emptyList() }, variant = TmButtonVariant.SECONDARY)
        }
    }
}

@Composable
private fun ReviewStep(form: CheckInForm, needsSignature: Boolean) {
    TmPanel(title = "Check everything over", icon = Res.drawable.check_square) {
        if (form.extra.isNotEmpty()) {
            Column {
                form.extra.forEach { item ->
                    TmKeyValueRow(label = item.ticket.device.toString().trim(), value = item.ticket.problem)
                }
            }
            TmDivider()
        }

        Column {
            TmKeyValueRow(label = "Customer", value = form.customer?.name.orEmpty())
            TmKeyValueRow(label = "Device", value = form.device.toString().trim())
            if (form.serial.isNotBlank()) TmKeyValueRow(label = "Serial", value = form.serial, numeric = true)
            TmKeyValueRow(label = "Problem", value = form.problem.trim())
            if (form.condition.isNotBlank()) TmKeyValueRow(label = "Condition", value = form.condition.trim())
            if (form.accessories.isNotBlank()) TmKeyValueRow(label = "Left with it", value = form.accessories.trim())
            if (form.checks.isNotEmpty()) {
                val faulty = form.checks.filterValues { it == CheckResult.FAULTY }.keys
                TmKeyValueRow(label = "Checked", value = if (faulty.isEmpty()) "Nothing faulty" else "Faulty: ${faulty.joinToString(", ")}")
            }
            if (form.passcode.isNotBlank()) TmKeyValueRow(label = "Passcode", value = "Given")
            form.warrantyOf?.let { TmKeyValueRow(label = "Warranty return", value = "for ${it.ticket.label}") }
            TmKeyValueRow(label = "Priority", value = formatEnumName(form.priority.priority.name))
            if (form.estimateCents > 0) TmKeyValueRow(label = "Estimate", value = formatMoney(form.estimateCents), numeric = true)
            TmKeyValueRow(label = "Promised", value = form.promisedInDays?.let { formatDay(promiseTimeInDays(it)) } ?: "No date")
            form.depositCents?.let {
                val how = form.depositCharge?.let { charge -> "on ${charge.readerName}, paid" } ?: "by ${formatEnumName(form.depositMethod.name).lowercase()}"
                TmKeyValueRow(label = "Deposit", value = "${formatMoney(it)} $how")
            }
            TmKeyValueRow(label = "Technician", value = form.technician?.text ?: "Not assigned")
            if (needsSignature) TmKeyValueRow(label = "Signed by", value = form.signedName)
        }
    }
}
