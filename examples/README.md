# MailKite examples — Android / Kotlin

Runnable, copy-pasteable snippets. Each file's header comment lists what to set
(client id, the `mailkiteRedirectScheme` manifest placeholder) and the Gradle dependency.

| File | What it shows |
| --- | --- |
| [`LoginAndSend.kt`](LoginAndSend.kt) | **Client-side login + send** — the end user signs into their own MailKite account via Chrome Custom Tabs (`mk.login(activity)`), then `mk.send(mapOf(...))` |

Full docs: <https://mailkite.dev/docs> · Library guide: <https://mailkite.dev/docs/libraries>
