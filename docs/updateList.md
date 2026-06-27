# `updateList`

Rename a contact list.

**HTTP:** `PATCH /api/lists/{id}`

## Parameters

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | string | ✓ |  |

## Returns

`any`

## Example

```kotlin
val res = mk.updateList(mapOf("id" to "lst_1", "name" to "VIPs"))
```

---

[← All methods](../README.md#api-methods) · [Docs](https://mailkite.dev/docs) · [mailkite.dev](https://mailkite.dev)
