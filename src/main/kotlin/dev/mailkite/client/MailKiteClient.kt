// Docs: docs/architecture/client-side-oauth-libraries.md
//
// dev.mailkite:mailkite-client — the client-side MailKite SDK. A user signs into
// their own MailKite account via OAuth 2.1 + PKCE (the token IS the user, same as
// a dashboard login), then drives the same API methods the server SDKs expose.
//
// This core is pure JVM/Kotlin (no Android imports), so the transport, OAuth/PKCE
// and webhook logic are unit-tested without an emulator. The Android pieces —
// Custom Tabs login and the EncryptedSharedPreferences token store — live in
// AndroidAuth.kt / AndroidTokenStore.kt and only add the native surface.

package dev.mailkite.client

/**
 * MailKite client. Construct with a `redirectUri`/`clientId` for the OAuth login
 * flow, or with [withToken] to drive the API from a token you already hold.
 *
 * All API methods are `suspend` — call them from a coroutine.
 */
class MailKiteClient(
    redirectUri: String? = null,
    clientId: String? = null,
    baseUrl: String = DEFAULT_BASE_URL,
    issuer: String = DEFAULT_ISSUER,
    scope: String? = null,
    private val store: TokenStore = MemoryTokenStore(),
    private val staticToken: String? = null,
    private val http: HttpClient = UrlConnectionHttpClient(),
) : GeneratedMethods() {

    private val baseUrl: String = baseUrl.trimEnd('/')

    // Default the custom-scheme redirect from the client id when not given.
    private val oauth = OAuthConfig(
        issuer = issuer.trimEnd('/'),
        clientId = clientId,
        redirectUri = redirectUri ?: (clientId?.let { "mailkite-$it://callback" } ?: ""),
        scope = scope,
    )

    /** Transient PKCE/state held between the authorize redirect and the exchange. */
    private data class PkceStash(val pkce: Pkce, val clientId: String)
    @Volatile private var stash: PkceStash? = null

    companion object {
        const val DEFAULT_BASE_URL = "https://api.mailkite.dev"
        const val DEFAULT_ISSUER = "https://mcp.mailkite.dev"

        /** Construct a client from a token you already hold (API key or access token). */
        @JvmStatic
        fun withToken(token: String, baseUrl: String = DEFAULT_BASE_URL): MailKiteClient =
            MailKiteClient(baseUrl = baseUrl, staticToken = token)
    }

    // --- Auth -----------------------------------------------------------------

    /** The OAuth client id in use (after registration), or null before login. */
    val clientId: String? get() = oauth.clientId

    /** The redirect URI this client authorizes with (custom scheme on Android). */
    val redirectUri: String get() = oauth.redirectUri

    /**
     * Step 1 of the login flow: returns the `/oauth/authorize` URL to open in a
     * Custom Tab. Registers a client id on first use (unless one was supplied).
     */
    suspend fun beginLogin(): String {
        if (oauth.redirectUri.isEmpty()) throw IllegalStateException("redirectUri (or clientId) is required to log in")
        if (oauth.clientId == null) oauth.clientId = OAuth.registerClient(http, oauth)
        val pkce = OAuth.createPkce()
        stash = PkceStash(pkce, oauth.clientId!!)
        return OAuth.authorizeUrl(oauth, pkce)
    }

    /**
     * Step 2 of the login flow: pass the full redirect URL the browser came back
     * with (`mailkite-<clientid>://callback?code=…&state=…`). Verifies state,
     * exchanges the code, and stores the tokens.
     */
    suspend fun completeLogin(redirectUrl: String) {
        val params = parseCallback(redirectUrl)
        params["error"]?.let { err ->
            throw MailKiteException(0, "Authorization failed: ${params["error_description"] ?: err}")
        }
        val code = params["code"]
        val state = params["state"]
        val s = stash ?: throw IllegalStateException("No login in progress (missing PKCE state)")
        if (code.isNullOrEmpty() || state.isNullOrEmpty() || state != s.pkce.state) {
            throw MailKiteException(0, "State mismatch — possible CSRF; aborting")
        }
        oauth.clientId = s.clientId
        val tokens = OAuth.exchangeCode(http, oauth, code, s.pkce.verifier)
        store.save(tokens)
        stash = null
    }

    /** Revoke the refresh token and clear local storage. */
    suspend fun logout() {
        val t = store.load()
        val rt = t?.refreshToken
        if (rt != null && oauth.clientId != null) {
            try { OAuth.revoke(http, oauth, rt) } catch (_: Exception) {}
        }
        store.clear()
    }

    fun isAuthenticated(): Boolean = staticToken != null || store.load() != null

    // --- Transport ------------------------------------------------------------

    /**
     * Low-level request behind every generated method. Injects the bearer token,
     * refreshes it when expired or on a 401 (retrying once), and surfaces a 429's
     * `Retry-After` on [MailKiteException.retryAfter].
     */
    override suspend fun request(method: String, path: String, body: Any?): Any? {
        val headers = LinkedHashMap<String, String>()
        var payload: ByteArray? = null
        if (body != null) {
            headers["Content-Type"] = "application/json"
            payload = Json.stringify(body).toByteArray(Charsets.UTF_8)
        }
        return dispatch(method, baseUrl + path, headers, payload, retried = false)
    }

    private suspend fun dispatch(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        retried: Boolean,
    ): Any? {
        val token = bearer()
        val h = LinkedHashMap(headers).apply {
            put("Accept", "application/json")
            if (token != null) put("Authorization", "Bearer $token")
        }
        val res = http.execute(HttpRequest(method, url, h, body))

        if (res.status == 401 && !retried && staticToken == null && tryRefresh()) {
            return dispatch(method, url, headers, body, retried = true)
        }
        return handle(res)
    }

    private fun handle(res: HttpResponse): Any? {
        val text = res.text
        val data = if (text.isEmpty()) null else safeJson(text)
        if (res.status !in 200..299) {
            val message = ((data as? Map<*, *>)?.get("error") as? String) ?: "HTTP ${res.status}"
            val retryAfter = if (res.status == 429) res.header("Retry-After")?.trim()?.toIntOrNull() else null
            throw MailKiteException(res.status, message, data, retryAfter)
        }
        return data
    }

    /** Current valid access token, refreshing if expired. */
    private suspend fun bearer(): String? {
        if (staticToken != null) return staticToken
        var t = store.load()
        if (t != null && t.expiresAt <= System.currentTimeMillis()) {
            tryRefresh()
            t = store.load()
        }
        return t?.accessToken
    }

    private suspend fun tryRefresh(): Boolean {
        val t = store.load()
        val rt = t?.refreshToken
        if (rt == null || oauth.clientId == null) return false
        return try {
            store.save(OAuth.refresh(http, oauth, rt))
            true
        } catch (_: Exception) {
            store.clear()
            false
        }
    }

    // --- Hand-written methods (special transport / local crypto) --------------

    /**
     * Upload a file and get back a time-limited URL to reference in [send] as an
     * attachment (`{ filename, url }`). Supply the file ONE of three ways:
     *  - [url]: a remote URL MailKite fetches and re-hosts (JSON POST);
     *  - [bytes]: raw file bytes (binary POST to `/v1/attachments?filename=&retentionDays=`);
     *  - [content]: a base64 string (JSON POST).
     * [retentionDays] (7/30/90/365, default 7 server-side) sets how long the URL lives.
     */
    suspend fun uploadAttachment(
        url: String? = null,
        bytes: ByteArray? = null,
        content: String? = null,
        filename: String? = null,
        contentType: String? = null,
        retentionDays: Int? = null,
    ): Any? {
        if (url != null) {
            return request("POST", "/v1/attachments", trim(linkedMapOf(
                "url" to url, "filename" to filename,
                "contentType" to contentType, "retentionDays" to retentionDays,
            )))
        }
        if (bytes != null) {
            val q = StringBuilder("/v1/attachments?filename=")
                .append(OAuth.urlEncode(filename ?: "file"))
            if (retentionDays != null) q.append("&retentionDays=").append(retentionDays)
            val headers = linkedMapOf("Content-Type" to (contentType ?: "application/octet-stream"))
            return dispatch("POST", baseUrl + q.toString(), headers, bytes, retried = false)
        }
        if (content != null) {
            return request("POST", "/v1/attachments", trim(linkedMapOf(
                "content" to content, "filename" to filename,
                "contentType" to contentType, "retentionDays" to retentionDays,
            )))
        }
        throw MailKiteException(0, "uploadAttachment needs one of: url, bytes, or content")
    }

    /**
     * Verify an inbound webhook's `x-mailkite-signature` header (HMAC-SHA256).
     * Local — no network, no token. Delegates to [Webhook.verify].
     */
    @JvmOverloads
    fun verifyWebhook(signature: String?, payload: String, secret: String, toleranceMs: Long = DEFAULT_TOLERANCE_MS): Boolean =
        Webhook.verify(signature, payload, secret, toleranceMs)

    fun replyOk(): String = Webhook.replyOk()
    fun replySpam(): String = Webhook.replySpam()
    fun replyDrop(): String = Webhook.replyDrop()
    fun replyBlockSender(): String = Webhook.replyBlockSender()
}

// --- internal helpers ---------------------------------------------------------

/** Parse `code`/`state`/`error` from a redirect URL or bare query string. */
internal fun parseCallback(redirectUrlOrQuery: String): Map<String, String> {
    val q = if (redirectUrlOrQuery.contains('?'))
        redirectUrlOrQuery.substringAfter('?')
    else redirectUrlOrQuery.removePrefix("?")
    val out = LinkedHashMap<String, String>()
    if (q.isEmpty()) return out
    for (pair in q.substringBefore('#').split("&")) {
        if (pair.isEmpty()) continue
        val idx = pair.indexOf('=')
        val k = if (idx < 0) pair else pair.substring(0, idx)
        val v = if (idx < 0) "" else pair.substring(idx + 1)
        out[urlDecode(k)] = urlDecode(v)
    }
    return out
}

private fun urlDecode(s: String): String =
    try { java.net.URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }

private fun safeJson(text: String): Any? =
    try { Json.parse(text) } catch (_: Exception) { text }

private fun trim(map: Map<String, Any?>): Map<String, Any?> {
    val out = LinkedHashMap<String, Any?>()
    for ((k, v) in map) if (v != null) out[k] = v
    return out
}
