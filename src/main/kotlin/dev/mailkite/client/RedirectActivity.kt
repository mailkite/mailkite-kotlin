// Docs: docs/architecture/client-side-oauth-libraries.md
//
// Catches the OAuth redirect back from the Custom Tab. The intent-filter in the
// manifest (scheme = ${mailkiteRedirectScheme}, host = callback) routes
// mailkite-<clientid>://callback?code=…&state=… here; we hand the full URI to the
// waiting login coroutine via MailKiteAuthBridge and finish immediately.

package dev.mailkite.client

import android.app.Activity
import android.os.Bundle

class RedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri != null) {
            MailKiteAuthBridge.deliver(uri.toString())
        } else {
            MailKiteAuthBridge.cancel("No redirect data")
        }
        finish()
    }
}
