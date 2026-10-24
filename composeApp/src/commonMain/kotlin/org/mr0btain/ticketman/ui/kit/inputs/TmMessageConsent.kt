package org.mr0btain.ticketman.ui.kit.inputs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.ui.kit.selection.TmCheckbox
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Whether a customer agreed to hear about their repairs by email and by text. Each box only works once there's an
 * address or number to send to
 */
@Composable
fun TmMessageConsent(
    email: Boolean,
    sms: Boolean,
    onEmailChange: (Boolean) -> Unit,
    onSmsChange: (Boolean) -> Unit,
    hasEmail: Boolean,
    hasPhone: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
        Text(text = "Updates about their repairs", style = TmTheme.typography.label, color = TmTheme.colors.text)
        TmCheckbox(checked = email && hasEmail, onCheckedChange = onEmailChange, label = "By email", enabled = hasEmail)
        TmCheckbox(checked = sms && hasPhone, onCheckedChange = onSmsChange, label = "By text message", enabled = hasPhone)
        Text(
            text = "Only tick what they agreed to. Messages the shop sends by hand still go",
            style = TmTheme.typography.caption,
            color = TmTheme.colors.textMuted
        )
    }
}
