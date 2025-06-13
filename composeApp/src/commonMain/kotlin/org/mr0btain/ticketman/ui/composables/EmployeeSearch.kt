package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirstOrNull
import org.mr0btain.ticketman.components.data.components.employee.Employee

/**
 * SearchBox for searching emplyees. Will return an id or a name and a true if they want to add new if it doesn't exist
 * @param modifier Modifier applied to drop down
 * @param items List of all customer info
 * @param input The state of the text field
 * @param onInputChange Event called when typing
 * @param onClick Event called when clicking, string passed is identifier for employee
 */

// TODO: Might be able to combine this with customer search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeSearch(
    modifier: Modifier = Modifier,
    items: List<Employee>,
    input: String,
    onInputChange: (String) -> Unit,
    onClick: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredList = remember(input) { items.filter {
        (it.name.firstName + it.name.lastName).lowercase()
            .contains(input.lowercase().replace(" ", ""))
                || it.identifier.toString().contains(input)
    }.sortedBy { it.name.firstName + it.name.lastName + it.identifier.toString() } }

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
            prefix = {
                // Get employee if we've matched with one
                val employee = remember(input) { items.fastFirstOrNull { it.identifier.toString() == input } }

                // If a match then prefix the name
                if (employee != null) { Text(text = employee.name.firstName + " " + employee.name.lastName + ": ") }
            },
            label = { Text("Search Employees") },
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
            filteredList.forEach { item ->
                DropdownMenuItem(
                    text = { Text(
                        text = item.name.firstName + " " + item.name.lastName + ": " + item.identifier.toString(),
                        fontSize = 16.sp
                    )
                    },
                    onClick = {
                        // minimize the drop-down
                        expanded = false

                        // Call on click
                        onClick(item.identifier)
                    }
                )
            }
        }
    }
}