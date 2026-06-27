// Docs: docs/architecture/client-side-oauth-libraries.md
//
// Production token store backed by EncryptedSharedPreferences (AES-256, keyed by
// the Android Keystore). Android-only — excluded from the pure-JVM unit tests,
// which use MemoryTokenStore. Pass an instance as the `store` to MailKiteClient.

package dev.mailkite.client

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * [TokenStore] that persists the token set in [EncryptedSharedPreferences] so a
 * login survives app restarts and the refresh token never sits in plaintext.
 *
 * ```
 * val store = EncryptedTokenStore(context)
 * val mk = MailKiteClient(clientId = "cli_…", store = store)
 * ```
 */
class EncryptedTokenStore(
    context: Context,
    fileName: String = "mailkite.tokens",
    private val key: String = "tokens",
) : TokenStore {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun load(): TokenSet? = TokenJson.decode(prefs.getString(key, null))

    override fun save(tokens: TokenSet) {
        prefs.edit().putString(key, TokenJson.encode(tokens)).apply()
    }

    override fun clear() {
        prefs.edit().remove(key).apply()
    }
}
