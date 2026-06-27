// Docs: docs/architecture/client-side-oauth-libraries.md
//
// Pure-JVM unit tests for the MailKite client core — no Android, no emulator.
// They stub the HttpClient, so PKCE math, request building, error/429 mapping,
// the login flow, webhook verification and reply strings are all exercised on a
// plain JVM. Run: scripts/run-jvm-tests.sh (or ./gradlew test in Android Studio).

package dev.mailkite.client

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Records outgoing requests and replies from a handler — the JVM stand-in for fetch. */
private class StubHttp(
    val handler: (HttpRequest) -> StubResponse,
) : HttpClient {
    val calls = mutableListOf<HttpRequest>()
    override suspend fun execute(request: HttpRequest): HttpResponse {
        calls.add(request)
        val r = handler(request)
        return HttpResponse(r.status, r.headers.mapKeys { it.key.lowercase() }, r.body.toByteArray(Charsets.UTF_8))
    }
}

private data class StubResponse(
    val status: Int = 200,
    val body: String = "",
    val headers: Map<String, String> = emptyMap(),
)

private fun json(s: String) = StubResponse(body = s)

class MailKiteClientTest {

    @Test
    fun `PKCE challenge is base64url(SHA-256(verifier))`() {
        val p = OAuth.createPkce()
        val digest = MessageDigest.getInstance("SHA-256").digest(p.verifier.toByteArray(Charsets.US_ASCII))
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        assertEquals(expected, p.challenge)
        assertTrue(p.verifier.matches(Regex("^[A-Za-z0-9\\-_]+$")))
        assertTrue(p.state.isNotEmpty())
    }

    @Test
    fun `request sends Bearer token, verb, path and JSON body`() = runBlocking {
        val http = StubHttp { json("""{"id":"msg_1","status":"queued"}""") }
        val mk = MailKiteClient(staticToken = "test_token", http = http)
        val out = mk.send(mapOf("from" to "a@app.mailkite.dev", "to" to "b@example.com", "subject" to "Hi", "text" to "yo"))
        @Suppress("UNCHECKED_CAST")
        val m = out as Map<String, Any?>
        assertEquals("msg_1", m["id"])
        assertEquals("queued", m["status"])
        assertEquals(1, http.calls.size)
        assertEquals("POST", http.calls[0].method)
        assertEquals("https://api.mailkite.dev/v1/send", http.calls[0].url)
        assertEquals("Bearer test_token", http.calls[0].headers["Authorization"])
        val sent = Json.parse(String(http.calls[0].body!!, Charsets.UTF_8)) as Map<*, *>
        assertEquals("b@example.com", sent["to"])
    }

    @Test
    fun `path and query params are URL-encoded`() = runBlocking {
        val http = StubHttp { json("""{"ok":true}""") }
        val mk = MailKiteClient(staticToken = "t", http = http)
        mk.getDomain("dom 1")
        mk.checkDomainAvailability("a b.com")
        assertEquals("https://api.mailkite.dev/api/domains/dom%201", http.calls[0].url)
        assertEquals("https://api.mailkite.dev/api/domains/register/check?domain=a%20b.com", http.calls[1].url)
    }

    @Test
    fun `non-2xx throws MailKiteException with status and message`() {
        val http = StubHttp { StubResponse(status = 422, body = """{"error":"bad domain"}""") }
        val mk = MailKiteClient(staticToken = "t", http = http)
        val ex = assertThrows(MailKiteException::class.java) {
            runBlocking { mk.createDomain(mapOf("domain" to "x")) }
        }
        assertEquals(422, ex.status)
        assertEquals("bad domain", ex.message)
    }

    @Test
    fun `429 surfaces Retry-After seconds`() {
        val http = StubHttp { StubResponse(status = 429, body = """{"error":"slow down"}""", headers = mapOf("Retry-After" to "30")) }
        val mk = MailKiteClient(staticToken = "t", http = http)
        val ex = assertThrows(MailKiteException::class.java) {
            runBlocking { mk.listMessages() }
        }
        assertEquals(429, ex.status)
        assertEquals(30, ex.retryAfter)
    }

    @Test
    fun `uploadAttachment remote url goes up as JSON`() = runBlocking {
        val http = StubHttp { json("""{"url":"https://files.mailkite.dev/x"}""") }
        val mk = MailKiteClient(staticToken = "t", http = http)
        mk.uploadAttachment(url = "https://example.com/a.pdf", filename = "a.pdf")
        assertEquals("https://api.mailkite.dev/v1/attachments", http.calls[0].url)
        val sent = Json.parse(String(http.calls[0].body!!, Charsets.UTF_8)) as Map<*, *>
        assertEquals("https://example.com/a.pdf", sent["url"])
        assertEquals("a.pdf", sent["filename"])
    }

    @Test
    fun `uploadAttachment raw bytes go up as binary with filename query`() = runBlocking {
        val http = StubHttp { json("""{"url":"https://files.mailkite.dev/y"}""") }
        val mk = MailKiteClient(staticToken = "t", http = http)
        mk.uploadAttachment(bytes = byteArrayOf(1, 2, 3), filename = "x y.bin", retentionDays = 30)
        assertEquals("https://api.mailkite.dev/v1/attachments?filename=x%20y.bin&retentionDays=30", http.calls[0].url)
        assertEquals("application/octet-stream", http.calls[0].headers["Content-Type"])
        assertEquals(3, http.calls[0].body!!.size)
    }

