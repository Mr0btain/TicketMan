package org.mr0btain.ticketman.ui.screens.kiosk

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.ticketman.ui.kit.buttons.TM_DISABLED_ALPHA
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Big buttons for typing a PIN on a touchscreen, the number keys work too. Sends it once all the digits are in
 * @param error Shown under the dots, like a wrong PIN
 * @param busy Ignores input while a PIN is being checked
 */
@Composable
fun PinPad(
    error: String?,
    busy: Boolean,
    onSubmit: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    fun type(digit: Char) {
        if (busy || pin.length >= PinRules.LENGTH) return
        pin += digit

        if (pin.length == PinRules.LENGTH) {
            onSubmit(pin)
            pin = ""
        }
    }

    Column(
        modifier = Modifier
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                val digit = event.utf16CodePoint.toChar()
                when {
                    digit.isDigit() -> { type(digit); true }
                    event.key == Key.Backspace -> { pin = pin.dropLast(1); true }
                    else -> false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // One dot per digit
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(PinRules.LENGTH) { index ->
                val filled by animateColorAsState(if (index < pin.length) colors.accent.color else colors.surfaceSunken)
                Box(
                    Modifier
                        .size(18.dp)
                        .background(filled, TmTheme.shapes.pill)
                        .border(1.dp, if (index < pin.length) filled else colors.outline, TmTheme.shapes.pill)
                )
            }
        }

        Text(
            modifier = Modifier.padding(vertical = spacing.md).heightIn(min = 20.dp),
            text = error.orEmpty(),
            style = TmTheme.typography.body,
            color = colors.danger.color
        )

        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    row.forEach { digit -> PadKey(digit.toString(), enabled = !busy) { type(digit) } }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                PadKey("Clear", enabled = !busy, quiet = true) { pin = "" }
                PadKey("0", enabled = !busy) { type('0') }
                PadKey("Back", enabled = !busy, quiet = true) { pin = pin.dropLast(1) }
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** One key of the pad, digits are raised tiles and [quiet] keys are plain text */
@Composable
private fun PadKey(label: String, enabled: Boolean, quiet: Boolean = false, onClick: () -> Unit) {
    val colors = TmTheme.colors
    val shape = TmTheme.shapes.panel

    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 72.dp)
            .alpha(if (enabled) 1f else TM_DISABLED_ALPHA)
            .then(
                if (quiet) Modifier
                else Modifier.background(colors.surfaceRaised, shape).border(1.dp, colors.hairline, shape)
            )
            .tmClickable(onClick = onClick, shape = shape, enabled = enabled),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = if (quiet) TmTheme.typography.label else TmTheme.typography.numericDisplay,
            color = if (quiet) colors.textMuted else colors.text
        )
    }
}
