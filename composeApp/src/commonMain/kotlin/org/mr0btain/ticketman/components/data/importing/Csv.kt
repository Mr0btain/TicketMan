package org.mr0btain.ticketman.components.data.importing

/**
 * Reads CSV the way spreadsheets write it (RFC 4180): quoted cells can hold the separator, quotes doubled up and line
 * breaks. The separator is whichever of comma, semicolon or tab the first line uses most outside quotes, since
 * spreadsheets in some countries save with semicolons. A byte order mark and blank lines are dropped
 * @return Rows of cells, the first row being the headers if the file has them
 */
fun parseCsv(text: String): List<List<String>> {
    val content = text.removePrefix("﻿")
    val separator = guessSeparator(content)
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0

    fun endCell() {
        row.add(cell.toString())
        cell.clear()
    }

    fun endRow() {
        endCell()
        if (row.any { it.isNotBlank() }) rows.add(row)
        row = mutableListOf()
    }

    while (i < content.length) {
        val c = content[i]
        when {
            quoted && c == '"' && content.getOrNull(i + 1) == '"' -> {
                cell.append('"')
                i++
            }
            c == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
            quoted -> cell.append(c)
            c == separator -> endCell()
            c == '\r' -> if (content.getOrNull(i + 1) != '\n') endRow()
            c == '\n' -> endRow()
            else -> cell.append(c)
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) endRow()

    return rows
}

private fun guessSeparator(text: String): Char {
    val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
    var quoted = false

    for (c in text) {
        if (c == '"') quoted = !quoted
        if (!quoted && (c == '\n' || c == '\r')) break
        if (!quoted && c in counts) counts[c] = counts.getValue(c) + 1
    }

    return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
}
