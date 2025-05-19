import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import components.injection.initKoin

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Multiplatform Template") {
        initKoin()
        App()
    }
}