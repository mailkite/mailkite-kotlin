// Docs: docs/architecture/client-side-oauth-libraries.md
//
// The Android login surface: open the /oauth/authorize URL in a Chrome Custom Tab
// and suspend until RedirectActivity catches the mailkite-<clientid>://callback
// redirect. This is the lightweight AppAuth-style pattern implemented directly on
// androidx.browser (one small dependency) — no net.openid:appauth needed.
//
// Android-only — excluded from the pure-JVM unit tests.

package dev.mailkite.client

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CompletableDeferred

/** Bridges the redirect Activity back to the suspended login() call. */
internal object MailKiteAuthBridge {
    @Volatile private var pending: CompletableDeferred<String>? = null

    fun await(): CompletableDeferred<String> =
        CompletableDeferred<String>().also { pending = it }

    fun deliver(redirectUrl: String) {
        pending?.complete(redirectUrl)
        pending = null
    }

    fun cancel(reason: String) {
        pending?.completeExceptionally(MailKiteException(0, "Login cancelled: $reason"))
        pending = null
    }
}

/**
 * One-call login: registers (if needed), opens the authorize URL in a Custom Tab,
 * waits for the redirect, verifies state, exchanges the code, and stores the
 * tokens. Call from a coroutine on the main scope:
 *
 * ```
 * lifecycleScope.launch { mk.login(this@MyActivity) }
 * ```
 */
suspend fun MailKiteClient.login(context: Context) {
    val authorizeUrl = beginLogin()
    val deferred = MailKiteAuthBridge.await()
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(authorizeUrl))
    val redirectUrl = deferred.await()
    completeLogin(redirectUrl)
}
