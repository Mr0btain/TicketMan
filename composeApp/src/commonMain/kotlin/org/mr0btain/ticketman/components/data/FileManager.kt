package org.mr0btain.ticketman.components.data

import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.components.data.components.settings.UserSettings

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class FileManager() {
    // TODO: Setup error management
    fun getSettings(scope: CoroutineScope): UserSettings
    fun saveSettings(settings: UserSettings, scope: CoroutineScope)
}

/*
// Store app configuration
val configFile = FileKit.filesDir / "config.json"
configFile.writeString("{\"version\": 1}")

// Store temporary processing data
val tempFile = FileKit.cacheDir / "temp_data.bin"
tempFile.write(processingData)

```kotlin
// Access a database file
val dbFile = FileKit.databasesDir / "app.db"
// Use the database file with your preferred database library
 */



// File picker stuff from FileKit
/*
// Pick a file
val file = FileKit.openFilePicker()

// Pick multiple files
val files = FileKit.openFilePicker(mode = FileKitMode.Multiple())

// Pick only images
val imageFile = FileKit.openFilePicker(type = FileKitType.Image)

// Pick a directory
val directory = FileKit.openDirectoryPicker()

// Save a file
val contentToSave = "Hello FileKit!"
val file = FileKit.openFileSaver(suggestedName = "document", extension = "txt")
file?.writeString(contentToSave)

// Work with files
val myFile = FileKit.filesDir / "document.pdf"
println(myFile.name)
println(myFile.size())
myFile.writeString("Hello, World!")

// Image operations
val compressedBytes = FileKit.compressImage(
    bytes = imageFile.readBytes(),
    quality = 80,
    maxWidth = 1024,
    maxHeight = 1024
)
 */