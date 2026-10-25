package org.mr0btain.ticketman.data.notify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationsTest {
    @Test
    fun phoneNumbersBecomeInternational() {
        assertEquals("+15551234567", PhoneNumbers.toInternational("(555) 123-4567", "1"))
        assertEquals("+15551234567", PhoneNumbers.toInternational("1 555 123 4567", "1"))
        assertEquals("+447700900123", PhoneNumbers.toInternational("07700 900123", "44"))
        assertEquals("+447700900123", PhoneNumbers.toInternational("+44 7700 900123", "1"))
        assertEquals("+447700900123", PhoneNumbers.toInternational("0044 7700 900123", "1"))
        assertNull(PhoneNumbers.toInternational("", "1"))
        assertNull(PhoneNumbers.toInternational("12", "1"))
    }

    @Test
    fun templatesFillTheirFieldsAndLeaveUnknownOnes() {
        val filled = MessageTemplates.fill("Hi {name}, {ticket} is {status}. {nope}", mapOf("name" to "Jane", "ticket" to "26-1", "status" to "ready"))
        assertEquals("Hi Jane, 26-1 is ready. {nope}", filled)
    }

    @Test
    fun secretsAreHiddenAndKeptWhenLeftOut() {
        val saved = NotificationSettings(smtp = SmtpSettings(password = "pw"), sms = SmsSettings(authToken = "tok"))
        val shown = saved.withoutSecrets()

        assertNull(shown.smtp.password)
        assertEquals(true, shown.smtpPasswordSet)
        assertEquals("pw", shown.keepingSecretsOf(saved).smtp.password)
        assertEquals("", shown.copy(smtp = shown.smtp.copy(password = "")).keepingSecretsOf(saved).smtp.password)
    }
}
