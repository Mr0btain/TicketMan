package screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.beans.introspect.PropertyInfo
import components.MainViewModel
import org.jetbrains.compose.resources.stringResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.login_text
import ticketman.composeapp.generated.resources.settings_text

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Surface(
            color = if (viewModel.darkMode.value) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.settings_text),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.padding(12.dp))

                // Use dark mode option
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        modifier = Modifier.semantics { contentDescription = "If enabled will use dark mode all the time" },
                        text = "Dark Mode",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.padding(16.dp))

                    Switch(
                        checked = viewModel.darkMode.value,
                        onCheckedChange = {
                            viewModel.darkMode.value = !viewModel.darkMode.value
                        }
                    )
                }
            }
        }
    }
}