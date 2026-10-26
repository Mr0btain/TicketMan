package org.mr0btain.ticketman.ui.screens.admin.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import org.mr0btain.ticketman.data.appointment.OnlineBooking
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmCheckbox
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Customers booking a visit from the status page: how long a visit is, how far ahead and how soon, and a note.
 * The times come from the shifts on the staff schedule
 * @param publicUrl The shop's public address, which the booking page is under
 */
@Composable
fun OnlineBookingEditor(booking: OnlineBooking, publicUrl: String, onChange: (OnlineBooking) -> Unit) {
    val spacing = TmTheme.spacing

    fun number(text: String, max: Int): Int = text.filter { it.isDigit() }.take(5).toIntOrNull()?.coerceAtMost(max) ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "Booking online", style = TmTheme.typography.label, color = TmTheme.colors.textMuted)
        TmCheckbox(
            checked = booking.enabled,
            onCheckedChange = { onChange(booking.copy(enabled = it)) },
            label = "Customers can book a visit from the status page, into the times staff are scheduled"
        )

        if (booking.enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                TmTextField(
                    modifier = Modifier.weight(1f),
                    value = booking.minutes.takeIf { it > 0 }?.toString().orEmpty(),
                    onValueChange = { onChange(booking.copy(minutes = number(it, 999))) },
                    label = "Each visit",
                    supportingText = "Minutes",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                TmTextField(
                    modifier = Modifier.weight(1f),
                    value = booking.daysAhead.takeIf { it > 0 }?.toString().orEmpty(),
                    onValueChange = { onChange(booking.copy(daysAhead = number(it, 999))) },
                    label = "Days ahead",
                    supportingText = "Today included",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                TmTextField(
                    modifier = Modifier.weight(1f),
                    value = (booking.leadMinutes / 60).toString(),
                    onValueChange = { onChange(booking.copy(leadMinutes = number(it, 999) * 60)) },
                    label = "Not sooner than",
                    supportingText = "Hours from now",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            TmTextField(
                modifier = Modifier.fillMaxWidth(),
                value = booking.note,
                onValueChange = { onChange(booking.copy(note = it)) },
                label = "Note on the booking page",
                placeholder = "Bring your charger and passcode"
            )

            booking.problem()?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
            Text(
                text = if (publicUrl.isBlank()) "Set the public address below so customers can reach the booking page."
                    else "Customers book at ${publicUrl.trim().trimEnd('/')}/status/book, also linked from the status lookup.",
                style = TmTheme.typography.caption,
                color = TmTheme.colors.textMuted
            )
        }
    }
}
