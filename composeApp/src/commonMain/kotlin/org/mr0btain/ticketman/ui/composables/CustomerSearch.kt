package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirstOrNull
import org.mr0btain.ticketman.components.data.components.CustomerInfo

/**
 * SearchBox for searching customers. Will return an id or a name and a true if they want to add new if it doesn't exist
 * @param modifier Modifier applied to drop down
 * @param items List of all customer info
 * @param input The state of the text field
 * @param onInputChange Event called when typing
 * @param onClick Event called when clicking, string passed is identifier for customer
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerSearch(
    modifier: Modifier = Modifier,
    items: List<CustomerInfo>,
    input: String,
    onInputChange: (String) -> Unit,
    onClick: (String) -> Unit,
    focusManager: FocusManager
) {
    var expanded by remember { mutableStateOf(false) }

    val filteredList = remember(input) { items.filter {
        (it.name.firstName + it.name.lastName).lowercase()
            .contains(input.lowercase().replace(" ", ""))
                || it.identifier.contains(input)
    }.sortedBy { it.name.firstName + it.name.lastName + it.identifier } }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.sizeIn(maxWidth = 400.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                // Check if were expanded on typing, because we should be
                if (!expanded) expanded = true

                // Call on input change
                onInputChange(it)
            },
            label = { Text("Search Customer") },
            prefix = {
                // Get customer if we've matched with one
                val customer = remember(input) { items.fastFirstOrNull { it.identifier == input } }

                // If a match then prefix the name
                if (customer != null) { Text(text = customer.name.firstName + " " + customer.name.lastName + ": ") }
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
            filteredList.forEach { item ->
                DropdownMenuItem(
                    text = { Text(
                        text = item.name.firstName + " " + item.name.lastName + ": " + item.identifier,
                        fontSize = 16.sp
                    )
                           },
                    onClick = {
                        // minimize the drop-down
                        expanded = false

                        // Clear focus
                        focusManager.clearFocus()

                        // Call on click
                        onClick(item.identifier)
                    }
                )
            }
        }
    }
}