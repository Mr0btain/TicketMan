package org.mr0btain.ticketman.ui.screens.admin.stations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.station.KioskSettings
import org.mr0btain.ticketman.data.station.Station
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.ui.composables.EmptyMessage
import org.mr0btain.ticketman.ui.composables.SectionCard
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatTimeAgo
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors

/**
 * Devices staff can PIN in at. Devices register themselves from Settings, admins approve them here
 */
@Composable
fun AdminStationsTab(
    stations: List<Station>,
    onApprove: (Station) -> Unit,
    onKiosk: (Station, KioskSettings) -> Unit,
    onRemove: (Station) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        SectionCard(
            modifier = Modifier.widthIn(max = 900.dp),
            title = "Stations (${stations.size})"
        ) {
            Text(
                text = "Staff can only sign in with a PIN on an approved station. A device asks to be one from Settings > Counter Station.",
                fontSize = 13.sp
            )

            Spacer(Modifier.padding(4.dp))

            if (stations.isEmpty()) {
                EmptyMessage("No devices have asked to be a station")
            }

            stations.forEach { station ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (station.status == StationStatus.APPROVED) CustomMaterialColors.success else CustomMaterialColors.inProgress
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            text = if (station.status == StationStatus.APPROVED) "Approved" else "Waiting",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Spacer(Modifier.padding(6.dp))

                    Column(Modifier.weight(1f)) {
                        Text(text = station.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Registered by ${station.requestedBy} ${formatTimeAgo(station.requestedAt)}" +
                                (station.approvedAt?.let { " - approved by ${station.approvedBy} on ${formatDateTime(it)}" } ?: ""),
                            fontSize = 12.sp
                        )
                    }

                    if (station.status == StationStatus.PENDING) {
                        Button(onClick = { onApprove(station) }) { Text("Approve") }
                        Spacer(Modifier.padding(4.dp))
                    }

                    OutlinedButton(onClick = { onRemove(station) }) { Text("Remove") }
                }

                if (station.status == StationStatus.APPROVED) KioskControls(station, onKiosk)

                HorizontalDivider()
            }
        }
    }
}

/**
 * Kiosk mode switch and idle lock time for one station
 */
@Composable
private fun KioskControls(station: Station, onKiosk: (Station, KioskSettings) -> Unit) {
    var timeout by remember(station) { mutableStateOf(station.idleTimeoutSeconds.toString()) }
    val seconds = timeout.toIntOrNull()?.takeIf { it in Station.IDLE_TIMEOUT_RANGE }

    Row(
        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = station.kioskEnabled,
            onCheckedChange = { enabled ->
                onKiosk(station, KioskSettings(enabled, seconds ?: station.idleTimeoutSeconds))
            }
        )

        Text(
            modifier = Modifier.padding(horizontal = 8.dp),
            text = if (station.kioskEnabled) "Kiosk mode, locks after" else "Kiosk mode off, would lock after",
            fontSize = 13.sp
        )

        OutlinedTextField(
            modifier = Modifier.width(110.dp),
            value = timeout,
            onValueChange = { text -> timeout = text.filter { it.isDigit() }.take(4) },
            label = { Text("Seconds") },
            isError = seconds == null,
            singleLine = true
        )

        Spacer(Modifier.padding(4.dp))

        TextButton(
            enabled = seconds != null && seconds != station.idleTimeoutSeconds,
            onClick = { seconds?.let { onKiosk(station, KioskSettings(station.kioskEnabled, it)) } }
        ) { Text("Save") }
    }
}
