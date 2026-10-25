package org.mr0btain.ticketman.components.notify

import jakarta.mail.AuthenticationFailedException
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.SendFailedException
import jakarta.mail.Session
import jakarta.mail.internet.AddressException
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mr0btain.ticketman.data.notify.SmsSettings
import org.mr0btain.ticketman.data.notify.SmtpSecurity
import org.mr0btain.ticketman.data.notify.SmtpSettings
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.Properties

/** What came of trying to send a message */
sealed interface SendResult {
    data object Sent : SendResult

    /** Worth trying again later, like the server being down */
    data class Retry(val reason: String) : SendResult

    /** Won't work however often it's tried, like an address that doesn't exist */
    data class Failed(val reason: String) : SendResult
}

fun interface EmailSender {
    fun send(smtp: SmtpSettings, to: String, subject: String, body: String): SendResult
}

fun interface SmsSender {
    fun send(sms: SmsSettings, to: String, body: String): SendResult
}

/**
 * The senders the server uses. Tests swap them for ones that only record, like `ServerRestart.exit`
 */
object NotificationSenders {
    var email: EmailSender = SmtpEmailSender()
    var sms: SmsSender = TwilioSmsSender()

    fun reset() {
        email = SmtpEmailSender()
        sms = TwilioSmsSender()
    }
}

/**
 * Plain text mail through the shop's own server
 */
class SmtpEmailSender : EmailSender {
    override fun send(smtp: SmtpSettings, to: String, subject: String, body: String): SendResult {
        val properties = Properties().apply {
            val protocol = if (smtp.security == SmtpSecurity.TLS) "smtps" else "smtp"
            put("mail.transport.protocol", protocol)
            put("mail.$protocol.host", smtp.host.trim())
            put("mail.$protocol.port", smtp.port.toString())
            put("mail.$protocol.auth", smtp.username.isNotBlank().toString())
            put("mail.$protocol.connectiontimeout", TIMEOUT_MILLIS.toString())
            put("mail.$protocol.timeout", TIMEOUT_MILLIS.toString())
            put("mail.$protocol.writetimeout", TIMEOUT_MILLIS.toString())
            if (smtp.security == SmtpSecurity.STARTTLS) {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
            if (smtp.security != SmtpSecurity.NONE) put("mail.$protocol.ssl.checkserveridentity", "true")
        }

        return try {
            val session = Session.getInstance(properties)
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(smtp.fromAddress.trim(), smtp.fromName.trim().ifBlank { null }, "UTF-8"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true))
                setSubject(subject, "UTF-8")
                setText(body, "UTF-8")
                sentDate = java.util.Date()
            }

            session.transport.use { transport ->
                if (smtp.username.isNotBlank()) transport.connect(smtp.username, smtp.password.orEmpty())
                else transport.connect()
                transport.sendMessage(message, message.allRecipients)
            }

            SendResult.Sent
        } catch (ex: AddressException) {
            SendResult.Failed("Not an email address: ${ex.message}")
        } catch (ex: SendFailedException) {
            // The server refused the recipient
            SendResult.Failed(ex.message ?: "The mail server refused the address")
        } catch (ex: AuthenticationFailedException) {
            SendResult.Retry("The mail server didn't accept the username and password")
        } catch (ex: MessagingException) {
            SendResult.Retry(ex.message ?: ex.toString())
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000
    }
}

/**
 * Texts through Twilio's REST API, called directly so no SDK is needed
 * @param baseUrl Swapped in tests for a local server
 */
class TwilioSmsSender(
    private val baseUrl: String = "https://api.twilio.com",
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
) : SmsSender {
    override fun send(sms: SmsSettings, to: String, body: String): SendResult {
        val sid = sms.accountSid.trim()
        val form = mapOf("To" to to, "From" to sms.fromNumber.trim(), "Body" to body)
            .entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
        val credentials = Base64.getEncoder().encodeToString("$sid:${sms.authToken.orEmpty()}".toByteArray())

        val request = HttpRequest.newBuilder(URI.create("$baseUrl/2010-04-01/Accounts/${URLEncoder.encode(sid, Charsets.UTF_8)}/Messages.json"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Basic $credentials")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()

        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (ex: Exception) {
            return SendResult.Retry("Couldn't reach Twilio: ${ex.message ?: ex.toString()}")
        }

        val status = response.statusCode()
        val reason = "Twilio said $status: ${errorMessage(response.body())}"

        return when {
            status in 200..299 -> SendResult.Sent
            // Wrong credentials can be fixed in the settings, and the message still goes
            status == 401 || status == 403 || status == 429 || status >= 500 -> SendResult.Retry(reason)
            else -> SendResult.Failed(reason)
        }
    }

    private fun errorMessage(body: String): String =
        runCatching { Json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
            ?: body.take(200)
}
