package components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.arrow_right
import ticketman.composeapp.generated.resources.x
import ui.theme.onErrorDark

/**
 * Text edit dialog
 */

@Composable
fun TextEditDialog(
    name: String,
    storedValue: State<String>,
    onSave: (String) -> Unit,
    onCheck: (String) -> Boolean,
    inputFilter: (String) -> String,
    keyboardType: KeyboardType,
    buttonText: String,
    placeholderText: String = "",
    onDismiss: () -> Unit // internal method to dismiss dialog from within
) {
    // storage for input
    var currentInput by remember {
        mutableStateOf(TextFieldValue(storedValue.value))
    }

    // if the input is valid - run the method for current value
    var isValid by remember {
        mutableStateOf(onCheck(storedValue.value))
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            Text(
                text = name,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                singleLine = true,
                value = currentInput,
                shape = RoundedCornerShape(8.dp),
                trailingIcon = {
                    IconButton(
                        modifier = Modifier.padding(4.dp),
                        onClick = {
                            // Check if input is valid
                            if (isValid) {
                                // save and dismiss the dialog
                                onSave(currentInput.text)
                                onDismiss()
                            } else {
                                // If we just want to clear then clear the text
                                currentInput = TextFieldValue("")
                            }
                        },
                        enabled = currentInput.text.isNotEmpty()
                    ) {
                        Icon(
                            painter = if (isValid) painterResource(Res.drawable.arrow_right) else painterResource(Res.drawable.x),
                            contentDescription = "Clear Contents"
                        )
                    }
                },
                onValueChange = {
                    // Filter input
                    val filteredText = inputFilter(it.text)

                    // Check if its valid
                    isValid = onCheck(filteredText)

                    // Set filtered value
                    currentInput = it.copy(text = filteredText)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next
                ),
                placeholder = { Text(placeholderText) }
            )

            Row {
                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        // save and dismiss the dialog
                        onSave(currentInput.text)
                        onDismiss()
                        // disable / enable the button
                    },
                    enabled = isValid
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}