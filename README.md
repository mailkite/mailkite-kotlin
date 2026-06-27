# dev.mailkite:mailkite-client (Android / Kotlin)

Client-side MailKite SDK for Android. Instead of a server API key, your user
**signs into their own MailKite account** with OAuth 2.1 + PKCE (Google / email —
whatever the dashboard supports), and the library gets a short-lived token that
**is** that user. The token then drives the same API methods every other SDK
exposes.

> **Read-only mirror.** This repo is a generated, release-time mirror of the MailKite
> monorepo (the private source of truth); the source isn't developed here. Depend on
> `dev.mailkite:mailkite-client` from Maven Central rather than cloning. Full docs:
> <https://mailkite.dev/docs/libraries>.
>
> The method surface is generated from the shared MailKite contract — same as every other SDK.

- **minSdk 24+**, Kotlin coroutines (every API call is a `suspend` fun).
- **OAuth via Chrome Custom Tabs** — implemented directly on `androidx.browser`
  (the lightweight AppAuth-style pattern), so there is **no `net.openid:appauth`
  dependency**.
- **Tokens in EncryptedSharedPreferences** (`androidx.security:security-crypto`),
  behind a `TokenStore` interface with an in-memory impl for tests.
- **HTTP via `HttpURLConnection`** (no OkHttp dependency) behind a pluggable
  `HttpClient` interface — swap in OkHttp or a stub if you prefer.

## Install

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}

// app/build.gradle.kts
dependencies {
    implementation("dev.mailkite:mailkite-client:0.1.0")
}
```

### Wire up the OAuth redirect

On Android the redirect comes back to a custom scheme, `mailkite-<clientid>://callback`.
Register a **public client id** once (dashboard or `POST /oauth/register`) and set
the matching scheme so the bundled `RedirectActivity` catches it:

```kotlin
// app/build.gradle.kts → android { defaultConfig { … } }
manifestPlaceholders["mailkiteRedirectScheme"] = "mailkite-cli_abc123"
```

> Prefer a pre-provisioned `clientId` on Android (like AppAuth): the manifest
> scheme is static, so it must match the client id. Dynamic registration still
> works, but then you must set the scheme to the id you get back.

## Log in and send

```kotlin
import dev.mailkite.client.MailKiteClient
import dev.mailkite.client.EncryptedTokenStore
import dev.mailkite.client.login

class MainActivity : AppCompatActivity() {
    private val mk by lazy {
        MailKiteClient(
            clientId = "cli_abc123",                 // matches the manifest scheme
            store = EncryptedTokenStore(this),        // survives restarts, encrypted at rest
        )
    }

    fun signIn() = lifecycleScope.launch {
        // Opens a Custom Tab, waits for the redirect, exchanges the code, stores tokens:
        mk.login(this@MainActivity)

        // Now call the API as that user — the token refreshes automatically:
        mk.send(mapOf(
            "from" to "you@yourdomain.com",
            "to" to "ada@example.com",
            "subject" to "Hi",
            "text" to "Sent from my Android app.",
        ))
        val domains = mk.listDomains()
    }

    fun signOut() = lifecycleScope.launch { mk.logout() } // revokes + clears
}
```

### Manual redirect flow (no `login()` helper)

```kotlin
val url = mk.beginLogin()          // returns the /oauth/authorize URL to open
// …open `url`, catch the redirect, then:
mk.completeLogin(redirectUrl)      // verifies state, exchanges the code, stores tokens
```

## Anything with a bearer token

```kotlin
val mk = MailKiteClient.withToken(accessTokenOrApiKey)
mk.listMessages()
```

## Webhooks (local, no network)

```kotlin
import dev.mailkite.client.Webhook

if (Webhook.verify(signatureHeader, rawBody, webhookSecret)) {
    // …process the event, then reply:
    return Webhook.replyOk()        // or replySpam() / replyDrop() / replyBlockSender()
}
```

## What you get

- The full API method surface (`send`, `uploadAttachment`, `agent`, `route`,
  `listDomains`, `createDomain`, `getDomain`, `verifyDomain`, `setWebhook`,
  `listRoutes`, `createRoute`, `listMessages`, … — generated from the spec).
- OAuth 2.1 + PKCE login (`beginLogin`/`completeLogin`/`login`/`logout`),
  automatic token refresh, `429`/`Retry-After` surfaced on `MailKiteException.retryAfter`.
- Local `Webhook.verify(signature, payload, secret)` + `replyOk/Spam/Drop/BlockSender`.
- Pluggable `TokenStore` (`EncryptedTokenStore`, `MemoryTokenStore`, or your own)
  and `HttpClient`.

## Develop

```sh
node ../codegen.mjs            # regenerate GeneratedMethods.kt from the spec
scripts/run-jvm-tests.sh       # pure-JVM unit tests (no emulator) — needs `brew install kotlin`
./gradlew test                 # the same tests, in an Android/Gradle toolchain
```
