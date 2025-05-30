package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.components.data.components.SubItemType
import org.mr0btain.ticketman.components.data.components.getLabelTextForType

/**
 * Dialog for adding a sub item to a main item like a Ticket or Customer
 * @param modifier Modifier
 * @param isDarkMode Whether dark mode is on or off
 * @param subItemType The type of item were looking to add
 * @param onConfirm What to do when we've confirmed we want to add the item
 * @param onDismissRequest What to do when we want to close the dialog
 */

@Composable
fun AddSubItemDialog(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    subItemType: SubItemType,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = { onDismissRequest() },
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 4.dp,
            color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Add new ${getLabelTextForType(subItemType)}:")

                Spacer(Modifier.padding(4.dp))

                OutlinedTextField(
                    label = { Text(getLabelTextForType(subItemType)) },
                    value = input,
                    onValueChange = { input = it }
                )

                Spacer(Modifier.padding(4.dp))

                Button(
                    onClick = { onConfirm(input) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}
