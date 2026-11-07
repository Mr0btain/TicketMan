package org.mr0btain.ticketman.ui.screens.admin.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.data.catalog.CatalogModel
import org.mr0btain.ticketman.data.catalog.RepairCatalog
import org.mr0btain.ticketman.data.catalog.RepairOffer
import org.mr0btain.ticketman.data.invoice.TaxClass
import org.mr0btain.ticketman.data.search.TmSearchResult
import org.mr0btain.ticketman.data.ticket.DeviceKind
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDeleteDialog
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.book_open
import ticketman.composeapp.generated.resources.package_icon
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.trash_2
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The repair menu: device models and the repairs offered on each at set prices. Check-in offers a model's repairs
 * when the device typed matches it, and the ones ticked go on the invoice
 * @param menu Null while loading or when it couldn't be read
 * @param taxClasses The shop's tax classes, which a repair can be charged at
 * @param onSave Saves a model, new or changed, calling back with what went wrong or null
 * @param onSearchParts Parts from inventory, for a repair that uses one
 * @param onPartNames Names of parts by id, for the repairs being edited
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun AdminCatalogTab(
    menu: List<CatalogModel>?,
    taxClasses: List<TaxClass>,
    onSave: (CatalogModel, Boolean, (String?) -> Unit) -> Unit,
    onRemove: (CatalogModel) -> Unit,
    onSearchParts: suspend (String) -> List<TmSearchResult>,
    onPartNames: suspend (Collection<Uuid>) -> Map<Uuid, String>
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    var editing by remember { mutableStateOf<Pair<CatalogModel, Boolean>?>(null) }
    var removing by remember { mutableStateOf<CatalogModel?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmPanel(
            modifier = Modifier.widthIn(max = 900.dp),
            title = "Repair menu",
            subtitle = "Models and the repairs you do on them at set prices. Check-in offers them for the device typed, and each one ticked goes on the invoice.",
            icon = Res.drawable.book_open,
            actions = { TmButton(text = "Add model", leadingIcon = Res.drawable.plus, size = TmControlSize.SMALL, onClick = { editing = CatalogModel() to true }) }
        ) {
            if (menu != null && menu.isEmpty()) {
                TmEmptyState(
                    title = "Nothing on the menu yet",
                    message = "Add the models you see most, like the phones you replace screens on, with what each repair costs",
                    icon = Res.drawable.book_open
                )
            }

            menu.orEmpty().forEachIndexed { index, model ->
                if (index > 0) TmDivider()
                TmListRow(
                    title = model.label,
                    subtitle = listOfNotNull(
                        model.kind.label,
                        when (model.repairs.size) {
                            0 -> "No repairs yet"
                            1 -> "1 repair"
                            else -> "${model.repairs.size} repairs"
                        },
                        model.repairs.minOfOrNull { it.priceCents }?.let { "from ${formatMoney(it)}" }
                    ).joinToString(" · "),
                    onClick = { editing = model to false },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            TmButton(text = "Edit", variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL, onClick = { editing = model to false })
                            TmIconButton(icon = Res.drawable.trash_2, contentDescription = "Remove ${model.label}", size = TmControlSize.SMALL, onClick = { removing = model })
                        }
                    }
                )
            }

            if (menu == null) {
                Text(text = "The repair menu couldn't be loaded", style = TmTheme.typography.caption, color = colors.textMuted)
            }
        }
    }

    editing?.let { (model, isNew) ->
        CatalogModelDialog(
            model = model,
            isNew = isNew,
            others = menu.orEmpty().filter { it.identifier != model.identifier },
            taxClasses = taxClasses,
            onSearchParts = onSearchParts,
            onPartNames = onPartNames,
            onSave = { saved, onResult -> onSave(saved, isNew) { problem -> onResult(problem); if (problem == null) editing = null } },
            onDismiss = { editing = null }
        )
    }

    TmConfirmDeleteDialog(
        itemName = removing?.label.orEmpty(),
        isVisible = removing != null,
        onConfirm = {
            removing?.let(onRemove)
            removing = null
        },
        onCancel = { removing = null },
        note = "Invoices keep the repairs already on them"
    )
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun CatalogModelDialog(
    model: CatalogModel,
    isNew: Boolean,
    others: List<CatalogModel>,
    taxClasses: List<TaxClass>,
    onSearchParts: suspend (String) -> List<TmSearchResult>,
    onPartNames: suspend (Collection<Uuid>) -> Map<Uuid, String>,
    onSave: (CatalogModel, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val spacing = TmTheme.spacing
    var draft by remember(model) { mutableStateOf(model) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Names of the parts the repairs use, filled in as they're read or picked
    var partNames by remember(model) { mutableStateOf<Map<Uuid, String>>(emptyMap()) }
    val invalid = RepairCatalog.problem(RepairCatalog.cleaned(draft), others)

    LaunchedEffect(model) {
        partNames = onPartNames(model.repairs.mapNotNull { it.inventoryIdentifier })
    }

    fun change(offer: RepairOffer) {
        draft = draft.copy(repairs = draft.repairs.map { if (it.identifier == offer.identifier) offer else it })
    }

    TmDialog(
        title = if (isNew) "Add a model" else "Edit ${model.label}",
        description = "Prices are the whole repair, part included",
        onDismissRequest = onDismiss,
        maxWidth = 640.dp,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                TmButton(text = "Cancel", variant = TmButtonVariant.SECONDARY, onClick = onDismiss)
                TmButton(
                    text = if (isNew) "Add" else "Save",
                    enabled = invalid == null && draft != model,
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
            { TmTextField(modifier = it, value = draft.manufacturer, onValueChange = { v -> draft = draft.copy(manufacturer = v) }, label = "Make", placeholder = "Apple") },
            { TmTextField(modifier = it, value = draft.model, onValueChange = { v -> draft = draft.copy(model = v) }, label = "Model", placeholder = "iPhone 14 Pro") }
        )
        TmDropdown(
            modifier = Modifier.width(200.dp),
            label = "Kind",
            options = DeviceKind.entries,
            selected = draft.kind,
            onSelect = { draft = draft.copy(kind = it) },
            optionLabel = { it.label }
        )

        Text(text = "Repairs", style = TmTheme.typography.label, color = TmTheme.colors.text)
        draft.repairs.forEachIndexed { index, offer ->
            if (index > 0) TmDivider()
            RepairEditor(
                offer = offer,
                partName = offer.inventoryIdentifier?.let { partNames[it] ?: "A part from inventory" },
                taxClasses = taxClasses,
                onSearchParts = onSearchParts,
                onChange = ::change,
                onPickPart = { part ->
                    if (part != null) partNames = partNames + (part.identifier to part.text)
                    change(offer.copy(inventoryIdentifier = part?.identifier))
                },
                onRemove = { draft = draft.copy(repairs = draft.repairs.filter { it.identifier != offer.identifier }) }
            )
        }
        TmButton(
            text = "Add repair",
            leadingIcon = Res.drawable.plus,
            variant = TmButtonVariant.SECONDARY,
            size = TmControlSize.SMALL,
            enabled = draft.repairs.size < RepairCatalog.MAX_REPAIRS,
            onClick = { draft = draft.copy(repairs = draft.repairs + RepairOffer()) }
        )

        (problem ?: invalid?.takeIf { draft != model && draft.model.isNotBlank() })?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
    }
}

/** One repair on a model: name, price, warranty, tax class and the part it uses */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun RepairEditor(
    offer: RepairOffer,
    partName: String?,
    taxClasses: List<TaxClass>,
    onSearchParts: suspend (String) -> List<TmSearchResult>,
    onChange: (RepairOffer) -> Unit,
    onPickPart: (TmSearchResult?) -> Unit,
    onRemove: () -> Unit
) {
    val spacing = TmTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.Bottom) {
            TmTextField(
                modifier = Modifier.weight(1f),
                value = offer.name,
                onValueChange = { onChange(offer.copy(name = it)) },
                label = "Repair",
                placeholder = "Screen replacement"
            )
            TmMoneyField(
                modifier = Modifier.width(140.dp),
                label = "Price",
                cents = offer.priceCents,
                onCentsChange = { onChange(offer.copy(priceCents = it)) }
            )
            TmIconButton(icon = Res.drawable.trash_2, contentDescription = "Remove ${offer.name.ifBlank { "this repair" }}", onClick = onRemove)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            TmTextField(
                modifier = Modifier.width(160.dp),
                value = offer.warrantyDays?.toString().orEmpty(),
                onValueChange = { v -> onChange(offer.copy(warrantyDays = v.filter { it.isDigit() }.take(4).toIntOrNull())) },
                label = "Warranty days",
                placeholder = "Shop's",
                textStyle = TmTheme.typography.numeric,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            if (taxClasses.isNotEmpty()) {
                TmDropdown(
                    modifier = Modifier.weight(1f),
                    label = "Tax",
                    options = listOf<TaxClass?>(null) + taxClasses,
                    selected = taxClasses.firstOrNull { it.identifier == offer.taxClass },
                    onSelect = { onChange(offer.copy(taxClass = it?.identifier)) },
                    optionLabel = { it?.name ?: "Main rate" },
                    placeholder = "Main rate"
                )
            }
        }

        PartPicker(partName = partName, onSearch = onSearchParts, onPick = onPickPart)
    }
}

/** The part a repair uses, picked by searching inventory, or none for labor only */
@Composable
private fun PartPicker(
    partName: String?,
    onSearch: suspend (String) -> List<TmSearchResult>,
    onPick: (TmSearchResult?) -> Unit
) {
    if (partName != null) {
        TmListRow(
            title = partName,
            subtitle = "Part used, taken from stock at pickup",
            icon = Res.drawable.package_icon,
            trailing = { TmButton(text = "No part", variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL, onClick = { onPick(null) }) }
        )
        return
    }

    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<TmSearchResult>>(emptyList()) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            matches = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        matches = onSearch(query).take(5)
    }

    TmSearchField(modifier = Modifier.fillMaxWidth(), query = query, onQueryChange = { query = it }, label = "Part used", placeholder = "None, labor only. Search inventory to add one")
    matches.forEach { match ->
        TmListRow(title = match.text, icon = Res.drawable.package_icon, onClick = { onPick(match) })
    }
}
