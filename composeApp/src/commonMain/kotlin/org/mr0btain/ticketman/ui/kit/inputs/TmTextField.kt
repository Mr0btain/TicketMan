package org.mr0btain.ticketman.ui.kit.inputs

import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import org.jetbrains.compose.resources.DrawableResource
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * The text field used everywhere. The label sits above the field instead of inside it
 * @param error Message shown under the field, also marks it as wrong
 * @param prefix Text before the value that can't be edited, like "$"
 */
@Composable
fun TmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    size: TmControlSize = TmControlSize.MEDIUM,
    leadingIcon: DrawableResource? = null,
    trailing: (@Composable () -> Unit)? = null,
    prefix: String? = null,
    textStyle: TextStyle? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource? = null
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()

    TmFieldFrame(
        modifier = modifier,
        label = label,
        supportingText = supportingText,
        error = error,
        enabled = enabled,
        focused = focused && !readOnly,
        size = size,
        leadingIcon = leadingIcon,
        trailing = trailing
    ) {
        if (prefix != null) {
            Text(text = prefix, style = textStyle ?: TmTheme.typography.body, color = TmTheme.colors.textMuted)
        }

        TmBasicInput(
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
            textStyle = textStyle,
            size = size
        ) { style, inputModifier, decorate ->
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = inputModifier,
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = style,
                cursorBrush = SolidColor(TmTheme.colors.accent.color),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                interactionSource = source,
                decorationBox = { inner -> decorate(value.isEmpty(), inner) }
            )
        }
    }
}

/** Same as the String version, for fields that need to control the cursor */
@Composable
fun TmTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    size: TmControlSize = TmControlSize.MEDIUM,
    leadingIcon: DrawableResource? = null,
    trailing: (@Composable () -> Unit)? = null,
    prefix: String? = null,
    textStyle: TextStyle? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource? = null
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()

    TmFieldFrame(
        modifier = modifier,
        label = label,
        supportingText = supportingText,
        error = error,
        enabled = enabled,
        focused = focused && !readOnly,
        size = size,
        leadingIcon = leadingIcon,
        trailing = trailing
    ) {
        if (prefix != null) {
            Text(text = prefix, style = textStyle ?: TmTheme.typography.body, color = TmTheme.colors.textMuted)
        }

        TmBasicInput(
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
            textStyle = textStyle,
            size = size
        ) { style, inputModifier, decorate ->
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = inputModifier,
                enabled = enabled,
                readOnly = readOnly,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(TmTheme.colors.accent.color),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                interactionSource = source,
                decorationBox = { inner -> decorate(value.text.isEmpty(), inner) }
            )
        }
    }
}

/**
 * Shared setup for the BasicTextFields above: resolves the text style and draws the placeholder.
 * [field] gets the style, a modifier for the field, and a decorator to call from its decorationBox
 */
@Composable
private fun TmBasicInput(
    modifier: Modifier,
    placeholder: String?,
    textStyle: TextStyle?,
    size: TmControlSize,
    field: @Composable (
        style: TextStyle,
        inputModifier: Modifier,
        decorate: @Composable (isEmpty: Boolean, inner: @Composable () -> Unit) -> Unit
    ) -> Unit
) {
    val colors = TmTheme.colors
    val base = textStyle ?: if (size == TmControlSize.LARGE) TmTheme.typography.bodyLarge else TmTheme.typography.body
    val style = base.copy(color = colors.text)
    val verticalPadding = TmTheme.spacing.sm

    field(style, modifier.padding(vertical = verticalPadding)) { isEmpty, inner ->
        Box {
            if (isEmpty && placeholder != null) {
                Text(text = placeholder, style = style, color = colors.textFaint, maxLines = 1)
            }
            inner()
        }
    }
}
