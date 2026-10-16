package org.mr0btain.ticketman.ui.composables.ticket

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketPriorityType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketPriorityDropDown(
    modifier: Modifier = Modifier,
    priority: TicketPriority,
    onClick: (TicketPriority) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            readOnly = true,
            value = priority.priority.name,
            onValueChange = {},
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            TicketPriorityType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(
                        text = option.name,
                        fontSize = 16.sp
                    ) },
                    onClick = {
                        // Call onClick
                        onClick(TicketPriority(option))

                        // Close menu
                        expanded = false
                    }
                )
            }
        }
    }
}