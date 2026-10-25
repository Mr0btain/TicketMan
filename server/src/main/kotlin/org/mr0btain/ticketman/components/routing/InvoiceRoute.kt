package org.mr0btain.ticketman.components.routing

import org.mr0btain.ticketman.server.InvoicePaths
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.mr0btain.ticketman.components.database.InvoiceDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.service.AccountingExport
import org.mr0btain.ticketman.components.service.InvoiceDocuments
import org.mr0btain.ticketman.data.invoice.ExportFormat
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.logging.AppLogger
import java.time.LocalDate
import org.mr0btain.ticketman.components.service.InvoiceService
import org.mr0btain.ticketman.server.request.InvoiceChangeRequest
import org.mr0btain.ticketman.server.request.PaymentRequest
import org.mr0btain.ticketman.server.request.RefundRequest

/**
 * Invoice routes. Anyone logged in can look, NORMAL and up can change open invoices, only admins can void, refund
 * and export
 */
fun Route.invoiceRoute(
    database: InvoiceDatabase,
    service: InvoiceService,
    userDatabase: UserDatabase,
    documents: InvoiceDocuments,
    auditLog: AuditLogDatabase,
    accounting: AccountingExport
) {
    val TAG = "InvoiceRoute"
    val PDF_TAG = "$TAG:Pdf"
    val EXPORT_TAG = "$TAG:Export"

    authenticate("restricted") {
        // Laid out like the printed one on a letter page, to email or file
        get(InvoicePaths.PDF) {
            val pdf = call.uuidParameter("identifier")?.let { documents.pdf(it) }

            if (pdf == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, pdf.fileName).toString()
            )
            call.respondOutputStream(ContentType.Application.Pdf) {
                try {
                    documents.writePdf(pdf.document, this)
                } catch (ex: Exception) {
                    // Headers are gone by now, so the download is cut short rather than answered 500
                    AppLogger.e(PDF_TAG, "Couldn't make ${pdf.fileName}", ex)
                    throw ex
                }
            }
        }

        get(InvoicePaths.FOR_TICKET) {
            val invoice = call.uuidParameter("ticketIdentifier")?.let { database.getByTicket(it) }

            if (invoice != null) call.respond(HttpStatusCode.OK, invoice) else call.respond(HttpStatusCode.NotFound)
        }

        get(InvoicePaths.GET) {
            val invoice = call.uuidParameter("identifier")?.let { database.getById(it) }

            if (invoice != null) call.respond(HttpStatusCode.OK, invoice) else call.respond(HttpStatusCode.NotFound)
        }

        get(InvoicePaths.FETCH) {
            val request = call.pagedRequest() ?: return@get call.respondBadQuery()

            call.respond(HttpStatusCode.OK, database.getPagedItems(request))
        }
    }

    authenticate {
        // Gets or makes the ticket's invoice
        post(InvoicePaths.FOR_TICKET) {
            val ticket = call.uuidParameter("ticketIdentifier")

            if (ticket == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(service.forTicket(ticket, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        post(InvoicePaths.UPDATE) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(service.update(identifier, call.receive<InvoiceChangeRequest>(), call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        post(InvoicePaths.PAYMENTS) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(
                service.addPayment(identifier, call.receive<PaymentRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.OK
            )
        }

        delete(InvoicePaths.PAYMENT) {
            val identifier = call.uuidParameter("identifier")
            val payment = call.uuidParameter("paymentIdentifier")

            if (identifier == null || payment == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            call.respondAccount(service.removePayment(identifier, payment, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }
    }

    authenticate("admin") {
        // For the books: invoices or payments between two days, as a spreadsheet, QuickBooks or Xero want them
        get(InvoicePaths.EXPORT) {
            val from = call.queryParameters["from"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val to = call.queryParameters["to"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val format = call.queryParameters["format"]?.let { name -> ExportFormat.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

            if (from == null || to == null || to < from) {
                call.respondText("Give from and to as days like 2026-10-01, from first", status = HttpStatusCode.BadRequest)
                return@get
            }

            if (call.queryParameters["format"] != null && format == null) {
                call.respondText("Format is one of ${ExportFormat.entries.joinToString { it.name.lowercase() }}", status = HttpStatusCode.BadRequest)
                return@get
            }

            val kind = format ?: ExportFormat.INVOICES
            val write: (java.io.Writer) -> Unit
            val count: Int

            when (kind) {
                ExportFormat.INVOICES -> {
                    val list = documents.inRange(from, to) ?: return@get call.respond(HttpStatusCode.InternalServerError)
                    write = { documents.writeCsv(list, it) }
                    count = list.size
                }

                ExportFormat.QUICKBOOKS, ExportFormat.XERO -> {
                    val list = accounting.paidInRange(from, to) ?: return@get call.respond(HttpStatusCode.InternalServerError)
                    write = { if (kind == ExportFormat.XERO) accounting.writeXero(list, it) else accounting.writeQuickBooks(list, it) }
                    count = list.size
                }

                ExportFormat.PAYMENTS -> {
                    val list = accounting.paymentsInRange(from, to) ?: return@get call.respond(HttpStatusCode.InternalServerError)
                    write = { accounting.writePayments(list, it) }
                    count = list.size
                }
            }

            val what = if (kind == ExportFormat.PAYMENTS) "payments" else "invoices"
            call.audit(auditLog, AuditAction.BACKUP, "invoices", details = "Exported $count $what for ${kind.label}, $from to $to")
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "${kind.fileName}-$from-to-$to.csv").toString()
            )
            call.respondTextWriter(ContentType.Text.CSV.withCharset(Charsets.UTF_8)) { write(this) }
            AppLogger.m(EXPORT_TAG, "$count $what for ${kind.label}, $from to $to")
        }

        post(InvoicePaths.VOID) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(service.void(identifier, call.getIdentifierFromToken()), HttpStatusCode.OK)
        }

        post(InvoicePaths.REFUND) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            call.respondAccount(
                service.refund(identifier, call.receive<RefundRequest>(), call.username(userDatabase), call.getIdentifierFromToken()),
                HttpStatusCode.OK
            )
        }

        // Only one kept after its ticket was deleted, see InvoiceService.remove
        delete(InvoicePaths.REMOVE) {
            val identifier = call.uuidParameter("identifier")

            if (identifier == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            call.respondAccount(service.remove(identifier, call.getIdentifierFromToken()), HttpStatusCode.Accepted)
        }
    }
}
