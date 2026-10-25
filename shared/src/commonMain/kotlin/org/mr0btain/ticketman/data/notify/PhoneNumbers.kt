package org.mr0btain.ticketman.data.notify

/**
 * Phone numbers as text message providers want them
 */
object PhoneNumbers {
    /**
     * [phone] in international form like `+15551234567`, or null when it can't be one. Numbers saved without a
     * country code are taken as [countryCode]'s, dropping a leading trunk 0 (`07700 900123` in the UK)
     * @param countryCode Digits only, like `1` or `44`
     */
    fun toInternational(phone: String, countryCode: String): String? {
        val trimmed = phone.trim()
        val digits = trimmed.filter { it.isDigit() }
        val country = countryCode.filter { it.isDigit() }

        val international = when {
            trimmed.startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            // Typed with the country code but no +, like 1 555 123 4567
            country.isNotEmpty() && digits.startsWith(country) && digits.length > NATIONAL_LENGTH -> digits
            country.isNotEmpty() -> country + digits.trimStart('0')
            else -> return null
        }

        return "+$international".takeIf { NotificationSettings.PHONE.matches(it) }
    }

    // Longer than this without a + already has its country code
    private const val NATIONAL_LENGTH = 10
}
