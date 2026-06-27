// Sign the end user into THEIR OWN MailKite account (OAuth 2.1 + PKCE via a Chrome
// Custom Tab) and send one email — set manifestPlaceholders["mailkiteRedirectScheme"]
// = "mailkite-<your-clientid>" in build.gradle so the SDK's RedirectActivity catches
// the mailkite-<clientid>://callback redirect (see ../README.md).
//
// Run: drop this into an Android app module. Replace cli_YOUR_CLIENT_ID with the
// OAuth client id you registered, and keep the manifestPlaceholders scheme in sync.
//
//   // app/build.gradle(.kts)
//   android { defaultConfig {
//       manifestPlaceholders["mailkiteRedirectScheme"] = "mailkite-cli_YOUR_CLIENT_ID"
//   } }
//
//   dependencies { implementation("dev.mailkite:mailkite-client:0.1.0") }

package dev.mailkite.examples

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.mailkite.client.EncryptedTokenStore
import dev.mailkite.client.MailKiteClient
import dev.mailkite.client.login
import kotlinx.coroutines.launch

class LoginAndSendActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The token persists (encrypted) across restarts. The clientId drives the
        // default redirect URI: mailkite-<clientid>://callback.
        val mk = MailKiteClient(
            clientId = "cli_YOUR_CLIENT_ID",
            store = EncryptedTokenStore(this),
        )

        lifecycleScope.launch {
            // Opens a Custom Tab; the user signs into their own MailKite account.
            // Suspends until the redirect comes back, then stores the tokens.
            if (!mk.isAuthenticated()) mk.login(this@LoginAndSendActivity)

            // Now drive the same API the user's dashboard does — as that user.
            val res = mk.send(mapOf(
                "from" to "you@yourdomain.com",
                "to" to "x@example.com",
                "subject" to "Hello from Android",
                "text" to "Sent with dev.mailkite:mailkite-client.",
            ))
            println("Sent: $res")
        }
    }
}
