package org.mr0btain.ticketman.ui.screens.login.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.server
import ticketman.composeapp.generated.resources.server_address_string


@Composable
fun ServerAddressField(
    modifier: Modifier = Modifier,
    error: Boolean,
    onContinue: (String) -> Unit
) {
    val address = remember { mutableStateOf("") }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Address field
        OutlinedTextField(
            label = {
                Text(stringResource(Res.string.server_address_string))
            },
            value = address.value,
            onValueChange = { address.value = it },
            singleLine = true,
            leadingIcon = {
                Icon(
                    modifier = Modifier.size(22.dp),
                    painter = painterResource(Res.drawable.server),
                    contentDescription = "Server address"
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            ),
            isError = error
        )

        Spacer(Modifier.padding(6.dp))

        // Continue button
        Button(
            onClick = { onContinue(address.value) }
        ) {
            Text("Continue")
        }
    }
}