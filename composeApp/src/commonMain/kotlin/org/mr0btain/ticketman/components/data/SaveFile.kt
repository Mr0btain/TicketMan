package org.mr0btain.ticketman.components.data

/**
 * Lets the user pick where to save [bytes]. On web this is a browser download.
 * @param suggestedName File name without the extension
 * @param extension Extension without the dot
 * @return Whether the file was written, false if it failed or the user cancelled
 */
expect suspend fun saveFile(bytes: ByteArray, suggestedName: String, extension: String): Boolean
