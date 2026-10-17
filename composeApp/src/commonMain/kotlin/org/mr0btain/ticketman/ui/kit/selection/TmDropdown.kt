package org.mr0btain.ticketman.ui.kit.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldFrame
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_down

/**
 * Pick one of [options]. Looks like a text field so it lines up in forms
 * @param optionLabel Text shown for an option
 * @param optionLeading Optional thing drawn before each option and the selected value, like a status dot
 */
@Composable
fun <T> TmDropdown(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Choose one",
    supportingText: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    size: TmControlSize = TmControlSize.MEDIUM,
    leadingIcon: DrawableResource? = null,
    optionLeading: (@Composable (T) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0) }
    val chevronTurn by animateFloatAsState(if (expanded) 180f else 0f)
    val colors = TmTheme.colors

    Box(modifier) {
        TmFieldFrame(
            label = label,
            supportingText = supportingText,
            error = error,
            enabled = enabled,
            focused = expanded,
            size = size,
            leadingIcon = leadingIcon,
            wellModifier = Modifier
                .onSizeChanged { anchorWidth = it.width }
                .tmClickable(
                    onClick = { expanded = !expanded },
                    shape = TmTheme.shapes.control,
                    enabled = enabled,
                    role = Role.DropdownList
                ),
            trailing = {
                Icon(
                    modifier = Modifier.size(size.iconSize).rotate(chevronTurn),
                    painter = painterResource(Res.drawable.chevron_down),
                    contentDescription = null
                )
            }
        ) {
            if (selected != null) optionLeading?.invoke(selected)

            Text(
                modifier = Modifier.weight(1f),
                text = selected?.let(optionLabel) ?: placeholder,
                style = if (size == TmControlSize.LARGE) TmTheme.typography.bodyLarge else TmTheme.typography.body,
                color = if (selected != null) colors.text else colors.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val menuWidth = with(LocalDensity.current) { anchorWidth.toDp() }

        TmMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = if (menuWidth > 0.dp) Modifier.width(menuWidth) else Modifier
        ) {
            options.forEach { option ->
                TmMenuItem(
                    text = optionLabel(option),
                    selected = option == selected,
                    leading = optionLeading?.let { { it(option) } },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
