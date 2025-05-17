@file:Suppress("FunctionName")

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App() {
    val platform = getPlatform()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = platform.name,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            fontSize = 24.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Hello World!",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            fontSize = 24.sp
        )
    }
}