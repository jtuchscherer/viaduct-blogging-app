package org.tuchscherer.auth

/** Shared limits for registration and administrative user updates. */
object UserInputLimits {
    const val MAX_USERNAME_LENGTH = 100
    const val MAX_EMAIL_LENGTH = 255
    const val MAX_NAME_LENGTH = 255
}
