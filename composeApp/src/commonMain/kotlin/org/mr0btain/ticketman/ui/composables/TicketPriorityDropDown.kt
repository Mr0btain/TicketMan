package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.components.data.components.TicketPriority

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketPriorityDropDown(
    modifier: Modifier = Modifier,
    onClick: (TicketPriority) -> Unit
) {
    var status by remember { mutableStateOf(TicketPriority.LOW) }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            readOnly = true,
            value = status.name,
            onValueChange = {},
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TicketPriority.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.name) },
                    onClick = {
                        expanded = false
                        status = option
                        onClick(option)
                    }
                )
            }
        }
    }
}