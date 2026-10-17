package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.mr0btain.ticketman.ui.composables.EmptyMessage
import org.mr0btain.ticketman.ui.composables.SectionCard
import org.mr0btain.ticketman.ui.composables.SimpleTextItem
import org.mr0btain.ticketman.ui.composables.signature.SignaturePad
import org.mr0btain.ticketman.ui.composables.ticket.TicketPriorityDropDown
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
    var estimate by mutableStateOf("")
    var promisedInDays by mutableStateOf<Int?>(3)
    var deposit by mutableStateOf("")
    var depositMethod by mutableStateOf(PaymentMethod.CARD)
    var technician by mutableStateOf<SearchResult?>(null)
    var strokes by mutableStateOf<List<List<SignaturePoint>>>(emptyList())
    var aspectRatio by mutableStateOf(3f)
    var signedName by mutableStateOf(customer?.cleanName().orEmpty())

    val device: Device
        get() = Device(manufacturer.trim(), model.trim(), serial.trim())

    val depositCents: Long?
        get() = Money.parse(deposit)?.takeIf { it > 0 }

    fun problem(step: CheckInStep, needsSignature: Boolean): String? = when (step) {
        CheckInStep.CUSTOMER -> if (customer == null) "Pick or add a customer" else null
        CheckInStep.DEVICE -> if (manufacturer.isBlank() && model.isBlank()) "Enter the make or model" else null
        CheckInStep.REPAIR -> when {
            problem.isBlank() -> "Describe what's wrong"
            estimate.isNotBlank() && Money.parse(estimate) == null -> "Estimate isn't a price"
            deposit.isNotBlank() && Money.parse(deposit) == null -> "Deposit isn't an amount"
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
                estimateCents = Money.parse(estimate),
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

    var step by remember(startingCustomer) { mutableStateOf(if (startingCustomer != null) CheckInStep.DEVICE else CheckInStep.CUSTOMER) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val problem = form.problem(step, needsSignature)
    val index = steps.indexOf(step)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ------------------------------------------ Steps ------------------------------------------
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Check In", fontSize = 28.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.padding(8.dp))

            steps.forEachIndexed { position, each ->
                val reachable = position <= index || steps.take(position).all { form.problem(it, needsSignature) == null }

                FilterChip(
                    selected = each == step,
                    enabled = reachable,
                    onClick = { step = each },
                    label = { Text("${position + 1}. ${each.label}") }
                )
            }
        }

        when (step) {
            CheckInStep.CUSTOMER -> CustomerStep(form, onSearchCustomers, onAddCustomer)
            CheckInStep.DEVICE -> DeviceStep(form, onRepeatCheck, onOpenTicket)
            CheckInStep.REPAIR -> RepairStep(form, onSearchTechnicians)
            CheckInStep.SIGN -> SignStep(form, terms)
            CheckInStep.REVIEW -> ReviewStep(form, needsSignature)
        }

        (error ?: problem)?.let {
            Text(modifier = Modifier.padding(horizontal = 16.dp), text = it, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
        }

        // ------------------------------------------ Moving between steps ------------------------------------------
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }

            if (index > 0) {
                OutlinedButton(onClick = { step = steps[index - 1] }) { Text("Back") }
            }

            if (step != CheckInStep.REVIEW) {
                Button(
                    enabled = problem == null,
                    onClick = { step = steps[index + 1] }
                ) { Text("Next") }
            } else {
                Button(
                    enabled = !submitting && steps.all { form.problem(it, needsSignature) == null },
                    onClick = {
                        submitting = true
                        error = null
                        onSubmit(form.toRequest(needsSignature)) { problemSaving ->
                            submitting = false
                            error = problemSaving
                        }
                    }
                ) { Text("Check In") }
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
        SectionCard(modifier = Modifier.widthIn(max = 640.dp), title = "Customer") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = customer.cleanName(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = listOf(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(" - "))
                }

                OutlinedButton(onClick = { form.customer = null }) { Text("Change") }
            }
        }
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

    SectionCard(
        modifier = Modifier.widthIn(max = 640.dp),
        title = "Find the Customer",
        action = { Button(onClick = { adding = true }) { Text("New Customer") } }
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = query,
            onValueChange = { query = it },
            label = { Text("Phone number or name") },
            singleLine = true
        )

        if (query.isNotBlank() && matches.isEmpty()) EmptyMessage("Nobody matches, add them as a new customer")

        matches.forEach { customer ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                onClick = {
                    form.customer = customer
                    form.signedName = customer.cleanName()
                },
                shape = RoundedCornerShape(6.dp),
                color = Color.Transparent
            ) {
                Row(Modifier.padding(8.dp)) {
                    Text(modifier = Modifier.weight(1f), text = customer.cleanName(), fontWeight = FontWeight.SemiBold)
                    Text(text = listOf(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(" - "))
                }
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

    SectionCard(modifier = Modifier.widthIn(max = 640.dp), title = "Device") {
        Row {
            OutlinedTextField(
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                value = form.manufacturer,
                onValueChange = { form.manufacturer = it },
                label = { Text("Make") },
                singleLine = true
            )

            OutlinedTextField(
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                value = form.model,
                onValueChange = { form.model = it },
                label = { Text("Model") },
                singleLine = true
            )
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.serial,
            onValueChange = { form.serial = it },
            label = { Text("Serial or IMEI") },
            singleLine = true
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.condition,
            onValueChange = { form.condition = it },
            label = { Text("Condition (scratches, dents, cracks)") },
            minLines = 2
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.accessories,
            onValueChange = { form.accessories = it },
            label = { Text("Left with it (charger, case, cable)") },
            singleLine = true
        )
    }

    if (repeats.isNotEmpty()) {
        SectionCard(modifier = Modifier.widthIn(max = 640.dp), title = "Possible Repeat Repair") {
            Text(
                text = "This customer brought this device in recently. Check whether it's covered by the last repair.",
                fontSize = 14.sp,
                color = CustomMaterialColors.inProgress
            )

            repeats.forEach { summary ->
                val ticket = summary.ticket
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text = "${ticket.label} - ${ticket.problem.ifBlank { "no problem noted" }}", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = ticket.pickedUpAt?.let { "Picked up ${formatTimeAgo(it)}" }
                                ?: "Checked in ${formatTimeAgo(ticket.identifier.timestampMillis())}",
                            fontSize = 12.sp
                        )
                    }

                    TextButton(onClick = { onOpenTicket(ticket.identifier) }) { Text("Look") }
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

    LaunchedEffect(techQuery) {
        delay(300)
        techMatches = if (techQuery.isBlank()) emptyList() else onSearchTechnicians(techQuery)
    }

    SectionCard(modifier = Modifier.widthIn(max = 640.dp), title = "Repair") {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.problem,
            onValueChange = { form.problem = it },
            label = { Text("What's wrong, in the customer's words") },
            minLines = 3
        )

        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TicketPriorityDropDown(
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                priority = form.priority,
                onClick = { form.priority = it }
            )

            OutlinedTextField(
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                value = form.estimate,
                onValueChange = { form.estimate = it },
                label = { Text("Estimate ($)") },
                singleLine = true
            )
        }

        Text(modifier = Modifier.padding(top = 12.dp), text = "Promised for", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 1, 2, 3, 5, 7).forEach { days ->
                FilterChip(
                    selected = form.promisedInDays == days,
                    onClick = { form.promisedInDays = days },
                    label = { Text(formatDay(promiseTimeInDays(days))) }
                )
            }

            FilterChip(
                selected = form.promisedInDays == null,
                onClick = { form.promisedInDays = null },
                label = { Text("No date") }
            )
        }

        Text(modifier = Modifier.padding(top = 12.dp), text = "Deposit", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                modifier = Modifier.width(160.dp),
                value = form.deposit,
                onValueChange = { form.deposit = it },
                label = { Text("Amount ($)") },
                singleLine = true
            )

            Spacer(Modifier.padding(4.dp))

            PaymentMethod.entries.forEach { method ->
                FilterChip(
                    modifier = Modifier.padding(end = 4.dp),
                    selected = form.depositMethod == method,
                    onClick = { form.depositMethod = method },
                    label = { Text(formatEnumName(method.name)) }
                )
            }
        }

        Text(modifier = Modifier.padding(top = 12.dp), text = "Technician", fontWeight = FontWeight.SemiBold)
        val technician = form.technician
        if (technician != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(modifier = Modifier.weight(1f), text = technician.text)
                TextButton(onClick = { form.technician = null }) { Text("Change") }
            }
        } else {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = techQuery,
                onValueChange = { techQuery = it },
                label = { Text("Assign to (optional)") },
                singleLine = true
            )

            techMatches.forEach { match ->
                TextButton(onClick = {
                    form.technician = match
                    techQuery = ""
                }) { Text(match.text) }
            }
        }
    }
}

