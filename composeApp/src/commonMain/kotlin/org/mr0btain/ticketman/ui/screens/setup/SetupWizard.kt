package org.mr0btain.ticketman.ui.screens.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.invoice.Warranty
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.UsernameRules
import org.mr0btain.ticketman.server.request.InviteRequest
import org.mr0btain.ticketman.server.response.InviteResponse
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmBackdrop
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.navigation.TmStepper
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.navigation.shellInsets
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.dollar_sign
import ticketman.composeapp.generated.resources.edit_3
import ticketman.composeapp.generated.resources.home
import ticketman.composeapp.generated.resources.user_plus
import ticketman.composeapp.generated.resources.users

/** Widest the wizard gets, so lines stay readable */
private val SETUP_WIDTH = 640.dp

internal enum class SetupStep(val label: String) {
    SHOP("Shop"),
    MONEY("Money"),
    TERMS("Terms"),
    STAFF("Staff"),
    DONE("Done")
}

/** A starting point for the repair terms, which the shop changes to its own */
internal const val SAMPLE_TERMS =
    "We aren't responsible for data on the device; back it up before leaving it with us. Estimates can change once " +
        "the device is opened, and we'll ask before doing more. Parts and labor are guaranteed for the warranty " +
        "period from pickup, not covering new damage or liquid. Devices left more than 30 days after they're ready " +
        "may be charged storage, and after 90 days are treated as abandoned."

/**
 * Everything typed into the setup, kept here so moving between steps doesn't lose it
 * @param invited Staff invited so far, with their codes, which are only shown here
 */
internal class SetupForm(start: ShopProfile) {
    var profile by mutableStateOf(start)
    var warrantyDays by mutableStateOf(start.warrantyDays.takeIf { it > 0 }?.toString().orEmpty())
    val invited = mutableStateListOf<InviteResponse>()

    fun problem(step: SetupStep): String? = when (step) {
        SetupStep.SHOP -> when {
            profile.name.isBlank() -> "Enter the shop's name"
            else -> ShopProfile.publicUrlProblem(profile.publicUrl)
        }
        SetupStep.MONEY -> {
            val symbol = profile.currencySymbol.trim()
            val days = warrantyDays.toIntOrNull()
            when {
                symbol.isEmpty() || symbol.length > Money.MAX_SYMBOL_LENGTH || symbol.any { it.isDigit() } ->
                    "The currency symbol is 1 to ${Money.MAX_SYMBOL_LENGTH} characters, no digits"
                profile.taxRate.isNotBlank() && Money.percentToBasisPoints(profile.taxRate) == null -> "The tax rate is a percent, like 8.25"
                warrantyDays.isNotBlank() && (days == null || days !in 0..Warranty.MAX_DAYS) -> "Warranty is 0 to ${Warranty.MAX_DAYS} days"
                else -> null
            }
        }
        SetupStep.TERMS, SetupStep.STAFF, SetupStep.DONE -> null
    }

    /** The profile as it'll be saved */
    fun toProfile(): ShopProfile =
        profile.copy(
            name = profile.name.trim(),
            currencySymbol = profile.currencySymbol.trim(),
            taxRate = profile.taxRate.trim(),
            publicUrl = profile.publicUrl.trim().trimEnd('/'),
            warrantyDays = warrantyDays.toIntOrNull() ?: 0,
            repairTerms = profile.repairTerms.trim()
        )
}

/**
 * The first run setup, taking the whole app for an admin on a new server: the shop's details, money and tax, repair
 * terms and inviting staff. Everything here is also in Admin later, so skipping is fine
 * @param onFinish Saves the profile, calling back with what went wrong or null
 * @param onSkip Leaves it for later, calling back the same way
 * @param onInvite Invites someone, calling back with the invite or what went wrong
 */
