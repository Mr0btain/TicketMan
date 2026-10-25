package org.mr0btain.ticketman.ui.screens.admin.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import org.mr0btain.ticketman.data.shop.Storage
import org.mr0btain.ticketman.data.shop.StorageTerms
import org.mr0btain.ticketman.ui.kit.data.LocalCurrencySymbol
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * What happens to devices left after they're ready: a daily storage fee after some days, and abandoned after more.
 * The terms are printed on the claim slip
 */
@Composable
fun StorageTermsEditor(terms: StorageTerms, onChange: (StorageTerms) -> Unit) {
    val spacing = TmTheme.spacing

    fun days(text: String): Int = text.filter { it.isDigit() }.take(4).toIntOrNull()?.coerceAtMost(StorageTerms.MAX_DAYS) ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "Devices left after they're ready", style = TmTheme.typography.label, color = TmTheme.colors.textMuted)

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            TmTextField(
                modifier = Modifier.weight(1f),
                value = terms.feeAfterDays.takeIf { it > 0 }?.toString().orEmpty(),
                onValueChange = { onChange(terms.copy(feeAfterDays = days(it))) },
                label = "Storage fee after",
                placeholder = "Never",
                supportingText = "Days",
                textStyle = TmTheme.typography.numeric,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            TmMoneyField(
                modifier = Modifier.weight(1f),
                label = "Fee a day",
                cents = terms.dailyFeeCents,
                onCentsChange = { onChange(terms.copy(dailyFeeCents = it)) }
            )
            TmTextField(
                modifier = Modifier.weight(1f),
                value = terms.abandonAfterDays.takeIf { it > 0 }?.toString().orEmpty(),
                onValueChange = { onChange(terms.copy(abandonAfterDays = days(it))) },
                label = "Abandoned after",
                placeholder = "Never",
                supportingText = "Days",
                textStyle = TmTheme.typography.numeric,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        terms.problem()?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
        Storage.termsText(terms, LocalCurrencySymbol.current)?.let {
            Text(text = "On the claim slip: $it", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
        }
    }
}
