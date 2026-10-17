package org.mr0btain.ticketman.ui.kit.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.dp

/**
 * Corner sizes. Things inside other things get smaller corners: sheet > panel > control > chip
 */
@Immutable
data class TmShapes(
    val chip: CornerBasedShape = RoundedCornerShape(6.dp),
    val control: CornerBasedShape = RoundedCornerShape(10.dp),
    val panel: CornerBasedShape = RoundedCornerShape(14.dp),
    val sheet: CornerBasedShape = RoundedCornerShape(18.dp),
    val pill: CornerBasedShape = CircleShape
) {
    fun toMaterial(): Shapes = Shapes(
        extraSmall = chip,
        small = control,
        medium = panel,
        large = panel,
        extraLarge = sheet
    )
}
