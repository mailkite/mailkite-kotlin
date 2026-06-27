// Docs: docs/architecture/client-side-oauth-libraries.md
//
// Where the token set lives between calls. The interface is pure JVM (so tests
// use MemoryTokenStore with no Android). The production EncryptedSharedPreferences
// store is in AndroidTokenStore.kt.

package dev.mailkite.client

/** The tokens held after a successful login. */
data class TokenSet(
    val accessToken: String,
    val refreshToken: String? = null,
    /** Epoch milliseconds when the access token expires. */
    val expiresAt: Long,
    val tokenType: String = "Bearer",
)

/** Pluggable token persistence. Implement this to back tokens with your own store. */
interface TokenStore {
    fun load(): TokenSet?
    fun save(tokens: TokenSet)
    fun clear()
}

/** In-memory store — the default for tests and ephemeral sessions. */
class MemoryTokenStore : TokenStore {
    @Volatile private var tokens: TokenSet? = null
    override fun load(): TokenSet? = tokens
    override fun save(tokens: TokenSet) { this.tokens = tokens }
    override fun clear() { tokens = null }
}

// --- serialization shared by persistent stores -------------------------------

internal object TokenJson {
    fun encode(t: TokenSet): String = Json.stringify(
        linkedMapOf(
            "accessToken" to t.accessToken,
            "refreshToken" to t.refreshToken,
            "expiresAt" to t.expiresAt,
            "tokenType" to t.tokenType,
        )
    )

    fun decode(raw: String?): TokenSet? {
        if (raw.isNullOrEmpty()) return null
        val m = Json.parse(raw) as? Map<*, *> ?: return null
        val access = m["accessToken"] as? String ?: return null
        return TokenSet(
            accessToken = access,
            refreshToken = m["refreshToken"] as? String,
            expiresAt = (m["expiresAt"] as? Number)?.toLong() ?: 0L,
            tokenType = (m["tokenType"] as? String) ?: "Bearer",
        )
    }
}
