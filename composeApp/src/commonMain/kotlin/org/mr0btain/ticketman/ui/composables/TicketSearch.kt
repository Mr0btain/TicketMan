package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SearchBox for searching tickets. Will return an id or a name and a true if they want to add new if it doesn't exist
 * @param modifier Modifier applied to drop down
 * @param items List of all tickets
 * @param input The state of the text field
 * @param onInputChange Event called when typing
 * @param onClick Event called when clicking, string passed is identifier for ticket
 */

// TODO: Possibly add some extra details into prefix like customer name?

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketSearch(
    modifier: Modifier = Modifier,
    items: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onClick: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredList = remember(input) { items.filter { it.lowercase().contains(input.lowercase()) } }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.sizeIn(maxWidth = 300.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                // Check if were expanded on typing, because we should be
                if (!expanded) expanded = true

                // Call on input change
                onInputChange(it)
                            },
            label = { Text("Search Tickets") },
            prefix = {
                // Get matching ticket if there is one
                val ticket = remember(input) { items.filter { it == input } }

                // Show prefix
                if (ticket.isNotEmpty()) { Text("Ticket:") }
                     },
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
            filteredList.forEach { option ->
                DropdownMenuItem(
                    text = { Text(
                        text = "Ticket: $option",
                        fontSize = 16.sp
                    ) },
                    onClick = {
                        // minimize the drop-down
                        expanded = false

                        // Call on click
                        onClick(option.toInt())
                    }
                )
            }
        }
    }
}