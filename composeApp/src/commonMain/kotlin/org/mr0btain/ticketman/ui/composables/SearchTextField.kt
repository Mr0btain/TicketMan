package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.search
import ticketman.composeapp.generated.resources.x

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTextField(
    modifier: Modifier = Modifier,
    label: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    val query = remember { mutableStateOf("")}

    OutlinedTextField(
        modifier = modifier,
        value = query.value,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        onValueChange = { text ->
            // Update the text
            query.value = text

            // Run the change through
            onValueChange(text)
        },
        leadingIcon = {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(Res.drawable.search),
                contentDescription = ""
            )
        },
        trailingIcon = {
            // Clear value button only show if there is text
            if (query.value.isNotEmpty()) {
                IconButton(
                    modifier = Modifier.size(26.dp),
                    onClick = { query.value = "" }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.x),
                        contentDescription = ""
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp)
    )
}