package org.mr0btain.ticketman.ui.kit.inputs

import org.mr0btain.uikit.inputs.TmTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.ui.kit.data.LocalCurrencySymbol
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.uikit.foundation.TmControlSize
import org.mr0btain.uikit.theme.TmTheme

/** Most digits a money field takes, $9,999,999,999.99 */
private const val MAX_MONEY_DIGITS = 12

/**
 * Money in cents, typed like a till: digits fill in from the right, so typing 1 2 3 4 gives 12.34
 */
@Composable
fun TmMoneyField(
    cents: Long,
    onCentsChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    supportingText: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    size: TmControlSize = TmControlSize.MEDIUM,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val symbol = LocalCurrencySymbol.current
    val text = Money.format(cents, symbol = "")

    TmTextField(
        // Cursor stays at the end since digits always go in from the right
        value = TextFieldValue(text, selection = TextRange(text.length)),
        onValueChange = { changed ->
            val digits = changed.text.filter { it.isDigit() }.trimStart('0').take(MAX_MONEY_DIGITS)
            onCentsChange(digits.toLongOrNull() ?: 0L)
        },
        modifier = modifier,
        label = label,
        supportingText = supportingText,
        error = error,
        enabled = enabled,
        readOnly = readOnly,
        size = size,
        prefix = symbol,
        textStyle = if (size == TmControlSize.LARGE) TmTheme.typography.numericLarge else TmTheme.typography.numeric,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        keyboardActions = keyboardActions
    )
}

/** Hidden, digits only field for a station PIN, stops at [PinRules.LENGTH] digits */
@Composable
fun TmPinField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = "PIN",
    error: String? = null,
    enabled: Boolean = true,
    size: TmControlSize = TmControlSize.MEDIUM,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    TmTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(PinRules.LENGTH)) },
        modifier = modifier,
        label = label,
        error = error,
        enabled = enabled,
        size = size,
        textStyle = TmTheme.typography.numericLarge,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = keyboardActions
    )
}
