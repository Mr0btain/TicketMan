package org.mr0btain.ticketman.components.routing

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.*
import kotlin.uuid.ExperimentalUuidApi


fun Route.imageRoute(
    appDir: String
) {
    val imageUploadDir = "$appDir/database/images"

    authenticate {
        post("/get") {
            // TODO()
        }
    }

    authenticate {
        post("/fetch") {
            // TODO()
        }
    }


    post("/upload-image") {
        /*
        al multipart = call.receiveMultipart()
        var noteId: Uuid? = null
        var fileName: String? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    if (part.name == "noteId") {
                        noteId = Uuid.parse(part.value)
                    }
                }

                is PartData.FileItem -> {
                    if (part.name == "image") {
                        val originalFileName = part.originalFileName ?: "image_${System.currentTimeMillis()}.jpg"
                        val fileBytes = part.streamProvider().readBytes()

                        // Save file to disk
                        val savePath = File(imageUploadDir, originalFileName)
                        savePath.writeBytes(fileBytes)

                        fileName = savePath.path
                    }
                }

                else -> Unit
            }

            part.dispose()
        }

        if (noteId != null && fileName != null) {
            // Store in DB
            val newImage = transaction {
                NoteEntity.findByIdAndUpdate(noteId.toJavaUuid()) { note ->
                    note.images = note.images
                }

                val note = NoteEntity.findById(noteId.toJavaUuid())
                    ?: return@transaction call.respond(HttpStatusCode.BadRequest, "Note not found")

                ImageEntity.new {
                    this.note = note
                    this.path = fileName // or use relative path

                }


            }

            call.respond(HttpStatusCode.OK, "Image uploaded and saved.")
        } else {
            call.respond(HttpStatusCode.BadRequest, "Missing noteId or image file.")
        }
         */
    }

}