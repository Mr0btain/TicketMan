package org.mr0btain.ticketman.database.tables

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.json.json

object ImageTable: DataIntTable("images") {
    val images = json<List<String>>("imageList", Json { ignoreUnknownKeys = true })
}