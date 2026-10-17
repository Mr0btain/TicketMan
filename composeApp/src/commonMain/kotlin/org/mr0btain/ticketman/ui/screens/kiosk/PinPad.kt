package org.mr0btain.ticketman.ui.screens.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.user.PinRules

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
                Box(
                    Modifier
                        .size(20.dp)
                        .background(
                            if (index < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                )
            }
        }

        Text(
            modifier = Modifier.padding(vertical = 12.dp).heightIn(min = 20.dp),
            text = error.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            fontSize = 15.sp
        )

        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit -> PadButton(digit.toString(), enabled = !busy) { type(digit) } }
            }
            Spacer(Modifier.padding(6.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadButton("Clear", enabled = !busy) { pin = "" }
            PadButton("0", enabled = !busy) { type('0') }
            PadButton("Back", enabled = !busy) { pin = pin.dropLast(1) }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun PadButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        modifier = Modifier.size(width = 96.dp, height = 72.dp),
        enabled = enabled,
        onClick = onClick
    ) {
        Text(text = label, fontSize = if (label.length == 1) 28.sp else 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
