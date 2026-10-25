package org.mr0btain.ticketman.ui.screens.locations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.screens.locations.components.LocationsHandler
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.map_pin

/**
 * Where this device works, for Settings. Nothing with only one location; fixed on a station
 * @param canSeeAll Offers every location at once, for admins
 */
@Composable
fun LocationPicker(locations: LocationsHandler, canSeeAll: Boolean, modifier: Modifier = Modifier) {
    val several by locations.several.collectAsState()
    if (!several) return

    val active by locations.active.collectAsState()
    val working by locations.working.collectAsState()
    val fixed by locations.fixed.collectAsState()

    // Null stands for every location
    val options: List<Location?> = active + if (canSeeAll) listOf(null) else emptyList()
    val selected = if (working.all) null else active.firstOrNull { it.identifier == working.identifier }

    TmDropdown(
        modifier = modifier,
        label = "Working at",
        options = options,
        selected = if (working.all || selected != null) selected else active.firstOrNull(),
        onSelect = { locations.choose(it?.identifier) },
        optionLabel = { it?.label ?: "All locations" },
        enabled = !fixed,
        leadingIcon = Res.drawable.map_pin,
        supportingText = if (fixed) "This device is a station, it stays at its own location" else "Lists, the counter and new work keep to it"
    )
}
