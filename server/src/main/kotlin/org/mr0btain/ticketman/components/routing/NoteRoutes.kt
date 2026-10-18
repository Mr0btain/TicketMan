package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.database.Database
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.logging.AppLogger

/**
 * Note routes shared by every entity: /add/note/{id}, /get/notes/{id} and /remove/note/{id}.
 * Anyone logged in can read notes, writing needs NORMAL access
 * @param entityType Used in the audit log, e.g. "ticket"
 */
fun Route.noteRoutes(
    database: Database<*>,
    userDatabase: UserDatabaseImpl,
    auditLog: AuditLogDatabase,
    entityType: String
) {
    val TAG = "NoteRoutes:$entityType"
    val GET_NOTES_TAG = "$TAG:GetNotes"

    authenticate("restricted") {
        // Post to match the client, which sends the identifier as the body too
        post("/get/notes/{identifier}") {
            val identifier = call.uuidParameter("identifier")
            val result = identifier?.let { database.getNotes(it) }

            if (result != null) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.NotFound)
                AppLogger.m(GET_NOTES_TAG, "$identifier not found")
            }
        }
    }

    authenticate {
        post("/add/note/{identifier}") {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            // Notes are signed by whoever wrote them unless an author was given
            val received = call.receive<Note>()
            val note = received.copy(author = received.author.ifBlank { call.username(userDatabase) })

            if (database.addNote(identifier, note)) {
                call.audit(auditLog, AuditAction.CREATE, "$entityType note", identifier, note.message.take(80))
                call.respond(HttpStatusCode.Created, note)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    authenticate {
        // Replaces a note's text and images, the identifier is the note's. Images must be uploaded first
        post("/update/note/{identifier}") {
            val identifier = call.uuidParameter("identifier")
            val received = call.receive<Note>()

            if (identifier == null || identifier != received.identifier) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if (database.updateNote(received)) {
                call.audit(auditLog, AuditAction.UPDATE, "$entityType note", identifier, received.message.take(80))
                call.respond(HttpStatusCode.Accepted)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        delete("/remove/note/{identifier}") {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            if (!database.removeNote(identifier)) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            call.audit(auditLog, AuditAction.DELETE, "$entityType note", identifier)
            call.respond(HttpStatusCode.Accepted)
        }
    }
}
