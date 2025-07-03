package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.InventoryItem

/**
 * SearchBox for searching inventory. Will return an id
 * @param modifier Modifier applied to drop down
 * @param items List of all inventory items
 * @param input The state of the text field
 * @param onInputChange Event called when typing
 * @param onClick Event called when clicking, identifier is returned for item
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventorySearch(
    modifier: Modifier = Modifier,
    items: List<InventoryItem>,
    input: String,
    onInputChange: (String) -> Unit,
    onClick: (InventoryItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredList = remember(input) { items.filter { "${it.name}${it.identifier}".lowercase().contains(input.lowercase()) } }

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
            label = { Text("Search Inventory") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(),
            singleLine = true,
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
                        text = "${option.name}: ${option.identifier}",
                        fontSize = 16.sp
                    ) },
                    onClick = {
                        // minimize the drop-down
                        expanded = false

                        // Call on click
                        onClick(option)
                    }
                )
            }
        }
    }
}