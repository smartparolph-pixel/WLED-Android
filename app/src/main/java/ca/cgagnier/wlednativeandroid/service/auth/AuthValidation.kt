package ca.cgagnier.wlednativeandroid.service.auth

/** Field problems shown under the fields. The UI maps each to a string resource. */
enum class FieldError { REQUIRED, INVALID_EMAIL, PASSWORD_TOO_SHORT, PASSWORDS_DIFFER }

data class LoginErrors(val email: FieldError? = null, val password: FieldError? = null) {
    val isValid: Boolean get() = email == null && password == null
}

data class SignUpErrors(
    val name: FieldError? = null,
    val email: FieldError? = null,
    val password: FieldError? = null,
    val confirmPassword: FieldError? = null,
) {
    val isValid: Boolean get() = listOf(name, email, password, confirmPassword).all { it == null }
}

object AuthValidation {
    const val MIN_PASSWORD_LENGTH = 8

    // Pragmatic format check: something@something.tld, no spaces. The server has the final say.
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun email(value: String): FieldError? = when {
        value.isBlank() -> FieldError.REQUIRED
        !EMAIL.matches(value.trim()) -> FieldError.INVALID_EMAIL
        else -> null
    }

    fun newPassword(value: String): FieldError? = when {
        value.isEmpty() -> FieldError.REQUIRED
        value.length < MIN_PASSWORD_LENGTH -> FieldError.PASSWORD_TOO_SHORT
        else -> null
    }

    fun confirmPassword(password: String, confirm: String): FieldError? = when {
        confirm.isEmpty() -> FieldError.REQUIRED
        confirm != password -> FieldError.PASSWORDS_DIFFER
        else -> null
    }

    fun login(email: String, password: String) = LoginErrors(
        email = email(email),
        password = if (password.isEmpty()) FieldError.REQUIRED else null,
    )

    fun signUp(name: String, email: String, password: String, confirm: String) = SignUpErrors(
        name = if (name.isBlank()) FieldError.REQUIRED else null,
        email = email(email),
        password = newPassword(password),
        confirmPassword = confirmPassword(password, confirm),
    )
}
