package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.print.PrintTemplate
import org.mr0btain.ticketman.data.shop.PaperSize

/**
 * Print button that asks which slip and which paper, starting on the shop's default paper
 * @param templates The slips that make sense here, the first is picked to start with
 * @param onPrint Prints it, calls back with what went wrong or null if it went to the printer
 */
@Composable
fun PrintButton(
    label: String = "Print",
    templates: List<PrintTemplate>,
    defaultPaper: PaperSize,
    onPrint: (PrintTemplate, PaperSize, (String?) -> Unit) -> Unit
) {
    var open by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { open = true }) { Text(label) }

    if (!open) return

    var template by remember { mutableStateOf(templates.first()) }
    var paper by remember { mutableStateOf(defaultPaper) }
    var printing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Print") },
        text = {
            Column {
                if (templates.size > 1) {
                    Text("What to print")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        templates.forEach { each ->
                            FilterChip(selected = template == each, onClick = { template = each }, label = { Text(each.label) })
                        }
                    }
                }

                Text(modifier = Modifier.padding(top = 8.dp), text = "Paper")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaperSize.entries.forEach { each ->
                        FilterChip(selected = paper == each, onClick = { paper = each }, label = { Text(each.label) })
                    }
                }

                error?.let { Text(modifier = Modifier.padding(top = 8.dp), text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled = !printing,
                onClick = {
                    printing = true
                    error = null
                    onPrint(template, paper) { problem ->
                        printing = false
                        error = problem
                        if (problem == null) open = false
                    }
                }
            ) { Text("Print") }
        },
        dismissButton = { OutlinedButton(onClick = { open = false }) { Text("Cancel") } }
    )
}
