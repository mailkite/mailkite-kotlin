// Docs: docs/architecture/client-side-oauth-libraries.md
//
// OAuth 2.1 Authorization-Code + PKCE against MailKite's existing OAuth server
// (api/src/oauth/server.ts). Pure JVM crypto (java.security / javax.crypto) +
// the pluggable HttpClient — no Android imports, so the PKCE math and request
// building are unit-tested on a plain JVM.

package dev.mailkite.client

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** A PKCE pair + CSRF state for one authorization request. */
data class Pkce(
    val verifier: String,
    val challenge: String,
    val state: String,
)

/** OAuth endpoints + this client's identity. */
data class OAuthConfig(
    /** mcp.mailkite.dev OAuth issuer base, e.g. "https://mcp.mailkite.dev". */
    val issuer: String,
    /** Public client id (pre-provisioned or from dynamic registration). */
    var clientId: String? = null,
    val redirectUri: String = "",
    /** Defaults to "mcp" (full account; the only scope today). */
    val scope: String? = null,
)

internal object OAuth {
    const val DEFAULT_SCOPE = "mcp"
    private val rng = SecureRandom()
    private val b64url = Base64.getUrlEncoder().withoutPadding()

    fun base64url(bytes: ByteArray): String = b64url.encodeToString(bytes)

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    /** verifier = base64url(32 random bytes); challenge = base64url(SHA-256(verifier)). */
    fun createPkce(): Pkce {
        val verifier = base64url(randomBytes(32)) // 43 chars, unreserved
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Pkce(verifier, base64url(digest), base64url(randomBytes(16)))
    }

    /** Build the /oauth/authorize URL the user is sent to. */
    fun authorizeUrl(cfg: OAuthConfig, pkce: Pkce): String {
        val q = formEncode(
            linkedMapOf(
                "response_type" to "code",
                "client_id" to (cfg.clientId ?: ""),
                "redirect_uri" to cfg.redirectUri,
                "scope" to (cfg.scope ?: DEFAULT_SCOPE),
                "state" to pkce.state,
                "code_challenge" to pkce.challenge,
                "code_challenge_method" to "S256",
            )
        )
        return "${cfg.issuer}/oauth/authorize?$q"
    }

    /** RFC 7591 dynamic client registration — returns a public client_id. */
    suspend fun registerClient(http: HttpClient, cfg: OAuthConfig, clientName: String = "MailKite Client"): String {
        val body = Json.stringify(
            linkedMapOf(
                "client_name" to clientName,
                "redirect_uris" to listOf(cfg.redirectUri),
                "token_endpoint_auth_method" to "none",
                "grant_types" to listOf("authorization_code", "refresh_token"),
                "response_types" to listOf("code"),
            )
        )
        val res = http.execute(
            HttpRequest(
                method = "POST",
                url = "${cfg.issuer}/oauth/register",
                headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                body = body.toByteArray(Charsets.UTF_8),
            )
        )
        if (res.status !in 200..299) throw oauthError(res)
        val parsed = Json.parse(res.text) as? Map<*, *>
        return (parsed?.get("client_id") as? String) ?: throw oauthError(res)
    }

    /** Exchange an authorization code for tokens. */
    suspend fun exchangeCode(http: HttpClient, cfg: OAuthConfig, code: String, verifier: String): TokenSet =
        tokenRequest(
            http, cfg,
            linkedMapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to cfg.redirectUri,
                "client_id" to (cfg.clientId ?: ""),
                "code_verifier" to verifier,
            )
        )

    /** Rotate a refresh token for a fresh access token (refresh token rotates too). */
    suspend fun refresh(http: HttpClient, cfg: OAuthConfig, refreshToken: String): TokenSet =
        tokenRequest(
            http, cfg,
            linkedMapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to (cfg.clientId ?: ""),
            )
        )

    /** RFC 7009 revocation — call on logout. */
    suspend fun revoke(http: HttpClient, cfg: OAuthConfig, token: String) {
        http.execute(
            HttpRequest(
                method = "POST",
                url = "${cfg.issuer}/oauth/revoke",
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = formEncode(linkedMapOf("token" to token, "client_id" to (cfg.clientId ?: "")))
                    .toByteArray(Charsets.UTF_8),
            )
        )
    }

    private suspend fun tokenRequest(http: HttpClient, cfg: OAuthConfig, params: Map<String, String>): TokenSet {
        val res = http.execute(
            HttpRequest(
                method = "POST",
                url = "${cfg.issuer}/oauth/token",
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Accept" to "application/json",
                ),
                body = formEncode(params).toByteArray(Charsets.UTF_8),
            )
        )
        if (res.status !in 200..299) throw oauthError(res)
        val b = Json.parse(res.text) as? Map<*, *> ?: throw oauthError(res)
        val access = b["access_token"] as? String ?: throw oauthError(res)
        val expiresIn = (b["expires_in"] as? Number)?.toLong() ?: 3600L
        return TokenSet(
            accessToken = access,
            refreshToken = b["refresh_token"] as? String,
            // 30s safety margin so we refresh just before the server expires it.
            expiresAt = System.currentTimeMillis() + expiresIn * 1000 - 30_000,
            tokenType = (b["token_type"] as? String) ?: "Bearer",
        )
    }

    private fun oauthError(res: HttpResponse): MailKiteException {
        var detail = "HTTP ${res.status}"
        try {
            val j = Json.parse(res.text) as? Map<*, *>
            detail = (j?.get("error_description") as? String) ?: (j?.get("error") as? String) ?: detail
        } catch (_: Exception) {}
        return MailKiteException(res.status, "OAuth ${res.status}: $detail", null)
    }

    fun formEncode(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" }

    fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
