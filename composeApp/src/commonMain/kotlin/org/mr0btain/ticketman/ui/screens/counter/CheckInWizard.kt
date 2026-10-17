package org.mr0btain.ticketman.ui.screens.counter

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
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.ticket.*
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.server.request.CheckInRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.KeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TicketIdText
import org.mr0btain.ticketman.ui.kit.feedback.TmDot
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.feedback.tone
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.SignaturePad
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.navigation.TmStepper
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.selection.TmFilterChip
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.alert_triangle
import ticketman.composeapp.generated.resources.check_square
import ticketman.composeapp.generated.resources.edit_3
import ticketman.composeapp.generated.resources.smartphone
import ticketman.composeapp.generated.resources.tool
import ticketman.composeapp.generated.resources.user
import ticketman.composeapp.generated.resources.user_plus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Widest the wizard's panels get, so lines stay readable on big screens */
private val FORM_WIDTH = 680.dp

private enum class CheckInStep(val label: String) {
    CUSTOMER("Customer"),
    DEVICE("Device"),
    REPAIR("Repair"),
    SIGN("Sign"),
    REVIEW("Review")
}

/** Everything typed into the wizard, kept here so going back a step doesn't lose it */
@OptIn(ExperimentalUuidApi::class)
private class CheckInForm(customer: Customer?) {
    // Made up front so the repeat check can leave this ticket out
    val ticketIdentifier: Uuid = Uuid.generateV7()

    var customer by mutableStateOf(customer)
    var manufacturer by mutableStateOf("")
    var model by mutableStateOf("")
    var serial by mutableStateOf("")
    var condition by mutableStateOf("")
    var accessories by mutableStateOf("")
    var problem by mutableStateOf("")
    var priority by mutableStateOf(TicketPriority(TicketPriorityType.NORMAL))
    var estimateCents by mutableLongStateOf(0L)
    var promisedInDays by mutableStateOf<Int?>(3)
    var depositInput by mutableLongStateOf(0L)
    var depositMethod by mutableStateOf(PaymentMethod.CARD)
    var technician by mutableStateOf<SearchResult?>(null)
    var strokes by mutableStateOf<List<List<SignaturePoint>>>(emptyList())
    var aspectRatio by mutableStateOf(3f)
    var signedName by mutableStateOf(customer?.cleanName().orEmpty())

    val device: Device
        get() = Device(manufacturer.trim(), model.trim(), serial.trim())

