# `setWebhook`

Set or replace the domain's catch-all webhook.

**HTTP:** `PUT /api/domains/{id}/webhook`

## Parameters

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `url` | string | ✓ |  |

## Returns

`any`

## Example

```kotlin
val res = mk.setWebhook(mapOf("id" to "dom_1", "url" to "https://app.com/hooks/mailkite"))
```

---

[← All methods](../README.md#api-methods) · [Docs](https://mailkite.dev/docs) · [mailkite.dev](https://mailkite.dev)
