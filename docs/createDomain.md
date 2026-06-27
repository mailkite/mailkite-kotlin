# `createDomain`

Add a domain. Returns the domain + DNS records.

**HTTP:** `POST /api/domains`

## Parameters

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `domain` | string | ✓ |  |

## Returns

`any`

## Example

```kotlin
val res = mk.createDomain(mapOf("domain" to "app.mailkite.dev"))
```

---

[← All methods](../README.md#api-methods) · [Docs](https://mailkite.dev/docs) · [mailkite.dev](https://mailkite.dev)