    val depositCents: Long?
        get() = depositInput.takeIf { it > 0 }

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
            ticket = Ticket(
                identifier = ticketIdentifier,
                device = device,
                priority = priority,
                problem = problem.trim(),
                condition = condition.trim(),
                accessories = accessories.trim(),
                estimateCents = estimateCents.takeIf { it > 0 },
                promisedAt = promisedInDays?.let { promiseTimeInDays(it) }
            ),
            customerIdentifier = customer!!.identifier,
            technicianIdentifier = technician?.identifier,
            deposit = depositCents?.let { PaymentRequest(depositMethod, it, "Deposit") },
            signature = if (needsSignature) SignatureInput(strokes, aspectRatio, signedName.trim()) else null
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
    terms: String,
    onSearchCustomers: suspend (String) -> List<Customer>,
    onAddCustomer: (Customer, (Customer?, String?) -> Unit) -> Unit,
    onSearchTechnicians: suspend (String) -> List<SearchResult>,
    onRepeatCheck: suspend (Uuid, Device, Uuid) -> List<TicketSummary>,
    onOpenTicket: (Uuid) -> Unit,
    onSubmit: (CheckInRequest, (String?) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    val form = remember(startingCustomer) { CheckInForm(startingCustomer) }
    val needsSignature = terms.isNotBlank()
    val steps = CheckInStep.entries.filter { it != CheckInStep.SIGN || needsSignature }
    val spacing = TmTheme.spacing

    var step by remember(startingCustomer) { mutableStateOf(if (startingCustomer != null) CheckInStep.DEVICE else CheckInStep.CUSTOMER) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val problem = form.problem(step, needsSignature)
    val index = steps.indexOf(step)

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
                CheckInStep.DEVICE -> DeviceStep(form, onRepeatCheck, onOpenTicket)
                CheckInStep.REPAIR -> RepairStep(form, onSearchTechnicians)
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

                if (step != CheckInStep.REVIEW) {
                    TmButton(
                        text = "Next: ${steps[index + 1].label.lowercase()}",
                        enabled = problem == null,
                        size = TmControlSize.LARGE,
                        onClick = { step = steps[index + 1] }
                    )
                } else {
                    TmButton(
                        text = "Check in",
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
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(300)
        matches = onSearch(query)
    }

    form.customer?.let { customer ->
        TmPanel(
            title = customer.cleanName(),
            subtitle = listOf(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(", "),
            icon = Res.drawable.user,
            actions = { TmButton(text = "Change customer", onClick = { form.customer = null }, variant = TmButtonVariant.SECONDARY) }
        ) {}
        return
    }

    if (adding) {
        CustomerForm(
            onSave = { customer, onResult ->
                onAdd(customer) { saved, problem ->
                    onResult(problem)
                    if (saved != null) {
                        form.customer = saved
                        form.signedName = saved.cleanName()
                        adding = false
                    }
                }
            },
            onCancel = { adding = false }
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
                    title = customer.cleanName(),
                    subtitle = listOf(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                    onClick = {
                        form.customer = customer
                        form.signedName = customer.cleanName()
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceStep(
    form: CheckInForm,
    onRepeatCheck: suspend (Uuid, Device, Uuid) -> List<TicketSummary>,
    onOpenTicket: (Uuid) -> Unit
) {
    var repeats by remember { mutableStateOf<List<TicketSummary>>(emptyList()) }

    // Look for a repeat repair once they've stopped typing the device
    LaunchedEffect(form.customer, form.manufacturer, form.model, form.serial) {
        delay(500)
        val customer = form.customer ?: return@LaunchedEffect
        repeats = onRepeatCheck(customer.identifier, form.device, form.ticketIdentifier)
    }

    TmPanel(title = "Device", icon = Res.drawable.smartphone) {
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
    }

    if (repeats.isNotEmpty()) {
        TmPanel(title = "Possible repeat repair", icon = Res.drawable.alert_triangle) {
            TmNotice(
                kind = TmNoticeKind.WARNING,
                text = "This customer brought this device in recently. Check whether it's covered by the last repair."
            )

            Column {
                repeats.forEach { summary ->
                    val ticket = summary.ticket
                    TmListRow(
                        title = ticket.problem.ifBlank { "No problem noted" },
                        subtitle = ticket.pickedUpAt?.let { "Picked up ${formatTimeAgo(it)}" }
                            ?: "Checked in ${formatTimeAgo(ticket.identifier.timestampMillis())}",
                        leading = { TicketIdText(ticket.label, Modifier.width(104.dp)) },
                        trailing = { TmButton(text = "Open", onClick = { onOpenTicket(ticket.identifier) }, variant = TmButtonVariant.GHOST) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RepairStep(
    form: CheckInForm,
    onSearchTechnicians: suspend (String) -> List<SearchResult>
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

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.Bottom) {
            TmMoneyField(
                modifier = Modifier.width(180.dp),
                label = "Deposit",
                cents = form.depositInput,
                onCentsChange = { form.depositInput = it }
            )

            TmSegmented(
                label = "Paid by",
                options = PaymentMethod.entries,
                selected = form.depositMethod,
                onSelect = { form.depositMethod = it },
                optionLabel = { formatEnumName(it.name) }
            )
        }

        val technician = form.technician
        if (technician != null) {
            KeyValueRow(label = "Technician") {
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

        SignaturePad(
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
        Column {
            KeyValueRow(label = "Customer", value = form.customer?.cleanName().orEmpty())
            KeyValueRow(label = "Device", value = form.device.toString().trim())
            if (form.serial.isNotBlank()) KeyValueRow(label = "Serial", value = form.serial, numeric = true)
            KeyValueRow(label = "Problem", value = form.problem.trim())
            if (form.condition.isNotBlank()) KeyValueRow(label = "Condition", value = form.condition.trim())
            if (form.accessories.isNotBlank()) KeyValueRow(label = "Left with it", value = form.accessories.trim())
            KeyValueRow(label = "Priority", value = formatEnumName(form.priority.priority.name))
            if (form.estimateCents > 0) KeyValueRow(label = "Estimate", value = Money.format(form.estimateCents), numeric = true)
            KeyValueRow(label = "Promised", value = form.promisedInDays?.let { formatDay(promiseTimeInDays(it)) } ?: "No date")
            form.depositCents?.let { KeyValueRow(label = "Deposit", value = "${Money.format(it)} by ${formatEnumName(form.depositMethod.name).lowercase()}") }
            KeyValueRow(label = "Technician", value = form.technician?.text ?: "Not assigned")
            if (needsSignature) KeyValueRow(label = "Signed by", value = form.signedName)
        }
    }
}
