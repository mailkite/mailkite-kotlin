// Docs: docs/architecture/client-side-oauth-libraries.md
//
// Local webhook signature verification + the canonical reply strings. No network
// call, no token — pure JVM (javax.crypto.Mac), so it runs in unit tests and can
// be used standalone (without a logged-in client).

package dev.mailkite.client

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Reject webhook events older than this (ms) to block replays. Pass 0 to disable. */
const val DEFAULT_TOLERANCE_MS: Long = 5 * 60 * 1000L

object Webhook {

    /**
     * Verify the `x-mailkite-signature` header on an inbound webhook delivery.
     * The header format is `t=<ms>,v1=<hex>`; the MAC is HMAC-SHA256 over
     * `"<t>." + payload` with your webhook secret. Returns true only for a fresh,
     * matching signature.
     *
     * @param payload the raw, unparsed request body (as received).
     * @param toleranceMs reject events older than this many ms (0 disables the check).
     */
    @JvmStatic
    @JvmOverloads
    fun verify(
        signature: String?,
        payload: String,
        secret: String,
        toleranceMs: Long = DEFAULT_TOLERANCE_MS,
    ): Boolean {
        if (signature.isNullOrEmpty()) return false

        var t: String? = null
        var v1: String? = null
        for (seg in signature.split(",")) {
            val idx = seg.indexOf('=')
            if (idx < 0) continue
            val k = seg.substring(0, idx).trim()
            val v = seg.substring(idx + 1).trim()
            when (k) {
                "t" -> t = v
                "v1" -> v1 = v
            }
        }
        if (t == null || v1.isNullOrEmpty() || !t.matches(Regex("-?\\d+"))) return false

        val ts = t.toLongOrNull() ?: return false
        // The t in the header is milliseconds since the epoch.
        if (toleranceMs > 0 && Math.abs(System.currentTimeMillis() - ts) > toleranceMs) return false

        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            val raw = mac.doFinal("$t.$payload".toByteArray(StandardCharsets.UTF_8))
            val hex = StringBuilder(raw.size * 2)
            for (b in raw) {
                hex.append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
                hex.append(Character.forDigit(b.toInt() and 0xF, 16))
            }
            // MessageDigest.isEqual is constant-time on modern JVMs.
            MessageDigest.isEqual(
                hex.toString().toByteArray(StandardCharsets.UTF_8),
                v1.toByteArray(StandardCharsets.UTF_8),
            )
        } catch (_: java.security.GeneralSecurityException) {
            false
        }
    }

    // --- canonical webhook reply bodies --------------------------------------

    /** The 200 body confirming the event was processed: `{"status":"ok"}`. */
    @JvmStatic fun replyOk(): String = "{\"status\":\"ok\"}"

    /** Tell MailKite to mark the message as spam: `{"status":"spam"}`. */
    @JvmStatic fun replySpam(): String = "{\"status\":\"spam\"}"

    /** Tell MailKite to drop (discard) the message: `{"status":"drop"}`. */
    @JvmStatic fun replyDrop(): String = "{\"status\":\"drop\"}"

    /** Tell MailKite to block the sender: `{"status":"ok","actions":[{"type":"block-sender"}]}`. */
    @JvmStatic fun replyBlockSender(): String = "{\"status\":\"ok\",\"actions\":[{\"type\":\"block-sender\"}]}"
}
