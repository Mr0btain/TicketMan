package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.components.utils.CustomOutlinedTextField
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MoneyTextField(
    modifier: Modifier = Modifier,
    state: TextFieldState,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    label: String = ""
) {
    CustomOutlinedTextField(
        modifier = modifier,
        state = state,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        lineLimits = TextFieldLineLimits.SingleLine,
        label = { Text(label) },
        prefix = { Text("$") },
        inputTransformation = {
            // Check if we've only input a number, else revert the change
            if (this.asCharSequence().filter { it.isDigit() }.length != asCharSequence().length) {
                revertAllChanges()
            } else {
                val amountToDrop = length - asCharSequence().dropWhile { it == '0' }.length

                replace(0, amountToDrop, "")

                when (length) {
                    0 -> append("000")
                    1 -> insert(0, "00")
                    2 -> insert(0, "0")
                }
            }
        },
        outputTransformation = {
            // Insert our decimal for cents
            insert(max(length - 2, 0), ".")

            // Thi is used to calculate where we have to put a ","
            var leftOver = length - 3

            // For every three we pop off left over add a comma, essentially working backwards from the decimal
            while (leftOver > 3) {
                leftOver -= 3 // pop 3 off
                insert(leftOver, ",") // add ","
            }
        },
    )
}

