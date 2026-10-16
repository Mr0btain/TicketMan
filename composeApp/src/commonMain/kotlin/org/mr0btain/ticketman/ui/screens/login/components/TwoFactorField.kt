package org.mr0btain.ticketman.ui.screens.login.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.shield

@Composable
fun TwoFactorField(
    modifier: Modifier = Modifier,
    state: TextFieldState = rememberTextFieldState()
) {
    Column(modifier) {
        Column(
            modifier = Modifier.width(560.dp)
        ) {
            Row(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ){
                Icon(
                    painter = painterResource(Res.drawable.shield),
                    "Two Factor"
                )

                Spacer(Modifier.padding(4.dp))

                Text(
                    text = "Two Factor Auth",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.padding(6.dp))
        }

        Surface(
            modifier = Modifier.width(560.dp),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text("Enter OTP Code")
                }

                Spacer(Modifier.padding(4.dp))

                BasicTextField(
                    modifier = Modifier
                        .semantics {
                            contentType = ContentType.SmsOtpCode
                        }
                        .fillMaxWidth()
                        .padding(8.dp),
                    state = state,
                    inputTransformation = InputTransformation.maxLength(6),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    decorator = {
                        val otpCode = state.text.toString()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            repeat(6) { index ->
                                Digit(otpCode.getOrElse(index, { ' ' }))
                            }
                        }
                    }
                )

                Spacer(Modifier.padding(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ClickableText(
                        text = AnnotatedString("Resend code"),
                        onClick = {

                        }
                    )
                }

                Spacer(Modifier.padding(4.dp))

                Button(
                    onClick = {
                        // TODO
                    }
                ) {
                    Text("Submit")
                }
            }
        }
    }
}

@Composable
private fun Digit(number: Char) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(
            Modifier.padding(10.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .border(1.5.dp, Color.Gray, RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(4.dp)
            ) {
                Text(
                    text = number.toString(),
                    fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}