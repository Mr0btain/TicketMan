package org.mr0btain.ticketman.util

import kotlinx.io.files.Path

/**
 * Just a class to get the app dir for the particular desktop system were running on
 * Most of this work was done by https://github.com/vinceglb/FileKit So thanks to him!
 */

expect class AppDir {
    val appId: String
    val path: Path?
    val currentPlatform: Platform
}