package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.components.data.components.TicketStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketStatusDropDown(
    modifier: Modifier = Modifier,
    onClick: (TicketStatus) -> Unit
) {
    var status by remember { mutableStateOf(TicketStatus.IN_PROGRESS) }
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
            TicketStatus.entries.forEach { option ->
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