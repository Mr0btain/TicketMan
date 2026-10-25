package org.mr0btain.ticketman.ui.screens.admin.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.payment.*
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.containers.TmTextEditDialog
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmCheckbox
import org.mr0btain.ticketman.ui.kit.selection.TmFilterChip
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.selection.TmSwitch
import org.mr0btain.ticketman.ui.kit.theme.TmColors
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.theme.TmTone
import org.mr0btain.ticketman.ui.screens.admin.messages.SecretField
import org.mr0btain.ticketman.server.ApiRoutes
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.credit_card
import ticketman.composeapp.generated.resources.globe
import ticketman.composeapp.generated.resources.inbox
import ticketman.composeapp.generated.resources.tablet
import ticketman.composeapp.generated.resources.x

private val PANEL_WIDTH = 520.dp

/**
 * How the shop takes cards through Stripe or Square, its readers and paying online, and every charge sent. Edits
 * are kept here until saved, and reset when the server sends the settings back
 * @param publicUrl The shop's public address, which links and webhooks need
 * @param accountReaders The readers the processor has, once [onFindReaders] asked
 * @param onRefund Gives back a paid charge that never made it onto an invoice, with why
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPaymentsTab(
    settings: PaymentSettings?,
    publicUrl: String,
    accountReaders: List<CardReader>?,
    charges: List<CardCharge>,
    chargesEnd: Boolean,
    isBusy: Boolean,
    onSave: (PaymentSettings) -> Unit,
    onFindReaders: () -> Unit,
    onRefund: (CardCharge, String) -> Unit,
    onLoadMore: () -> Unit
) {
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        if (settings == null) {
            TmNotice(text = "Couldn't load the payment settings. Check the connection, then refresh.", kind = TmNoticeKind.ERROR)
        } else {
            SettingsPanels(settings, publicUrl, accountReaders, isBusy, onSave, onFindReaders)
        }

        ChargesPanel(charges, chargesEnd, onRefund, onLoadMore)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPanels(
    loaded: PaymentSettings,
    publicUrl: String,
    accountReaders: List<CardReader>?,
    isBusy: Boolean,
    onSave: (PaymentSettings) -> Unit,
    onFindReaders: () -> Unit
) {
    var draft by remember(loaded) { mutableStateOf(loaded) }
    val problem = draft.problem()
    val spacing = TmTheme.spacing

    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.weight(1f),
            text = "Card payments go through the shop's own Stripe or Square account. The keys stay on the server.",
            style = TmTheme.typography.body,
            color = TmTheme.colors.textMuted
        )
        TmButton(text = "Save settings", enabled = draft != loaded && problem == null && !isBusy, onClick = { onSave(draft) })
    }
    if (draft != loaded) problem?.let { TmNotice(text = it, kind = TmNoticeKind.WARNING) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        ProcessorPanel(draft, publicUrl) { draft = it }
        ReadersPanel(draft, accountReaders, saved = loaded.isConfigured && draft.provider == loaded.provider, isBusy, onFindReaders) { draft = it }
        OnlinePanel(draft, publicUrl) { draft = it }
    }
}

@Composable
private fun ProcessorPanel(draft: PaymentSettings, publicUrl: String, onChange: (PaymentSettings) -> Unit) {
    val colors = TmTheme.colors

    TmPanel(
        modifier = Modifier.widthIn(max = PANEL_WIDTH).fillMaxWidth(),
        title = "Card processor",
        subtitle = "Readers at the counter and payment links",
        icon = Res.drawable.credit_card,
        actions = { TmSwitch(checked = draft.enabled, onCheckedChange = { onChange(draft.copy(enabled = it)) }, contentDescription = "Take cards") }
    ) {
        TmSegmented(
            label = "Processor",
            options = PaymentProvider.entries,
            selected = draft.provider,
            // Readers belong to one account, so they don't carry over
            onSelect = { provider -> if (provider != draft.provider) onChange(draft.copy(provider = provider, readers = emptyList())) },
            optionLabel = { it.label }
        )

        TmTextField(
            modifier = Modifier.width(120.dp),
            value = draft.currency,
            onValueChange = { onChange(draft.copy(currency = it.filter { c -> c.isLetter() }.take(3).uppercase())) },
            label = "Currency",
            placeholder = "USD",
            textStyle = TmTheme.typography.numeric,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )

        val webhook = publicUrl.trim().trimEnd('/').ifBlank { null }?.let { "$it${ApiRoutes.STATUS}/payments/${draft.provider.name.lowercase()}/webhook" }

        when (draft.provider) {
            PaymentProvider.STRIPE -> {
                val stripe = draft.stripe
                SecretField(Modifier.fillMaxWidth(), stripe.secretKey, draft.stripeKeySet, "Secret key") { onChange(draft.copy(stripe = stripe.copy(secretKey = it))) }
                SecretField(Modifier.fillMaxWidth(), stripe.webhookSecret, draft.stripeWebhookSecretSet, "Webhook signing secret (optional)") {
                    onChange(draft.copy(stripe = stripe.copy(webhookSecret = it)))
                }
            }

            PaymentProvider.SQUARE -> {
                val square = draft.square
                TmSegmented(
                    label = "Environment",
                    options = SquareEnvironment.entries,
                    selected = square.environment,
                    onSelect = { onChange(draft.copy(square = square.copy(environment = it))) },
                    optionLabel = { it.label }
                )
                TmFieldPair(
                    { SecretField(it, square.accessToken, draft.squareTokenSet, "Access token") { v -> onChange(draft.copy(square = square.copy(accessToken = v))) } },
                    { TmTextField(modifier = it, value = square.locationId, onValueChange = { v -> onChange(draft.copy(square = square.copy(locationId = v.trim()))) }, label = "Location ID") }
                )
                SecretField(Modifier.fillMaxWidth(), square.webhookSignatureKey, draft.squareWebhookKeySet, "Webhook signature key (optional)") {
                    onChange(draft.copy(square = square.copy(webhookSignatureKey = it)))
                }
            }

            PaymentProvider.SIMULATED -> Text(
                text = "Pretends to take cards, only on a server started in development mode. A reader is paid after 2 seconds, unless the amount ends in .02.",
                style = TmTheme.typography.caption,
                color = colors.textMuted
            )
        }

        if (draft.provider != PaymentProvider.SIMULATED) {
            Text(
                text = webhook?.let { "Webhooks are optional, the server asks about each payment itself. To get them sooner, point one at $it" }
                    ?: "Webhooks need the shop's public address, set on the Server and data tab. Without them the server asks about each payment itself.",
                style = TmTheme.typography.caption,
                color = colors.textMuted
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadersPanel(
    draft: PaymentSettings,
    accountReaders: List<CardReader>?,
    saved: Boolean,
    isBusy: Boolean,
    onFindReaders: () -> Unit,
    onChange: (PaymentSettings) -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    var identifier by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    TmPanel(
        modifier = Modifier.widthIn(max = PANEL_WIDTH).fillMaxWidth(),
        title = "Card readers",
        subtitle = "The ones the counter offers",
        icon = Res.drawable.tablet
    ) {
        if (draft.readers.isEmpty()) {
            Text(text = "None yet. Card payments are typed in by hand until one is added.", style = TmTheme.typography.body, color = colors.textMuted)
        }

        draft.readers.forEach { reader ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = reader.name, style = TmTheme.typography.label, color = colors.text)
                    Text(text = reader.identifier, style = TmTheme.typography.numeric, color = colors.textMuted)
                }
                TmIconButton(icon = Res.drawable.x, contentDescription = "Remove ${reader.name}", size = TmControlSize.SMALL, onClick = {
                    onChange(draft.copy(readers = draft.readers - reader))
                })
            }
        }

        TmDivider()

        // From the account, once its keys are saved
        TmButton(
            text = "Find the account's readers",
            variant = TmButtonVariant.SECONDARY,
            enabled = saved && !isBusy,
            onClick = onFindReaders
        )
        if (!saved) Text(text = "Save the keys first to look the readers up", style = TmTheme.typography.caption, color = colors.textMuted)
        accountReaders?.filter { found -> draft.readers.none { it.identifier == found.identifier } }?.takeIf { it.isNotEmpty() }?.let { found ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                found.forEach { reader ->
                    TmFilterChip(text = "Add ${reader.name}", selected = false, onClick = { onChange(draft.copy(readers = draft.readers + reader)) })
                }
            }
        }

        // Or by hand, with the id from the processor's dashboard
        TmFieldPair(
            { TmTextField(modifier = it, value = identifier, onValueChange = { v -> identifier = v.trim() }, label = "Reader ID", textStyle = TmTheme.typography.numeric) },
            { TmTextField(modifier = it, value = name, onValueChange = { v -> name = v }, label = "Name", placeholder = "Front counter") }
        )
        TmButton(
            text = "Add reader",
            variant = TmButtonVariant.GHOST,
            enabled = identifier.isNotBlank() && name.isNotBlank() && draft.readers.none { it.identifier == identifier },
            onClick = {
                onChange(draft.copy(readers = draft.readers + CardReader(identifier, name.trim())))
                identifier = ""
                name = ""
            }
        )
    }
}

@Composable
private fun OnlinePanel(draft: PaymentSettings, publicUrl: String, onChange: (PaymentSettings) -> Unit) {
    TmPanel(
        modifier = Modifier.widthIn(max = PANEL_WIDTH).fillMaxWidth(),
        title = "Paying online",
        subtitle = "From the customer status page",
        icon = Res.drawable.globe
    ) {
        TmCheckbox(
            checked = draft.onlinePayments,
            onCheckedChange = { onChange(draft.copy(onlinePayments = it)) },
            label = "Customers can pay the balance from their status page"
        )
        if (publicUrl.isBlank()) {
            TmNotice(text = "Paying online and payment links need the shop's public address, set on the Server and data tab.", kind = TmNoticeKind.INFO)
        }
    }
}

@Composable
private fun ChargesPanel(
    charges: List<CardCharge>,
    chargesEnd: Boolean,
    onRefund: (CardCharge, String) -> Unit,
    onLoadMore: () -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    var refunding by remember { mutableStateOf<CardCharge?>(null) }

    TmPanel(
        modifier = Modifier.fillMaxWidth(),
        title = "Card charges",
        subtitle = "Newest first. One paid but not on an invoice, like a sale that wasn't finished, can be refunded here",
        icon = Res.drawable.inbox
    ) {
        if (charges.isEmpty()) {
            TmEmptyState(title = "No card charges yet", message = "They show here as readers and links are used")
            return@TmPanel
        }

        charges.forEachIndexed { index, charge ->
            if (index > 0) TmDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                TmMoneyText(cents = charge.amountCents, modifier = Modifier.width(96.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Text(
                        text = listOfNotNull(
                            charge.invoiceLabel.ifBlank { if (charge.unclaimed) "Not on an invoice" else null },
                            charge.readerName.ifBlank { charge.kind.label },
                            charge.provider.label
                        ).joinToString(", "),
                        style = TmTheme.typography.label,
                        color = colors.text
                    )
                    Text(
                        text = listOfNotNull(formatDateTime(charge.createdAt), charge.createdBy.ifBlank { null }?.let { "by $it" }, charge.error.ifBlank { null }).joinToString(", "),
                        style = TmTheme.typography.caption,
                        color = if (charge.status == CardChargeStatus.FAILED) colors.danger.color else colors.textMuted
                    )
                }

                TmBadge(text = charge.status.label, tone = colors.chargeTone(charge.status), dot = false)
                if (charge.unclaimed) TmButton(text = "Refund", variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL, onClick = { refunding = charge })
            }
        }

        if (!chargesEnd) TmButton(text = "Load more", variant = TmButtonVariant.GHOST, onClick = onLoadMore)
    }

    TmTextEditDialog(
        label = "Why it's being refunded",
        value = "",
        visible = refunding != null,
        onConfirm = { reason ->
            val charge = refunding
            if (reason.isBlank() || charge == null) false else {
                onRefund(charge, reason.trim())
                refunding = null
                true
            }
        },
        onCancel = { refunding = null },
        keyboardType = KeyboardType.Text,
        hint = "Goes back onto the card it was paid with"
    )
}

private fun TmColors.chargeTone(status: CardChargeStatus): TmTone = when (status) {
    CardChargeStatus.PENDING -> warning
    CardChargeStatus.SUCCEEDED -> success
    CardChargeStatus.FAILED -> danger
    CardChargeStatus.CANCELLED, CardChargeStatus.REFUNDED -> neutral
}
