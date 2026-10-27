package ca.cgagnier.wlednativeandroid.service.auth

/** A signed-in user. [token] is a secret: never log it. */
data class AuthSession(val userId: String, val email: String, val name: String?, val token: String) {
    override fun toString(): String = "AuthSession(userId=$userId, email=$email)"
}

enum class AuthError { INVALID_CREDENTIALS, EMAIL_IN_USE, NETWORK, UNKNOWN }

sealed interface AuthResult {
    data class Success(val session: AuthSession) : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

/**
 * Account operations used by the Sinag screens. The real provider (Supabase) is not wired yet;
 * until then [FakeAuthService] is bound in [ca.cgagnier.wlednativeandroid.di.AuthModule].
 */
interface AuthService {
    suspend fun login(email: String, password: String): AuthResult

    suspend fun signUp(name: String, email: String, password: String): AuthResult

    /** @return false only when the request could not be sent. Never reveals whether the email exists. */
    suspend fun requestPasswordReset(email: String): Boolean

    suspend fun signOut()

    /** The stored session, or null when signed out. */
    suspend fun currentSession(): AuthSession?
}

/** Where the session survives app restarts. Implementations must keep the token encrypted at rest. */
interface SessionStore {
    suspend fun save(session: AuthSession)

    suspend fun load(): AuthSession?

    suspend fun clear()
}
