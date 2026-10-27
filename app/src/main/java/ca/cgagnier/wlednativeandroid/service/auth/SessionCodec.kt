package ca.cgagnier.wlednativeandroid.service.auth

import com.squareup.moshi.Moshi

/** Session <-> JSON. Kept free of Android types so it can be unit-tested. */
object SessionCodec {
    private val adapter = Moshi.Builder().build().adapter(Map::class.java)

    fun encode(session: AuthSession): String = adapter.toJson(
        mapOf("userId" to session.userId, "email" to session.email, "name" to session.name, "token" to session.token),
    )

    /** @throws IllegalArgumentException when the JSON is not a session. */
    fun decode(json: String): AuthSession {
        val map = runCatching { adapter.fromJson(json) }.getOrNull()
        val userId = map?.get("userId") as? String
        val email = map?.get("email") as? String
        val token = map?.get("token") as? String
        require(userId != null && email != null && token != null) { "Not a session" }
        return AuthSession(userId, email, map["name"] as? String, token)
    }
}
