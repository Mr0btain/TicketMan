package org.mr0btain.ticketman.ui.screens.admin.locations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.invoice.TaxClass
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmSwitch
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.map_pin
import ticketman.composeapp.generated.resources.plus

/**
 * The shop's locations: each keeps its own tickets, stock counts, stations and staff, numbers its tickets with its
 * code, and can have its own address, contacts and tax rates. Customers and the catalog are shared
 * @param taxClasses The shop's tax classes, which a location can charge at its own rates
 * @param onSave Saves one, new or changed, calling back with what went wrong or null
 */
@Composable
fun AdminLocationsTab(
    locations: List<Location>,
    taxClasses: List<TaxClass>,
    onSave: (Location, Boolean, (String?) -> Unit) -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    var editing by remember { mutableStateOf<Pair<Location, Boolean>?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmPanel(
            modifier = Modifier.widthIn(max = 900.dp),
            title = "Locations",
            subtitle = "Each location keeps its own tickets, stock, stations and staff. Customers and items are shared, so a customer's tickets show at every one.",
            icon = Res.drawable.map_pin,
            actions = { TmButton(text = "Add location", leadingIcon = Res.drawable.plus, size = TmControlSize.SMALL, onClick = { editing = Location() to true }) }
        ) {
            locations.forEachIndexed { index, location ->
                if (index > 0) TmDivider()
                TmListRow(
                    title = location.name,
                    subtitle = listOfNotNull(
                        if (location.code.isBlank()) "Numbers like 26-00000042" else "Numbers like ${location.code}-26-00000042",
                        listOf(location.street, location.city).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                        location.taxRate?.let { "Tax $it%" }
                    ).joinToString(" · "),
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            if (location.main) TmBadge(text = "Main", tone = colors.accent, dot = false)
                            if (location.retired) TmBadge(text = "Retired", tone = colors.neutral, dot = false)
                            TmButton(text = "Edit", variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL, onClick = { editing = location to false })
                        }
                    }
                )
            }

            if (locations.size == 1) {
                Text(
                    text = "With one location nothing about locations shows anywhere else. Add another to give each its own counter.",
                    style = TmTheme.typography.caption,
                    color = colors.textMuted
                )
            }
        }
    }

    editing?.let { (location, isNew) ->
        LocationDialog(
            location = location,
            isNew = isNew,
            others = locations.filter { it.identifier != location.identifier },
            taxClasses = taxClasses,
            onSave = { saved, onResult -> onSave(saved, isNew) { problem -> onResult(problem); if (problem == null) editing = null } },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun LocationDialog(
    location: Location,
    isNew: Boolean,
    others: List<Location>,
    taxClasses: List<TaxClass>,
    onSave: (Location, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val spacing = TmTheme.spacing
    var draft by remember(location) { mutableStateOf(location) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val invalid = draft.copy(code = draft.code.trim().uppercase(), name = draft.name.trim()).problem(others)

    TmDialog(
        title = if (isNew) "Add a location" else "Edit ${location.name}",
        description = "Blank address, contact and tax fields use the shop profile's",
        onDismissRequest = onDismiss,
        maxWidth = 560.dp,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                TmButton(text = "Cancel", variant = TmButtonVariant.SECONDARY, onClick = onDismiss)
                TmButton(
                    text = if (isNew) "Add" else "Save",
                    enabled = invalid == null && draft != location,
                    loading = busy,
                    onClick = {
                        busy = true
                        onSave(draft) { failed ->
                            busy = false
                            problem = failed
                        }
                    }
                )
            }
        }
    ) {
        TmFieldPair(
            { TmTextField(modifier = it, value = draft.name, onValueChange = { v -> draft = draft.copy(name = v) }, label = "Name", placeholder = "Downtown") },
            {
                TmTextField(
                    modifier = it,
                    value = draft.code,
                    onValueChange = { v -> draft = draft.copy(code = v.filter { c -> c.isLetterOrDigit() }.take(4).uppercase()) },
                    label = "Code",
                    placeholder = if (location.main) "None" else "DT",
                    supportingText = if (draft.code.isBlank()) "Numbers like 26-00000042" else "Numbers like ${draft.code}-26-00000042",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                )
            }
        )
        if (!isNew && draft.code != location.code) {
            TmNotice(text = "Numbers handed out from now on use the new code; ones already given keep theirs.", kind = TmNoticeKind.INFO)
        }

        TmTextField(value = draft.street, onValueChange = { draft = draft.copy(street = it) }, label = "Street")
        TmFieldPair(
            { TmTextField(modifier = it, value = draft.city, onValueChange = { v -> draft = draft.copy(city = v) }, label = "City") },
            {
                TmFieldPair(
                    { m -> TmTextField(modifier = m, value = draft.state, onValueChange = { v -> draft = draft.copy(state = v) }, label = "State") },
                    { m -> TmTextField(modifier = m, value = draft.zip, onValueChange = { v -> draft = draft.copy(zip = v) }, label = "Zip") }
                )
            }
        )
        TmFieldPair(
            { TmTextField(modifier = it, value = draft.phone, onValueChange = { v -> draft = draft.copy(phone = v) }, label = "Phone", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)) },
            { TmTextField(modifier = it, value = draft.email, onValueChange = { v -> draft = draft.copy(email = v.trim()) }, label = "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)) }
        )

        TmTextField(
            modifier = Modifier.width(200.dp),
            value = draft.taxRate.orEmpty(),
            onValueChange = { draft = draft.copy(taxRate = it.ifBlank { null }) },
            label = "Sales tax %",
            placeholder = "Shop's rate",
            textStyle = TmTheme.typography.numeric,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        taxClasses.forEach { taxClass ->
            TmTextField(
                modifier = Modifier.width(200.dp),
                value = draft.taxClassRates[taxClass.identifier].orEmpty(),
                onValueChange = { v ->
                    draft = draft.copy(taxClassRates = if (v.isBlank()) draft.taxClassRates - taxClass.identifier else draft.taxClassRates + (taxClass.identifier to v))
                },
                label = "${taxClass.name} %",
                placeholder = "${taxClass.rate}, the shop's",
                textStyle = TmTheme.typography.numeric,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        if (!location.main && !isNew) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                TmSwitch(checked = draft.retired, onCheckedChange = { draft = draft.copy(retired = it) }, contentDescription = "Retired")
                Text(
                    text = if (draft.retired) "Retired: not offered for new work, its records are kept" else "In use",
                    style = TmTheme.typography.body,
                    color = TmTheme.colors.text
                )
            }
        }

        (problem ?: invalid?.takeIf { draft != location && draft.name.isNotBlank() })?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
    }
}
