package ca.cgagnier.wlednativeandroid.service.auth

import kotlinx.coroutines.delay
import java.util.UUID

/**
 * ⚠️ DEVELOPMENT ONLY. Not a real backend: accounts live in memory and no data leaves the phone.
 *
 * Replace with the Supabase implementation before release. Behaviour, so every screen state can be
 * exercised by hand:
 * - Sign up succeeds unless the email was already used in this run (EMAIL_IN_USE).
 * - Log in succeeds for any account created in this run with the same password, and for any email
 *   with the password "password123". An email containing "offline" simulates a NETWORK error.
 * - Every call waits [latencyMillis] so the loading states are visible.
 */
class FakeAuthService(private val sessionStore: SessionStore, private val latencyMillis: Long = DEFAULT_LATENCY) :
    AuthService {

    private val accounts = mutableMapOf<String, Pair<String, String>>() // email -> (name, password)

    override suspend fun login(email: String, password: String): AuthResult {
        delay(latencyMillis)
        val key = email.trim().lowercase()
        val account = accounts[key]
        return when {
            OFFLINE_MARKER in key -> AuthResult.Failure(AuthError.NETWORK)
            account?.second == password || password == DEMO_PASSWORD -> success(key, account?.first)
            else -> AuthResult.Failure(AuthError.INVALID_CREDENTIALS)
        }
    }

    override suspend fun signUp(name: String, email: String, password: String): AuthResult {
        delay(latencyMillis)
        val key = email.trim().lowercase()
        return when {
            OFFLINE_MARKER in key -> AuthResult.Failure(AuthError.NETWORK)

            key in accounts -> AuthResult.Failure(AuthError.EMAIL_IN_USE)

            else -> {
                accounts[key] = name.trim() to password
                success(key, name.trim())
            }
        }
    }

    override suspend fun requestPasswordReset(email: String): Boolean {
        delay(latencyMillis)
        return OFFLINE_MARKER !in email.lowercase()
    }

    override suspend fun signOut() {
        sessionStore.clear()
    }

    override suspend fun currentSession(): AuthSession? = sessionStore.load()

    private suspend fun success(email: String, name: String?): AuthResult {
        val session = AuthSession(
            userId = UUID.nameUUIDFromBytes(email.toByteArray()).toString(),
            email = email,
            name = name,
            token = "fake-${UUID.randomUUID()}",
        )
        sessionStore.save(session)
        return AuthResult.Success(session)
    }

    companion object {
        const val DEMO_PASSWORD = "password123"
        private const val OFFLINE_MARKER = "offline"
        private const val DEFAULT_LATENCY = 800L
    }
}

/** In-memory [SessionStore] for tests and previews. */
class InMemorySessionStore : SessionStore {
    private var session: AuthSession? = null

    override suspend fun save(session: AuthSession) {
        this.session = session
    }

    override suspend fun load(): AuthSession? = session

    override suspend fun clear() {
        session = null
    }
}
