package org.mr0btain.ticketman.ui.screens.admin.messages

import org.mr0btain.ticketman.components.data.rememberKept
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.data.notify.*
import org.mr0btain.ticketman.data.ticket.CustomStatus
import org.mr0btain.ticketman.data.ticket.label
import org.mr0btain.ticketman.data.ticket.selectable
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.feedback.tone
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmPasswordField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmCheckbox
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.selection.TmFilterChip
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.selection.TmSwitch
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.bell
import ticketman.composeapp.generated.resources.edit_3
import ticketman.composeapp.generated.resources.inbox
import ticketman.composeapp.generated.resources.mail
import ticketman.composeapp.generated.resources.message_square
import ticketman.composeapp.generated.resources.send

private val PANEL_WIDTH = 520.dp

/**
 * How the shop sends messages to customers, and every message sent. Edits are kept here until saved, and reset when
 * the server sends the settings back
 * @param statuses The shop's own statuses, to pick which ones send a message
 * @param onTest Sends a test with the settings as they are on screen
 */
@Composable
fun AdminMessagesTab(
    settings: NotificationSettings?,
    statuses: List<CustomStatus>,
    outbox: List<OutboxMessage>,
    outboxEnd: Boolean,
    isBusy: Boolean,
    onSave: (NotificationSettings) -> Unit,
    onTest: (NotificationSettings, NotificationChannel, String) -> Unit,
    onRetry: (OutboxMessage) -> Unit,
    onCancel: (OutboxMessage) -> Unit,
    onLoadMore: () -> Unit
) {
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        if (settings == null) {
            TmNotice(text = "Couldn't load the message settings. Check the connection, then refresh.", kind = TmNoticeKind.ERROR)
        } else {
            SettingsPanels(settings, statuses, isBusy, onSave, onTest)
        }

        OutboxPanel(outbox, outboxEnd, onRetry, onCancel, onLoadMore)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPanels(
    loaded: NotificationSettings,
    statuses: List<CustomStatus>,
    isBusy: Boolean,
    onSave: (NotificationSettings) -> Unit,
    onTest: (NotificationSettings, NotificationChannel, String) -> Unit
) {
    var draft by rememberKept("messages", loaded) { mutableStateOf(loaded) }
    val problem = draft.problem()
    val spacing = TmTheme.spacing

    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.weight(1f),
            text = "Messages go to customers who agreed to them on their record, and staff can send one from a ticket.",
            style = TmTheme.typography.body,
            color = TmTheme.colors.textMuted
        )
        TmButton(text = "Save settings", enabled = draft != loaded && problem == null && !isBusy, onClick = { onSave(draft) })
    }
    if (draft != loaded) problem?.let { TmNotice(text = it, kind = TmNoticeKind.WARNING) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        EmailPanel(draft) { draft = it }
        SmsPanel(draft) { draft = it }
        WhenPanel(draft, statuses) { draft = it }
        WordingPanel(draft) { draft = it }
        TestPanel(draft, isBusy, onTest)
    }
}