    @Test
    fun `verifyWebhook accepts a valid signature and rejects tampering or staleness`() {
        val secret = "whsec_test"
        val payload = """{"event":"inbound","id":"msg_9"}"""
        val t = System.currentTimeMillis()
        val v1 = hmacHex(secret, "$t.$payload")
        val sig = "t=$t,v1=$v1"
        assertTrue(Webhook.verify(sig, payload, secret))
        assertFalse(Webhook.verify(sig, payload + "x", secret))            // tampered body
        assertFalse(Webhook.verify(sig, payload, "wrong"))                  // wrong secret
        assertFalse(Webhook.verify("t=${t - 10 * 60 * 1000},v1=$v1", payload, secret)) // stale
        assertFalse(Webhook.verify(null, payload, secret))                  // missing
        assertFalse(Webhook.verify("garbage", payload, secret))             // malformed
    }

    @Test
    fun `reply helpers are the canonical control strings`() {
        assertEquals("{\"status\":\"ok\"}", Webhook.replyOk())
        assertEquals("{\"status\":\"spam\"}", Webhook.replySpam())
        assertEquals("{\"status\":\"drop\"}", Webhook.replyDrop())
        assertEquals("{\"status\":\"ok\",\"actions\":[{\"type\":\"block-sender\"}]}", Webhook.replyBlockSender())
    }

    @Test
    fun `login flow registers a client, builds authorize URL, exchanges code`() = runBlocking {
        val http = StubHttp { req ->
            when {
                req.url.endsWith("/oauth/register") -> json("""{"client_id":"cli_123"}""")
                req.url.endsWith("/oauth/token") -> json("""{"access_token":"at_1","refresh_token":"rt_1","expires_in":3600,"token_type":"Bearer"}""")
                else -> json("{}")
            }
        }
        val mk = MailKiteClient(redirectUri = "mailkite-cli://callback", http = http, store = MemoryTokenStore())
        val url = mk.beginLogin()
        assertTrue(url.startsWith("https://mcp.mailkite.dev/oauth/authorize?"))
        val params = parseCallback(url)
        assertEquals("S256", params["code_challenge_method"])
        assertEquals("cli_123", params["client_id"])
        assertEquals("cli_123", mk.clientId)
        val state = params["state"]!!
        mk.completeLogin("mailkite-cli://callback?code=auth_code&state=$state")
        assertTrue(mk.isAuthenticated())
        assertTrue(http.calls.any { it.url.endsWith("/oauth/token") })
    }

    @Test
    fun `completeLogin rejects a state mismatch (CSRF guard)`() = runBlocking {
        val http = StubHttp { req ->
            if (req.url.endsWith("/oauth/register")) json("""{"client_id":"c"}""") else json("{}")
        }
        val mk = MailKiteClient(redirectUri = "mailkite-c://callback", http = http)
        mk.beginLogin()
        val ex = assertThrows(MailKiteException::class.java) {
            runBlocking { mk.completeLogin("mailkite-c://callback?code=x&state=WRONG") }
        }
        assertTrue(ex.message!!.contains("State mismatch"))
    }

    @Test
    fun `expired access token triggers a refresh before the call`() = runBlocking {
        val store = MemoryTokenStore().apply {
            save(TokenSet(accessToken = "old", refreshToken = "rt_old", expiresAt = 0L)) // already expired
        }
        val http = StubHttp { req ->
            when {
                req.url.endsWith("/oauth/token") -> json("""{"access_token":"fresh","refresh_token":"rt_new","expires_in":3600}""")
                else -> json("""{"ok":true}""")
            }
        }
        val mk = MailKiteClient(clientId = "cli_x", http = http, store = store)
        mk.listMessages()
        // token endpoint hit first (refresh), then the API call carries the fresh token.
        assertTrue(http.calls.any { it.url.endsWith("/oauth/token") })
        val apiCall = http.calls.last { it.url.endsWith("/api/messages") }
        assertEquals("Bearer fresh", apiCall.headers["Authorization"])
        assertEquals("fresh", store.load()!!.accessToken)
    }

    @Test
    fun `401 refreshes and retries the request once`() = runBlocking {
        val store = MemoryTokenStore().apply {
            save(TokenSet(accessToken = "stale", refreshToken = "rt", expiresAt = Long.MAX_VALUE))
        }
        var apiHits = 0
        val http = StubHttp { req ->
            when {
                req.url.endsWith("/oauth/token") -> json("""{"access_token":"renewed","expires_in":3600}""")
                req.url.endsWith("/api/domains") -> {
                    apiHits++
                    if (apiHits == 1) StubResponse(status = 401, body = """{"error":"expired"}""")
                    else json("""[{"id":"dom_1"}]""")
                }
                else -> json("{}")
            }
        }
        val mk = MailKiteClient(clientId = "cli_x", http = http, store = store)
        val out = mk.listDomains()
        assertNotNull(out)
        assertEquals(2, apiHits) // first 401, retried after refresh
        assertTrue(http.calls.any { it.url.endsWith("/oauth/token") })
    }

    // --- test helper ---
    private fun hmacHex(secret: String, msg: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(msg.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