@Composable
fun SetupWizard(
    start: ShopProfile,
    onFinish: (ShopProfile, (String?) -> Unit) -> Unit,
    onSkip: ((String?) -> Unit) -> Unit,
    onInvite: (InviteRequest, (InviteResponse?, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val form = remember(start) { SetupForm(start) }
    SetupWizard(form, onFinish, onSkip, onInvite, modifier)
}

@Composable
internal fun SetupWizard(
    form: SetupForm,
    onFinish: (ShopProfile, (String?) -> Unit) -> Unit,
    onSkip: ((String?) -> Unit) -> Unit,
    onInvite: (InviteRequest, (InviteResponse?, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    startAt: SetupStep = SetupStep.SHOP
) {
    val spacing = TmTheme.spacing
    val steps = SetupStep.entries
    var step by remember { mutableStateOf(startAt) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val index = steps.indexOf(step)
    val problem = form.problem(step)

    fun done(failed: String?) {
        saving = false
        error = failed
    }

    TmBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(shellInsets)
                .verticalScroll(rememberScrollState())
                .padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = SETUP_WIDTH).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                TmPageHeader(
                    title = "Set up your shop",
                    onBack = null,
                    subtitle = "A few things customers see and the counter needs. All of it can be changed later in Admin."
                )

                TmStepper(
                    steps = steps.map { it.label },
                    currentIndex = index,
                    onSelect = { step = steps[it] },
                    canOpen = { position -> position <= index || steps.take(position).all { form.problem(it) == null } }
                )

                when (step) {
                    SetupStep.SHOP -> ShopStep(form)
                    SetupStep.MONEY -> MoneyStep(form)
                    SetupStep.TERMS -> TermsStep(form)
                    SetupStep.STAFF -> StaffStep(form, onInvite)
                    SetupStep.DONE -> DoneStep(form)
                }

                (error ?: problem)?.let { TmNotice(text = it, kind = if (error != null) TmNoticeKind.ERROR else TmNoticeKind.INFO) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TmButton(
                        text = "Skip setup",
                        variant = TmButtonVariant.GHOST,
                        enabled = !saving,
                        onClick = {
                            saving = true
                            error = null
                            onSkip(::done)
                        }
                    )
                    Spacer(Modifier.weight(1f))
                    if (index > 0) {
                        TmButton(text = "Back", variant = TmButtonVariant.SECONDARY, size = TmControlSize.LARGE, onClick = { step = steps[index - 1] })
                    }
                    if (step != SetupStep.DONE) {
                        TmButton(
                            text = "Next: ${steps[index + 1].label.lowercase()}",
                            enabled = problem == null,
                            size = TmControlSize.LARGE,
                            onClick = { step = steps[index + 1] }
                        )
                    } else {
                        TmButton(
                            text = "Finish",
                            enabled = steps.all { form.problem(it) == null },
                            loading = saving,
                            size = TmControlSize.LARGE,
                            onClick = {
                                saving = true
                                error = null
                                onFinish(form.toProfile(), ::done)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopStep(form: SetupForm) {
    val profile = form.profile
    fun change(new: ShopProfile) {
        form.profile = new
    }

    TmPanel(title = "Your shop", subtitle = "On claim slips, invoices, messages and the status page", icon = Res.drawable.home) {
        TmTextField(modifier = Modifier.fillMaxWidth(), value = profile.name, onValueChange = { change(profile.copy(name = it)) }, label = "Shop name", placeholder = "Bench Repair")
        TmFieldPair(
            { TmTextField(modifier = it, value = profile.phone, onValueChange = { v -> change(profile.copy(phone = v)) }, label = "Phone", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)) },
            { TmTextField(modifier = it, value = profile.email, onValueChange = { v -> change(profile.copy(email = v.trim())) }, label = "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)) }
        )
        TmTextField(modifier = Modifier.fillMaxWidth(), value = profile.street, onValueChange = { change(profile.copy(street = it)) }, label = "Street")
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
            TmTextField(modifier = Modifier.weight(2f), value = profile.city, onValueChange = { v -> change(profile.copy(city = v)) }, label = "City")
            TmTextField(modifier = Modifier.weight(1f), value = profile.state, onValueChange = { v -> change(profile.copy(state = v)) }, label = "State")
            TmTextField(modifier = Modifier.weight(1f), value = profile.zip, onValueChange = { v -> change(profile.copy(zip = v)) }, label = "Zip")
        }
        TmTextField(modifier = Modifier.fillMaxWidth(), value = profile.website, onValueChange = { change(profile.copy(website = it.trim())) }, label = "Website")
        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = profile.publicUrl,
            onValueChange = { change(profile.copy(publicUrl = it.trim())) },
            label = "Public address",
            placeholder = "https://repairs.example.com",
            supportingText = "Where customers reach this server for their repair's status page and texts. Leave it empty if they can't yet"
        )
    }
}

@Composable
private fun MoneyStep(form: SetupForm) {
    val profile = form.profile
    fun change(new: ShopProfile) {
        form.profile = new
    }

    TmPanel(title = "Money and tax", subtitle = "Used on every invoice and estimate", icon = Res.drawable.dollar_sign) {
        TmFieldPair(
            {
                TmTextField(
                    modifier = it,
                    value = profile.currencySymbol,
                    onValueChange = { v -> change(profile.copy(currencySymbol = v.take(Money.MAX_SYMBOL_LENGTH))) },
                    label = "Currency symbol",
                    placeholder = "$"
                )
            },
            {
                TmTextField(
                    modifier = it,
                    value = profile.taxRate,
                    onValueChange = { v -> change(profile.copy(taxRate = v)) },
                    label = "Sales tax %",
                    placeholder = "8.25",
                    supportingText = "Empty for none. More rates can be added in Admin",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        )
        TmFieldPair(
            {
                TmMoneyField(
                    modifier = it,
                    label = "Labor per hour",
                    cents = profile.laborRateCents,
                    onCentsChange = { v -> change(profile.copy(laborRateCents = v)) },
                    supportingText = "For time logged on a ticket"
                )
            },
            {
                TmTextField(
                    modifier = it,
                    value = form.warrantyDays,
                    onValueChange = { v -> form.warrantyDays = v.filter { c -> c.isDigit() }.take(4) },
                    label = "Warranty days",
                    placeholder = "90",
                    supportingText = "From pickup. Empty for none",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        )
        TmDropdown(
            modifier = Modifier.fillMaxWidth(),
            label = "Printer paper",
            options = PaperSize.entries.filter { it != PaperSize.LABEL },
            selected = profile.defaultPaper,
            onSelect = { change(profile.copy(defaultPaper = it)) },
            optionLabel = { it.label }
        )
    }
}

@Composable
private fun TermsStep(form: SetupForm) {
    val profile = form.profile

    TmPanel(
        title = "Repair terms",
        subtitle = "What customers sign at check-in. Left empty, check-in doesn't ask for a signature",
        icon = Res.drawable.edit_3,
        actions = {
            if (profile.repairTerms.isBlank()) {
                TmButton(text = "Start from a sample", variant = TmButtonVariant.SECONDARY, size = TmControlSize.SMALL, onClick = { form.profile = profile.copy(repairTerms = SAMPLE_TERMS) })
            }
        }
    ) {
        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = profile.repairTerms,
            onValueChange = { form.profile = profile.copy(repairTerms = it) },
            label = "Terms",
            singleLine = false,
            minLines = 6
        )
        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = profile.invoiceFooter,
            onValueChange = { form.profile = profile.copy(invoiceFooter = it) },
            label = "Invoice footer",
            placeholder = "Thanks for choosing us!",
            singleLine = false,
            minLines = 2
        )
    }
}

@Composable
private fun StaffStep(form: SetupForm, onInvite: (InviteRequest, (InviteResponse?, String?) -> Unit) -> Unit) {
    val spacing = TmTheme.spacing
    val colors = TmTheme.colors
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(AccessLevel.NORMAL) }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    val usernameProblem = username.takeIf { it.isNotBlank() }?.let { UsernameRules.problem(it) }

    TmPanel(
        title = "Staff",
        subtitle = "Each person gets a code to log in with once, then picks their own password. More can be invited from Admin > Users",
        icon = Res.drawable.users
    ) {
        TmFieldPair(
            { TmTextField(modifier = it, value = name, onValueChange = { v -> name = v }, label = "Name", placeholder = "Priya Raman") },
            {
                TmTextField(
                    modifier = it,
                    value = username,
                    onValueChange = { v -> username = v.trim().lowercase() },
                    label = "Username",
                    placeholder = "priya",
                    error = usernameProblem
                )
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.Bottom) {
            TmDropdown(
                modifier = Modifier.weight(1f),
                label = "Access",
                options = listOf(AccessLevel.NORMAL, AccessLevel.RESTRICTED, AccessLevel.ADMIN),
                selected = level,
                onSelect = { level = it },
                optionLabel = { accessLabel(it) }
            )
            TmButton(
                text = "Invite",
                leadingIcon = Res.drawable.user_plus,
                enabled = username.isNotBlank() && usernameProblem == null,
                loading = sending,
                onClick = {
                    sending = true
                    failed = null
                    onInvite(InviteRequest(username = username, name = name.trim(), accessLevel = level)) { invite, problem ->
                        sending = false
                        failed = problem
                        if (invite != null) {
                            form.invited += invite
                            name = ""
                            username = ""
                        }
                    }
                }
            )
        }
        failed?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }

        form.invited.forEach { invite ->
            TmListRow(
                title = invite.user.name.ifBlank { invite.user.username },
                subtitle = "${invite.user.username} · ${accessLabel(invite.user.accessLevel)} · code good until ${formatDateTime(invite.expiresAt)}",
                trailing = { Text(text = invite.code, style = TmTheme.typography.numeric, color = colors.text) }
            )
        }
        if (form.invited.isNotEmpty()) {
            Text(text = "Write the codes down or hand them over now, they aren't shown again.", style = TmTheme.typography.caption, color = colors.textMuted)
        }
    }
}

@Composable
private fun DoneStep(form: SetupForm) {
    val profile = form.toProfile()
    val colors = TmTheme.colors

    TmPanel(title = "Ready", subtitle = "Finish to start checking devices in", icon = Res.drawable.home) {
        Column {
            TmKeyValueRow(label = "Shop", value = profile.name)
            TmKeyValueRow(label = "Phone", value = profile.phone.ifBlank { "None" })
            TmKeyValueRow(label = "Sales tax", value = profile.taxRate.ifBlank { "None" }.let { if (profile.taxRate.isBlank()) it else "$it%" }, numeric = profile.taxRate.isNotBlank())
            TmKeyValueRow(label = "Labor per hour", value = if (profile.laborRateCents > 0) formatMoney(profile.laborRateCents) else "Not set", numeric = profile.laborRateCents > 0)
            TmKeyValueRow(label = "Warranty", value = if (profile.warrantyDays > 0) "${profile.warrantyDays} days" else "None")
            TmKeyValueRow(label = "Repair terms", value = if (profile.repairTerms.isBlank()) "None, no signature at check-in" else "Signed at check-in")
            TmKeyValueRow(label = "Staff invited", value = form.invited.size.toString())
        }
        Text(
            text = "Ticket statuses, intake checklists, the repair menu, messages, card payments and more locations are " +
                "in Admin whenever you want them.",
            style = TmTheme.typography.caption,
            color = colors.textMuted
        )
    }
}

private fun accessLabel(level: AccessLevel): String = when (level) {
    AccessLevel.ADMIN -> "Admin, everything"
    AccessLevel.NORMAL -> "Normal, works tickets and the counter"
    AccessLevel.RESTRICTED -> "Restricted, looks only"
    AccessLevel.NONE -> "None"
}