@Composable
private fun EmailPanel(draft: NotificationSettings, onChange: (NotificationSettings) -> Unit) {
    val smtp = draft.smtp
    fun change(new: SmtpSettings) = onChange(draft.copy(smtp = new))

    TmPanel(
        modifier = Modifier.widthIn(max = PANEL_WIDTH).fillMaxWidth(),
        title = "Email",
        subtitle = "Through the shop's own mail server",
        icon = Res.drawable.mail,
        actions = { TmSwitch(checked = draft.emailEnabled, onCheckedChange = { onChange(draft.copy(emailEnabled = it)) }, contentDescription = "Send email") }
    ) {
        TmFieldPair(
            { TmTextField(modifier = it, value = smtp.host, onValueChange = { v -> change(smtp.copy(host = v)) }, label = "Mail server", placeholder = "smtp.example.com") },
            {
                TmTextField(
                    modifier = it,
                    value = smtp.port.toString(),
                    onValueChange = { v -> v.filter { c -> c.isDigit() }.take(5).toIntOrNull()?.let { port -> change(smtp.copy(port = port)) } },
                    label = "Port",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        )
        TmSegmented(
            label = "Security",
            options = SmtpSecurity.entries,
            selected = smtp.security,
            // The usual port for each, unless one was typed that isn't one of those
            onSelect = { security ->
                val usual = smtp.port in SmtpSecurity.entries.map { it.defaultPort }
                change(smtp.copy(security = security, port = if (usual) security.defaultPort else smtp.port))
            },
            optionLabel = { it.label }
        )
        TmFieldPair(
            { TmTextField(modifier = it, value = smtp.username, onValueChange = { v -> change(smtp.copy(username = v)) }, label = "Username") },
            { SecretField(it, smtp.password, draft.smtpPasswordSet, "Password") { v -> change(smtp.copy(password = v)) } }
        )
        TmFieldPair(
            { TmTextField(modifier = it, value = smtp.fromAddress, onValueChange = { v -> change(smtp.copy(fromAddress = v.trim())) }, label = "From address", placeholder = "repairs@example.com") },
            { TmTextField(modifier = it, value = smtp.fromName, onValueChange = { v -> change(smtp.copy(fromName = v)) }, label = "From name", placeholder = "Fix It Shop") }
        )
    }
}

@Composable
private fun SmsPanel(draft: NotificationSettings, onChange: (NotificationSettings) -> Unit) {
    val sms = draft.sms
    fun change(new: SmsSettings) = onChange(draft.copy(sms = new))

    TmPanel(
        modifier = Modifier.widthIn(max = PANEL_WIDTH).fillMaxWidth(),
        title = "Text messages",
        subtitle = "Through the shop's ${sms.provider.label} account",
        icon = Res.drawable.message_square,
        actions = { TmSwitch(checked = draft.smsEnabled, onCheckedChange = { onChange(draft.copy(smsEnabled = it)) }, contentDescription = "Send texts") }
    ) {
        TmFieldPair(
            { TmTextField(modifier = it, value = sms.accountSid, onValueChange = { v -> change(sms.copy(accountSid = v.trim())) }, label = "Account SID") },
            { SecretField(it, sms.authToken, draft.smsTokenSet, "Auth token") { v -> change(sms.copy(authToken = v)) } }
        )
        TmFieldPair(
            {
                TmTextField(
                    modifier = it,
                    value = sms.fromNumber,
                    onValueChange = { v -> change(sms.copy(fromNumber = v.trim())) },
                    label = "From number",
                    placeholder = "+15551234567",
                    textStyle = TmTheme.typography.numeric
                )
            },
            {
                TmTextField(
                    modifier = it,
                    value = sms.countryCode,
                    onValueChange = { v -> change(sms.copy(countryCode = v.filter { c -> c.isDigit() }.take(3))) },
                    label = "Country code",
                    supportingText = "For numbers saved without one",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        )
    }
}

/**
 * A password or token that never comes back from the server. Left empty it keeps the saved one; typing replaces it
 */
@Composable
internal fun SecretField(modifier: Modifier, value: String?, saved: Boolean, label: String, onChange: (String?) -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
        TmPasswordField(modifier = Modifier.fillMaxWidth(), value = value.orEmpty(), onValueChange = { onChange(it.ifEmpty { null }) }, label = label)
        Text(
            text = if (saved) "Saved. Leave empty to keep it" else "None saved",
            style = TmTheme.typography.caption,
            color = TmTheme.colors.textMuted
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenPanel(draft: NotificationSettings, statuses: List<CustomStatus>, onChange: (NotificationSettings) -> Unit) {
    val spacing = TmTheme.spacing

    TmPanel(modifier = Modifier.widthIn(max = PANEL_WIDTH).fillMaxWidth(), title = "When to send", icon = Res.drawable.bell) {
        NotificationEvent.automatic.forEach { event ->
            TmCheckbox(
                checked = event in draft.events,
                onCheckedChange = { on -> onChange(draft.copy(events = if (on) draft.events + event else draft.events - event)) },
                label = event.label
            )
        }

        if (NotificationEvent.STATUS_CHANGED in draft.events) {
            Text(text = "Statuses that send status changed", style = TmTheme.typography.label, color = TmTheme.colors.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                statuses.selectable().forEach { choice ->
                    val on = choice in draft.statusChangeStatuses
                    TmFilterChip(
                        text = statuses.label(choice),
                        selected = on,
                        onClick = { onChange(draft.copy(statusChangeStatuses = if (on) draft.statusChangeStatuses - choice else draft.statusChangeStatuses + choice)) }
                    )
                }
            }
        }

        if (NotificationEvent.PICKUP_REMINDER in draft.events) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                DaysField(Modifier.weight(1f), draft.reminderAfterDays, "First after, days") { onChange(draft.copy(reminderAfterDays = it)) }
                DaysField(Modifier.weight(1f), draft.reminderEveryDays, "Then every, days") { onChange(draft.copy(reminderEveryDays = it)) }
                DaysField(Modifier.weight(1f), draft.reminderLimit, "At most") { onChange(draft.copy(reminderLimit = it)) }
            }
        }
    }
}

@Composable
private fun DaysField(modifier: Modifier, value: Int, label: String, onChange: (Int) -> Unit) {
    TmTextField(
        modifier = modifier,
        value = value.toString(),
        onValueChange = { text -> onChange(text.filter { it.isDigit() }.take(3).toIntOrNull() ?: 0) },
        label = label,
        textStyle = TmTheme.typography.numeric,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun WordingPanel(draft: NotificationSettings, onChange: (NotificationSettings) -> Unit) {
    var event by remember { mutableStateOf(NotificationEvent.READY_FOR_PICKUP) }
    val template = draft.templates[event] ?: MessageTemplates.defaults.getValue(event)
    val custom = event in draft.templates
    fun change(new: MessageTemplate) = onChange(draft.copy(templates = draft.templates + (event to new)))

    TmPanel(
        modifier = Modifier.widthIn(max = PANEL_WIDTH).fillMaxWidth(),
        title = "Wording",
        subtitle = "Texts use the message only. Keep them short, long texts cost more",
        icon = Res.drawable.edit_3
    ) {
        TmDropdown(
            modifier = Modifier.fillMaxWidth(),
            label = "Message",
            options = NotificationEvent.entries,
            selected = event,
            onSelect = { event = it },
            optionLabel = { it.label }
        )
        TmTextField(modifier = Modifier.fillMaxWidth(), value = template.subject, onValueChange = { change(template.copy(subject = it)) }, label = "Email subject")
        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = template.body,
            onValueChange = { change(template.copy(body = it)) },
            label = "Message",
            singleLine = false,
            minLines = 4,
            supportingText = "${template.body.length} characters"
        )
        Text(
            text = MessageTemplates.FIELDS.joinToString("   ") { (field, what) -> "{$field} $what" },
            style = TmTheme.typography.caption,
            color = TmTheme.colors.textMuted
        )
        if (custom) {
            TmButton(
                text = "Back to the default",
                variant = TmButtonVariant.GHOST,
                size = TmControlSize.SMALL,
                onClick = { onChange(draft.copy(templates = draft.templates - event)) }
            )
        }
    }
}

@Composable
private fun TestPanel(draft: NotificationSettings, isBusy: Boolean, onTest: (NotificationSettings, NotificationChannel, String) -> Unit) {
    var channel by remember { mutableStateOf(NotificationChannel.EMAIL) }
    var recipient by remember { mutableStateOf("") }

    TmPanel(
        modifier = Modifier.widthIn(max = PANEL_WIDTH).fillMaxWidth(),
        title = "Try it",
        subtitle = "Sends a test now with the settings above, saved or not",
        icon = Res.drawable.send
    ) {
        TmSegmented(options = NotificationChannel.entries, selected = channel, onSelect = { channel = it }, optionLabel = { it.label })
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm), verticalAlignment = Alignment.Bottom) {
            TmTextField(
                modifier = Modifier.weight(1f),
                value = recipient,
                onValueChange = { recipient = it },
                label = if (channel == NotificationChannel.EMAIL) "Send to address" else "Send to number"
            )
            TmButton(text = "Send test", enabled = recipient.isNotBlank() && !isBusy, variant = TmButtonVariant.SECONDARY, onClick = { onTest(draft, channel, recipient) })
        }
    }
}

@Composable
private fun OutboxPanel(
    outbox: List<OutboxMessage>,
    outboxEnd: Boolean,
    onRetry: (OutboxMessage) -> Unit,
    onCancel: (OutboxMessage) -> Unit,
    onLoadMore: () -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    TmPanel(modifier = Modifier.fillMaxWidth(), title = "Sent messages", subtitle = "Newest first. Ones that didn't go are tried again for a few hours", icon = Res.drawable.inbox) {
        if (outbox.isEmpty()) {
            TmEmptyState(title = "No messages yet", message = "They show here as they're sent")
            return@TmPanel
        }

        outbox.forEachIndexed { index, message ->
            if (index > 0) TmDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Text(
                        text = listOfNotNull(message.ticketLabel.ifBlank { null }, message.event.label, message.channel.label, message.recipient).joinToString(", "),
                        style = TmTheme.typography.label,
                        color = colors.text
                    )
                    Text(text = message.body, style = TmTheme.typography.body, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = listOfNotNull(
                            formatDateTime(message.createdAt),
                            message.createdBy.ifBlank { null }?.let { "by $it" },
                            message.lastError.ifBlank { null }
                        ).joinToString(", "),
                        style = TmTheme.typography.caption,
                        color = if (message.status == OutboxStatus.FAILED) colors.danger.color else colors.textMuted
                    )
                }

                TmBadge(text = message.status.label, tone = colors.tone(message.status), dot = false)

                when (message.status) {
                    OutboxStatus.PENDING -> TmButton(text = "Cancel", variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL, onClick = { onCancel(message) })
                    OutboxStatus.FAILED, OutboxStatus.CANCELLED -> TmButton(text = "Retry", variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL, onClick = { onRetry(message) })
                    OutboxStatus.SENT -> Unit
                }
            }
        }

        if (!outboxEnd) TmButton(text = "Load more", variant = TmButtonVariant.GHOST, onClick = onLoadMore)
    }
}
