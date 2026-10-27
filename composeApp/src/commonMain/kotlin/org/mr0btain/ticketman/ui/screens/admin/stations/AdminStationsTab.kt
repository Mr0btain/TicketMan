package org.mr0btain.ticketman.ui.screens.admin.stations

import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatTimeAgo
import org.mr0btain.ticketman.data.station.KioskSettings
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmSurface
import org.mr0btain.ticketman.ui.kit.foundation.TmSurfaceLevel
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmSwitch
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.monitor

/**
 * Devices staff can PIN in at. Devices register themselves from Settings, admins approve them here
 * @param locations The locations a station can be at, offered with more than one
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminStationsTab(
    stations: List<Station>,
    onApprove: (Station) -> Unit,
    onKiosk: (Station, KioskSettings) -> Unit,
    onRemove: (Station) -> Unit,
    locations: List<Location> = emptyList(),
    onLocation: (Station, Location) -> Unit = { _, _ -> }
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TmPanel(
            modifier = Modifier.widthIn(max = 900.dp),
            title = "Stations",
            subtitle = "Staff can only sign in with a PIN on an approved station. A device asks to be one from Settings, Counter station.",
            icon = Res.drawable.monitor
        ) {
            if (stations.isEmpty()) {
                TmEmptyState(icon = Res.drawable.monitor, title = "No devices have asked to be a station")
            }

            stations.forEach { station ->
                val approved = station.status == StationStatus.APPROVED

                TmSurface(level = TmSurfaceLevel.SUNKEN, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                // The badge goes under the name when there's no room beside it
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                                    itemVerticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = station.name, style = TmTheme.typography.subtitle, color = colors.text)
                                    TmBadge(text = if (approved) "Approved" else "Waiting for approval", tone = if (approved) colors.success else colors.warning)
                                }
                                Text(
                                    text = "Registered by ${station.requestedBy} ${formatTimeAgo(station.requestedAt)}" +
                                        (station.approvedAt?.let { ", approved by ${station.approvedBy} on ${formatDateTime(it)}" } ?: ""),
                                    style = TmTheme.typography.caption,
                                    color = colors.textMuted
                                )
                            }

                            if (!approved) TmButton(text = "Approve", onClick = { onApprove(station) })
                            TmButton(text = "Remove", onClick = { onRemove(station) }, variant = TmButtonVariant.GHOST)
                        }

                        // Where it is, once the shop has more than one location
                        if (locations.size > 1) {
                            TmDropdown(
                                modifier = Modifier.widthIn(max = 320.dp),
                                label = "Location",
                                options = locations,
                                selected = locations.firstOrNull { it.identifier == station.locationIdentifier } ?: locations.firstOrNull { it.main },
                                onSelect = { onLocation(station, it) },
                                optionLabel = { it.label },
                                size = TmControlSize.SMALL
                            )
                        }

                        if (approved) KioskControls(station, onKiosk)
                    }
                }
            }
        }
    }
}

/**
 * Kiosk mode switch and idle lock time for one station
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KioskControls(station: Station, onKiosk: (Station, KioskSettings) -> Unit) {
    var timeout by remember(station) { mutableStateOf(station.idleTimeoutSeconds.toString()) }
    val seconds = timeout.toIntOrNull()?.takeIf { it in Station.IDLE_TIMEOUT_RANGE }
    val spacing = TmTheme.spacing

    // Wraps on a narrow window, the time with its unit staying together
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        TmSwitch(
            checked = station.kioskEnabled,
            contentDescription = "Kiosk mode",
            onCheckedChange = { enabled -> onKiosk(station, KioskSettings(enabled, seconds ?: station.idleTimeoutSeconds)) }
        )

        Text(
            text = if (station.kioskEnabled) "Kiosk mode, locks after" else "Kiosk mode off, would lock after",
            style = TmTheme.typography.body,
            color = TmTheme.colors.text
        )

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            TmTextField(
                modifier = Modifier.width(90.dp),
                value = timeout,
                onValueChange = { text -> timeout = text.filter { it.isDigit() }.take(4) },
                textStyle = TmTheme.typography.numeric,
                size = TmControlSize.SMALL,
                error = if (seconds == null) "" else null
            )

            Text(text = "seconds", style = TmTheme.typography.body, color = TmTheme.colors.textMuted, maxLines = 1)
        }

        TmButton(
            text = "Save",
            variant = TmButtonVariant.SECONDARY,
            size = TmControlSize.SMALL,
            enabled = seconds != null && seconds != station.idleTimeoutSeconds,
            onClick = { seconds?.let { onKiosk(station, KioskSettings(station.kioskEnabled, it)) } }
        )
    }
}
