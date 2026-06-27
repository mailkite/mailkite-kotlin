// Docs: docs/architecture/client-side-oauth-libraries.md
//
// Pluggable HTTP layer. The SDK talks to this interface only, so unit tests stub
// it on a plain JVM (no network, no Android). The default implementation uses
// java.net.HttpURLConnection — no heavy dependency, and it works on Android too.

package dev.mailkite.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** One HTTP request. `body` is null for GET/DELETE without a payload. */
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

/** One HTTP response. `headers` keys are lowercased for case-insensitive lookups. */
data class HttpResponse(
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
) {
    val text: String get() = String(body, Charsets.UTF_8)
    fun header(name: String): String? = headers[name.lowercase()]
}

/** The transport the client core depends on. Swap it in tests or to use OkHttp. */
interface HttpClient {
    suspend fun execute(request: HttpRequest): HttpResponse
}

/** Default transport built on HttpURLConnection. Blocking work runs on Dispatchers.IO. */
class UrlConnectionHttpClient : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val conn = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = false
            for ((k, v) in request.headers) setRequestProperty(k, v)
            if (request.body != null) {
                doOutput = true
                // setFixedLengthStreamingMode avoids buffering the whole body.
                setFixedLengthStreamingMode(request.body.size)
            }
        }
        try {
            request.body?.let { conn.outputStream.use { os -> os.write(it) } }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val bytes = stream?.use { readAll(it) } ?: ByteArray(0)
            val headers = LinkedHashMap<String, String>()
            for ((k, vs) in conn.headerFields) {
                if (k != null && vs.isNotEmpty()) headers[k.lowercase()] = vs.last()
            }
            HttpResponse(status, headers, bytes)
        } finally {
            conn.disconnect()
        }
    }

    private fun readAll(stream: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
