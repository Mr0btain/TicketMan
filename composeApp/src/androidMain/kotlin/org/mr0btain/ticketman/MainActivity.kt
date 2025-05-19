package org.mr0btain.ticketman

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import components.injection.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App()
            initKoin {
                androidLogger()
                androidContext(this@MyApp)
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}