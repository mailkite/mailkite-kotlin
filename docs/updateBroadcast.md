# `updateBroadcast`

Edit a draft broadcast (any of from/subject/audience/html/… ). Drafts only.

**HTTP:** `PATCH /api/broadcasts/{id}`

## Parameters

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | string |  |  |
| `from` | string |  |  |
| `replyTo` | string |  |  |
| `subject` | string |  |  |
| `preview` | string |  |  |
| `audience` | object |  |  |
| `templateId` | string |  |  |
| `html` | string |  |  |
| `text` | string |  |  |
| `footerAddress` | string |  |  |

## Returns

`any`

## Example

```kotlin
val res = mk.updateBroadcast(mapOf("id" to "bct_1", "subject" to "Launch week (final)"))
```

---

[← All methods](../README.md#api-methods) · [Docs](https://mailkite.dev/docs) · [mailkite.dev](https://mailkite.dev)
