# `createRoute`

Create a route (match, action, destination).

**HTTP:** `POST /api/routes`

## Parameters

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `match` | string | ✓ | Address pattern: exact, *@domain, addr+*@domain, or /regex/. |
| `action` | string |  | What to do with matching mail. Defaults to webhook. |
| `destination` | string |  | Required for action webhook (URL) or forward (address). |
| `agentPrompt` | string |  | Required for action agent — instructions for the inbox agent. |

## Returns

`any`

## Example

```kotlin
val res = mk.createRoute(mapOf("match" to "*@app.mailkite.dev", "action" to "webhook", "destination" to "https://app.com/hooks"))
```

---

[← All methods](../README.md#api-methods) · [Docs](https://mailkite.dev/docs) · [mailkite.dev](https://mailkite.dev)
