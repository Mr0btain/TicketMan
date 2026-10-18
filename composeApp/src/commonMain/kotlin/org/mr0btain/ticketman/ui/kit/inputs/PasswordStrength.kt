package org.mr0btain.ticketman.ui.kit.inputs

fun passwordStrength(password: String): PasswordStrength =
    when {
        REGEX_STRONG_PASSWORD.toRegex().containsMatchIn(password) -> PasswordStrength.STRONG
        else -> PasswordStrength.WEAK
    }

enum class PasswordStrength {
    STRONG,
    WEAK
}

private const val REGEX_STRONG_PASSWORD =
    "(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9])(?=.{8,})"