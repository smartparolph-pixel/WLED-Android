package ca.cgagnier.wlednativeandroid.service.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {

    @Test
    fun email() {
        assertEquals(FieldError.REQUIRED, AuthValidation.email(" "))
        assertEquals(FieldError.INVALID_EMAIL, AuthValidation.email("juan"))
        assertEquals(FieldError.INVALID_EMAIL, AuthValidation.email("juan@parol"))
        assertEquals(FieldError.INVALID_EMAIL, AuthValidation.email("juan dela@cruz.ph"))
        assertNull(AuthValidation.email("juan@sinagtechlabs.com"))
        assertNull(AuthValidation.email("  juan@sinag.ph  "))
    }

    @Test
    fun passwordNeedsEightCharacters() {
        assertEquals(FieldError.REQUIRED, AuthValidation.newPassword(""))
        assertEquals(FieldError.PASSWORD_TOO_SHORT, AuthValidation.newPassword("1234567"))
        assertNull(AuthValidation.newPassword("12345678"))
    }

    @Test
    fun confirmPasswordMustMatch() {
        assertEquals(FieldError.REQUIRED, AuthValidation.confirmPassword("parol2026", ""))
        assertEquals(FieldError.PASSWORDS_DIFFER, AuthValidation.confirmPassword("parol2026", "parol2025"))
        assertNull(AuthValidation.confirmPassword("parol2026", "parol2026"))
    }

    @Test
    fun forms() {
        assertTrue(AuthValidation.login("juan@sinag.ph", "x").isValid)
        assertEquals(LoginErrors(FieldError.INVALID_EMAIL, FieldError.REQUIRED), AuthValidation.login("juan", ""))
        assertTrue(AuthValidation.signUp("Juan", "juan@sinag.ph", "parol2026", "parol2026").isValid)
        assertEquals(
            SignUpErrors(FieldError.REQUIRED, null, null, FieldError.PASSWORDS_DIFFER),
            AuthValidation.signUp(" ", "juan@sinag.ph", "parol2026", "parol2027"),
        )
    }
}

class FakeAuthServiceTest {

    private val store = InMemorySessionStore()
    private val service = FakeAuthService(store, latencyMillis = 0)

    @Test
    fun signUpThenLogInAndOut() = runBlocking {
        val signUp = service.signUp("Juan", "Juan@Sinag.ph", "parol2026")
        assertTrue(signUp is AuthResult.Success)
        assertEquals("juan@sinag.ph", service.currentSession()?.email)
        assertEquals(AuthResult.Failure(AuthError.EMAIL_IN_USE), service.signUp("J", "juan@sinag.ph", "parol2026"))

        service.signOut()
        assertNull(service.currentSession())
        assertEquals(AuthResult.Failure(AuthError.INVALID_CREDENTIALS), service.login("juan@sinag.ph", "wrong-pass"))
        assertTrue(service.login("juan@sinag.ph", "parol2026") is AuthResult.Success)
        assertEquals("Juan", service.currentSession()?.name)
    }

    @Test
    fun demoPasswordAndOfflineMarker() = runBlocking {
        assertTrue(service.login("anyone@sinag.ph", FakeAuthService.DEMO_PASSWORD) is AuthResult.Success)
        assertEquals(AuthResult.Failure(AuthError.NETWORK), service.login("offline@sinag.ph", "parol2026"))
        assertFalse(service.requestPasswordReset("offline@sinag.ph"))
        assertTrue(service.requestPasswordReset("juan@sinag.ph"))
    }
}

class SessionCodecTest {

    @Test
    fun roundTrip() {
        val session = AuthSession("id-1", "juan@sinag.ph", "Juan", "secret-token")
        assertEquals(session, SessionCodec.decode(SessionCodec.encode(session)))
        val noName = session.copy(name = null)
        assertEquals(noName, SessionCodec.decode(SessionCodec.encode(noName)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsGarbage() {
        SessionCodec.decode("""{"email":"juan@sinag.ph"}""")
    }

    @Test
    fun toStringHidesToken() {
        assertFalse("secret-token" in AuthSession("id", "e@x.ph", null, "secret-token").toString())
    }
}
