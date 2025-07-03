package org.mr0btain.ticketman

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.mr0btain.ticketman.components.data.datastore.ContextUtils
import org.mr0btain.ticketman.components.injection.initKoin

class MainActivity : ComponentActivity() {
    companion object {
        lateinit var appContext: Context
        private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ContextUtils.setContext(context = this)
        appContext = applicationContext
        initKoin()

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}