package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.data.components.ticket.TicketStatus
import org.mr0btain.ticketman.components.data.components.ticket.TicketStatusType
import org.mr0btain.ticketman.components.data.components.shared.getTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketStatusDropDown(
    modifier: Modifier = Modifier,
    status: TicketStatus,
    onClick: (TicketStatus) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            readOnly = true,
            value = status.status.name,
            onValueChange = {},
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            TicketStatusType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(
                        text = option.name,
                        fontSize = 16.sp
                    ) },
                    onClick = {
                        // Call onClick
                        onClick(TicketStatus(option, getTime()))

                        // Close dialog
                        expanded = false
                    }
                )
            }
        }
    }
}