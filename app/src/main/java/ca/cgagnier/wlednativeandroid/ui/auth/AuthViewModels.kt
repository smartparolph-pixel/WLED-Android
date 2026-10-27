package ca.cgagnier.wlednativeandroid.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.auth.AuthError
import ca.cgagnier.wlednativeandroid.service.auth.AuthResult
import ca.cgagnier.wlednativeandroid.service.auth.AuthService
import ca.cgagnier.wlednativeandroid.service.auth.AuthValidation
import ca.cgagnier.wlednativeandroid.service.auth.FieldError
import ca.cgagnier.wlednativeandroid.service.auth.LoginErrors
import ca.cgagnier.wlednativeandroid.service.auth.SignUpErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Message shown above the primary button. */
data class FormMessage(val text: Int, val isError: Boolean)

fun FieldError.message(): Int = when (this) {
    FieldError.REQUIRED -> R.string.sinag_error_required
    FieldError.INVALID_EMAIL -> R.string.sinag_error_email
    FieldError.PASSWORD_TOO_SHORT -> R.string.sinag_error_password_short
    FieldError.PASSWORDS_DIFFER -> R.string.sinag_error_password_mismatch
}

private fun AuthError.message(): Int = when (this) {
    AuthError.INVALID_CREDENTIALS -> R.string.sinag_error_invalid_credentials
    AuthError.EMAIL_IN_USE -> R.string.sinag_error_email_in_use
    AuthError.NETWORK -> R.string.sinag_error_network
    AuthError.UNKNOWN -> R.string.sinag_error_unknown
}

enum class SplashDestination { MAIN, LOGIN }

/** Shows the splash for [MIN_SPLASH_MILLIS], then routes on whether a session exists. */
@HiltViewModel
class SplashViewModel @Inject constructor(private val authService: AuthService) : ViewModel() {
    var destination by mutableStateOf<SplashDestination?>(null)
        private set

    init {
        viewModelScope.launch {
            val session = async { authService.currentSession() }
            delay(MIN_SPLASH_MILLIS)
            destination = if (session.await() != null) SplashDestination.MAIN else SplashDestination.LOGIN
        }
    }

    private companion object {
        const val MIN_SPLASH_MILLIS = 1_500L
    }
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    /** Errors only show after the first submit, then update as the user types. */
    val showErrors: Boolean = false,
    val loading: Boolean = false,
    val message: FormMessage? = null,
    val loggedIn: Boolean = false,
) {
    val errors: LoginErrors get() = if (showErrors) AuthValidation.login(email, password) else LoginErrors()
}

@HiltViewModel
class LoginViewModel @Inject constructor(private val authService: AuthService) : ViewModel() {
    var state by mutableStateOf(LoginUiState())
        private set

    fun setEmail(value: String) {
        state = state.copy(email = value, message = null)
    }

    fun setPassword(value: String) {
        state = state.copy(password = value, message = null)
    }

    fun submit() {
        if (state.loading) return
        state = state.copy(showErrors = true, message = null)
        if (!state.errors.isValid) return
        state = state.copy(loading = true)
        viewModelScope.launch {
            state = when (val result = authService.login(state.email.trim(), state.password)) {
                is AuthResult.Success -> state.copy(loading = false, loggedIn = true, password = "")

                is AuthResult.Failure -> state.copy(
                    loading = false,
                    message = FormMessage(result.error.message(), true),
                )
            }
        }
    }

    fun forgotPassword() {
        if (state.loading) return
        val emailError = AuthValidation.email(state.email)
        if (emailError != null) {
            state = state.copy(showErrors = true, message = FormMessage(R.string.sinag_reset_needs_email, true))
            return
        }
        state = state.copy(loading = true, message = null)
        viewModelScope.launch {
            val sent = authService.requestPasswordReset(state.email.trim())
            state = state.copy(
                loading = false,
                message = if (sent) {
                    FormMessage(
                        R.string.sinag_reset_sent,
                        false,
                    )
                } else {
                    FormMessage(R.string.sinag_error_network, true)
                },
            )
        }
    }
}

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val showErrors: Boolean = false,
    val loading: Boolean = false,
    val message: FormMessage? = null,
    val signedUp: Boolean = false,
) {
    val errors: SignUpErrors
        get() = when {
            showErrors -> AuthValidation.signUp(name, email, password, confirmPassword)

            // A mismatch is shown as soon as both password fields have text.
            confirmPassword.isNotEmpty() && confirmPassword != password ->
                SignUpErrors(confirmPassword = FieldError.PASSWORDS_DIFFER)

            else -> SignUpErrors()
        }
}

@HiltViewModel
class SignUpViewModel @Inject constructor(private val authService: AuthService) : ViewModel() {
    var state by mutableStateOf(SignUpUiState())
        private set

    fun setName(value: String) {
        state = state.copy(name = value, message = null)
    }

    fun setEmail(value: String) {
        state = state.copy(email = value, message = null)
    }

    fun setPassword(value: String) {
        state = state.copy(password = value, message = null)
    }

    fun setConfirmPassword(value: String) {
        state = state.copy(confirmPassword = value, message = null)
    }

    fun submit() {
        if (state.loading) return
        state = state.copy(showErrors = true, message = null)
        if (!state.errors.isValid) return
        state = state.copy(loading = true)
        viewModelScope.launch {
            val result = authService.signUp(state.name.trim(), state.email.trim(), state.password)
            state = when (result) {
                is AuthResult.Success -> state.copy(
                    loading = false,
                    signedUp = true,
                    password = "",
                    confirmPassword = "",
                )

                is AuthResult.Failure -> state.copy(
                    loading = false,
                    message = FormMessage(result.error.message(), true),
                )
            }
        }
    }
}
