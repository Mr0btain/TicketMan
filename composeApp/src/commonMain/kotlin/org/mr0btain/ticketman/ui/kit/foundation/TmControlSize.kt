package org.mr0btain.ticketman.ui.kit.foundation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height shared by buttons, fields and dropdowns so they line up in a row.
 * LARGE is for touch screens at the counter and kiosk
 */
enum class TmControlSize(val height: Dp, val horizontalPadding: Dp, val iconSize: Dp) {
    SMALL(32.dp, 12.dp, 16.dp),
    MEDIUM(40.dp, 16.dp, 18.dp),
    LARGE(52.dp, 22.dp, 20.dp)
}
