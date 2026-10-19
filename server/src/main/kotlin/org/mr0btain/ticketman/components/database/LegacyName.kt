package org.mr0btain.ticketman.components.database

/**
 * A name as older versions stored it, as it should read. They kept `first:last`, often lowercased, and capitalised
 * each part when showing it. Parts are split on `:` and each word gets a capital first letter: "jane:doe" is
 * "Jane Doe"
 */
fun legacyName(stored: String): String =
    stored.split(':')
        .flatMap { part -> part.trim().split(' ') }
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

/** Whether [name] looks like an older version wrote it: split with `:`, or all lowercase */
fun isLegacyName(name: String): Boolean =
    ':' in name || (name.any { it.isLetter() } && name == name.lowercase())
