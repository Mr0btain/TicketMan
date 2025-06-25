package org.mr0btain.ticketman.components.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Basically just an OutlinedTextField that uses the newer BasicTextField that has InputTransformation
 * and OutputTransformation instead of VisualTransformation
 *
 * @param state [TextFieldState] object that holds the internal editing state of this text field.
 * @param modifier a [Modifier] for this text field
 * @param enabled controls the enabled state of the [CustomOutlinedTextField]. When `false`, the text
 *   field will be neither editable nor focusable, the input of the text field will not be
 *   selectable, visually text field will appear in the disabled UI state
 * @param readOnly controls the editable state of the [CustomOutlinedTextField]. When `true`, the text
 *   field can not be modified, however, a user can focus it and copy text from it. Read-only text
 *   fields are usually used to display pre-filled forms that user can not edit
 * @param textStyle the style to be applied to the input text. The default [textStyle] uses the
 *   [LocalTextStyle] defined by the theme
 * @param label the optional label to be displayed inside the text field container. The default text
 *   style for internal text is Typography.caption when the text field is in focus and
 *   Typography.subtitle1 when the text field is not in focus
 * @param placeholder the optional placeholder to be displayed when the text field is in focus and
 *   the input text is empty. The default text style for internal text is Typography.subtitle1
 * @param leadingIcon the optional leading icon to be displayed at the beginning of the text field
 *   container
 * @param trailingIcon the optional trailing icon to be displayed at the end of the text field
 *   container
 * @param isError indicates if the text field's current value is in error. If set to true, the
 *   label, bottom indicator and trailing icon by default will be displayed in error color
 * @param inputTransformation Optional [InputTransformation] that will be used to transform changes
 *   to the [TextFieldState] made by the user. The transformation will be applied to changes made by
 *   hardware and software keyboard events, pasting or dropping text, accessibility services, and
 *   tests. The transformation will _not_ be applied when changing the [state] programmatically, or
 *   when the transformation is changed. If the transformation is changed on an existing text field,
 *   it will be applied to the next user edit. the transformation will not immediately affect the
 *   current [state].
 * @param outputTransformation An [OutputTransformation] that transforms how the contents of the
 *   text field are presented.
 * @param keyboardOptions software keyboard options that contains configuration such as
 *   KeyboardType and ImeAction
 * @param onKeyboardAction Called when the user presses the action button in the input method editor
 *   (IME), or by pressing the enter key on a hardware keyboard. By default, this parameter is null,
 *   and would execute the default behavior for a received IME Action e.g., ImeAction.Done would
 *   close the keyboard, ImeAction.Next would switch the focus to the next focusable item on the
 *   screen.
 * @param lineLimits Whether the text field should be SingleLine, scroll horizontally, and ignore
 *   newlines; or MultiLine and grow and scroll vertically. If SingleLine is passed, all newline
 *   characters ('\n') within the text will be replaced with regular whitespace (' '), ensuring that
 *   the contents of the text field are presented in a single line.
 * @param scrollState Scroll state that manages either horizontal or vertical scroll of the text
 *   field. If lineLimits is SingleLine, this text field is treated as single line with
 *   horizontal scroll behavior. In other cases the text field becomes vertically scrollable.
 * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
 *   emitting Interactions for this text field. You can use this to change the text field's
 *   appearance or preview the text field in different states. Note that if `null` is provided,
 *   interactions will still happen internally.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomOutlinedTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    scrollState: ScrollState = rememberScrollState(),
    interactionSource: MutableInteractionSource = MutableInteractionSource(),
) {
    BasicTextField(
        modifier = if (label != null) modifier.padding(top = 8.dp) else modifier
            .sizeIn(
                minWidth = OutlinedTextFieldDefaults.MinWidth,
                minHeight = OutlinedTextFieldDefaults.MinHeight
            ),
        state = state,
        textStyle = textStyle,
        inputTransformation = inputTransformation,
        outputTransformation = outputTransformation,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        scrollState = scrollState,
        keyboardOptions = keyboardOptions,
        enabled = enabled,
        readOnly = readOnly,
        interactionSource = interactionSource,
        decorator = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                enabled = enabled,
                innerTextField = innerTextField,
                singleLine = true,
                value = state.text.toString(),
                placeholder = placeholder,
                visualTransformation = BlankVisualTransform(),
                interactionSource = interactionSource,
                isError = isError,
                prefix = prefix,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                label = label
            )
        }
    )
}

/**
 * Just a blank visual transformation that's needed for decoration box
 */

private class BlankVisualTransform: VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val offsetMap = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int { return offset }
            override fun transformedToOriginal(offset: Int): Int { return offset }
        }
        return TransformedText(text, offsetMap)
    }
}