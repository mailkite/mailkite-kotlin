// Docs: docs/architecture/client-side-oauth-libraries.md
//
// Raised when the API returns a non-2xx response (or a request fails locally).

package dev.mailkite.client

/**
 * Error surface for every MailKite call.
 *
 * @property status  HTTP status (or 0 for a local/transport failure).
 * @property body    Parsed response body when available (Map/List/String), else null.
 * @property retryAfter  Seconds to wait before retrying, parsed from a 429 `Retry-After` header.
 */
class MailKiteException(
    val status: Int,
    message: String,
    val body: Any? = null,
    val retryAfter: Int? = null,
) : RuntimeException(message)
