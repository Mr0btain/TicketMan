package org.mr0btain.ticketman.ui.kit.inputs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.eye
import ticketman.composeapp.generated.resources.eye_off

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
    val text = Money.format(cents).removePrefix("$")

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
        prefix = "$",
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

/**
 * Password field with a show/hide button
 * @param showStrength Shows a meter under the field, [onStrengthChange] gets whether it's strong enough
 */
@Composable
fun TmPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = "Password",
    error: String? = null,
    enabled: Boolean = true,
    size: TmControlSize = TmControlSize.MEDIUM,
    showStrength: Boolean = false,
    onStrengthChange: (isStrong: Boolean) -> Unit = {},
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val strong = remember(value) { passwordStrength(value) == PasswordStrength.STRONG }

    if (showStrength) {
        LaunchedEffect(strong) { onStrengthChange(strong) }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
        TmTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            error = error,
            enabled = enabled,
            size = size,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = keyboardActions,
            trailing = {
                TmIconButton(
                    icon = if (visible) Res.drawable.eye_off else Res.drawable.eye,
                    contentDescription = if (visible) "Hide password" else "Show password",
                    onClick = { visible = !visible },
                    size = TmControlSize.SMALL
                )
            }
        )

        if (showStrength && value.isNotEmpty()) {
            PasswordStrengthMeter(strong)
        }
    }
}

@Composable
private fun PasswordStrengthMeter(strong: Boolean) {
    val colors = TmTheme.colors
    val tone = if (strong) colors.success else colors.danger

    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
            repeat(2) { index ->
                val filled = index == 0 || strong
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(if (filled) tone.color else colors.hairline, TmTheme.shapes.pill)
                )
            }
        }

        Text(
            text = if (strong) "Strong password"
            else "Use 8 or more characters with upper and lower case letters, a number and a symbol",
            style = TmTheme.typography.caption,
            color = tone.color
        )
    }
}