@Composable
private fun SignStep(form: CheckInForm, terms: String) {
    SectionCard(modifier = Modifier.widthIn(max = 760.dp), title = "Customer Signature") {
        Text(text = "Repair terms", fontWeight = FontWeight.SemiBold)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                text = terms,
                fontSize = 14.sp
            )
        }

        Text(text = "By signing, the customer agrees to the terms above.", fontSize = 13.sp)

        SignaturePad(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(vertical = 8.dp),
            strokes = form.strokes,
            onStrokesChange = { form.strokes = it },
            onSize = { form.aspectRatio = it }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = form.signedName,
                onValueChange = { form.signedName = it },
                label = { Text("Name of whoever signed") },
                singleLine = true
            )

            Spacer(Modifier.padding(4.dp))

            OutlinedButton(onClick = { form.strokes = emptyList() }) { Text("Clear") }
        }
    }
}

@Composable
private fun ReviewStep(form: CheckInForm, needsSignature: Boolean) {
    SectionCard(modifier = Modifier.widthIn(max = 640.dp), title = "Check Everything Over") {
        SimpleTextItem("Customer", form.customer?.cleanName().orEmpty())
        SimpleTextItem("Device", form.device.toString().trim())
        if (form.serial.isNotBlank()) SimpleTextItem("Serial", form.serial)
        SimpleTextItem("Problem", form.problem.trim())
        if (form.condition.isNotBlank()) SimpleTextItem("Condition", form.condition.trim())
        if (form.accessories.isNotBlank()) SimpleTextItem("Left with it", form.accessories.trim())
        SimpleTextItem("Priority", formatEnumName(form.priority.priority.name))
        Money.parse(form.estimate)?.let { SimpleTextItem("Estimate", Money.format(it)) }
        SimpleTextItem("Promised", form.promisedInDays?.let { formatDay(promiseTimeInDays(it)) } ?: "No date")
        form.depositCents?.let { SimpleTextItem("Deposit", "${Money.format(it)} ${formatEnumName(form.depositMethod.name).lowercase()}") }
        SimpleTextItem("Technician", form.technician?.text ?: "Not assigned")
        if (needsSignature) SimpleTextItem("Signed by", form.signedName)
    }
}
