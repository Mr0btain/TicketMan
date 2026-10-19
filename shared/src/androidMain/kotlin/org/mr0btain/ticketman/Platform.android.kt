package org.mr0btain.ticketman

import android.os.Build

class AndroidPlatform : Platform {
    // "Samsung SM-T220", or just the model when it already starts with the maker ("Pixel 7" from Google)
    override val name: String = Build.MODEL.let { model ->
        val maker = Build.MANUFACTURER.replaceFirstChar { it.titlecase() }
        if (model.startsWith(Build.MANUFACTURER, ignoreCase = true) || maker == "Google") model else "$maker $model"
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()
