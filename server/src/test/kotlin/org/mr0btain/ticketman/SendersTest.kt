package org.mr0btain.ticketman

import com.sun.net.httpserver.HttpServer
import org.mr0btain.ticketman.components.notify.SendResult
import org.mr0btain.ticketman.components.notify.SmtpEmailSender
import org.mr0btain.ticketman.components.notify.TwilioSmsSender
import org.mr0btain.ticketman.data.notify.SmsSettings
import org.mr0btain.ticketman.data.notify.SmtpSecurity
import org.mr0btain.ticketman.data.notify.SmtpSettings
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.test.*

/**
 * The real email and text senders, against a small SMTP server and a stand in for Twilio on this machine
 */
class SendersTest {
    // ------------------------------------------ Email ------------------------------------------

    /**
     * Just enough SMTP for one message. Refuses [refuse] at RCPT TO, and keeps every line it was sent
     */
    private class FakeSmtp(private val refuse: String? = null) : AutoCloseable {
        private val socket = ServerSocket(0)
        val port = socket.localPort
        val lines = mutableListOf<String>()

        private val worker = thread(isDaemon = true) {
            socket.accept().use { client ->
                val input = client.getInputStream().bufferedReader()
                val output = client.getOutputStream().bufferedWriter()
                fun reply(text: String) = output.apply { write("$text\r\n"); flush() }

                reply("220 fake ESMTP")
                var inData = false
                while (true) {
                    val line = input.readLine() ?: break
                    synchronized(lines) { lines += line }

                    when {
                        inData && line == "." -> { inData = false; reply("250 queued") }
                        inData -> Unit
                        line.startsWith("EHLO") -> { output.write("250-fake\r\n"); reply("250 AUTH PLAIN LOGIN") }
                        line.startsWith("AUTH") -> reply("235 ok")
                        line.startsWith("RCPT") && refuse != null && refuse in line -> reply("550 no such user")
                        line == "DATA" -> { inData = true; reply("354 go on") }
                        line == "QUIT" -> { reply("221 bye"); break }
                        else -> reply("250 ok")
                    }
                }
            }
        }

        fun received(): List<String> = synchronized(lines) { lines.toList() }

        override fun close() {
            worker.join(5_000)
            socket.close()
        }
    }

    private fun smtp(port: Int) = SmtpSettings(
        host = "localhost", port = port, security = SmtpSecurity.NONE, username = "shop", password = "secret",
        fromAddress = "shop@example.com", fromName = "Fix It Shop"
    )

    @Test
    fun emailIsSentWithTheLoginSubjectAndBody() {
        FakeSmtp().use { server ->
            val result = SmtpEmailSender().send(smtp(server.port), "jane@home.test", "Your iPhone is ready", "Hi Jane, it's ready.")
            assertEquals(SendResult.Sent, result)
            server.close()

            val lines = server.received()
            assertTrue(lines.any { it.startsWith("AUTH") })
            assertTrue(lines.any { it == "MAIL FROM:<shop@example.com>" })
            assertTrue(lines.any { it == "RCPT TO:<jane@home.test>" })
            assertTrue(lines.any { it == "Subject: Your iPhone is ready" })
            assertTrue(lines.any { it == "Hi Jane, it's ready." })
        }
    }

    @Test
    fun aRefusedAddressFailsAndAnUnreachableServerIsRetried() {
        FakeSmtp(refuse = "nobody@").use { server ->
            assertIs<SendResult.Failed>(SmtpEmailSender().send(smtp(server.port), "nobody@home.test", "Hi", "Hi"))
        }

        // Nothing listening
        val port = ServerSocket(0).use { it.localPort }
        assertIs<SendResult.Retry>(SmtpEmailSender().send(smtp(port), "jane@home.test", "Hi", "Hi"))
        assertIs<SendResult.Failed>(SmtpEmailSender().send(smtp(port), "not an address", "Hi", "Hi"))
    }

    // ------------------------------------------ Texts ------------------------------------------

    private data class Request(val path: String, val authorization: String?, val form: Map<String, String>)

    /** Answers every request with [status] and [body], keeping what it was sent */
    private fun twilio(status: Int, body: String, block: (String, MutableList<Request>) -> Unit) {
        val requests = mutableListOf<Request>()
        val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { exchange ->
            val form = exchange.requestBody.readBytes().decodeToString().split("&").associate { pair ->
                val (key, value) = pair.split("=", limit = 2)
                key to URLDecoder.decode(value, Charsets.UTF_8)
            }
            synchronized(requests) { requests += Request(exchange.requestURI.path, exchange.requestHeaders.getFirst("Authorization"), form) }

            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            block("http://localhost:${server.address.port}", requests)
        } finally {
            server.stop(0)
        }
    }

    private val sms = SmsSettings(accountSid = "AC123", authToken = "token", fromNumber = "+15550000000")

    @Test
    fun textsGoToTheAccountsMessagesWithBasicAuth() = twilio(201, """{"sid":"SM1"}""") { base, requests ->
        assertEquals(SendResult.Sent, TwilioSmsSender(base).send(sms, "+15551234567", "Ready & waiting"))

        val request = requests.single()
        assertEquals("/2010-04-01/Accounts/AC123/Messages.json", request.path)
        assertEquals("Basic " + Base64.getEncoder().encodeToString("AC123:token".toByteArray()), request.authorization)
        assertEquals(mapOf("To" to "+15551234567", "From" to "+15550000000", "Body" to "Ready & waiting"), request.form)
    }

    @Test
    fun aBadNumberFailsWithTwiliosReasonAndOutagesAreRetried() {
        twilio(400, """{"code":21211,"message":"The 'To' number is not valid."}""") { base, _ ->
            assertEquals(SendResult.Failed("Twilio said 400: The 'To' number is not valid."), TwilioSmsSender(base).send(sms, "+1", "Hi"))
        }

        listOf(401, 429, 503).forEach { status ->
            twilio(status, "{}") { base, _ -> assertIs<SendResult.Retry>(TwilioSmsSender(base).send(sms, "+15551234567", "Hi"), "$status") }
        }

        val closed = ServerSocket(0).use { it.localPort }
        assertIs<SendResult.Retry>(TwilioSmsSender("http://localhost:$closed").send(sms, "+15551234567", "Hi"))
    }
}
