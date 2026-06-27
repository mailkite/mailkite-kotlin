# `route`

Route a message to one of your registered routes (by `routeId` or `address`), running that route's action — agent, webhook, or forward. The route must already exist on your account; arbitrary destinations are not allowed.

**HTTP:** `POST /v1/route`

## Parameters

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `routeId` | string |  | Target route by id (rte_…). One of routeId or address is required. The route must already… |
| `address` | string |  | Target route by the address it matches. One of routeId or address is required. |
| `from` | string | ✓ | Sender address recorded on the message. |
| `subject` | string |  | Optional subject line. |
| `text` | string |  | Plain-text body. |
| `html` | string |  | HTML body. |

## Returns

`route-response` — see the [`route-response`](https://mailkite.dev/docs/api-reference) schema.

## Example

```kotlin
val res = mk.route(mapOf("routeId" to "rte_1", "from" to "ops@example.com", "subject" to "Process this", "text" to "Please handle."))
```

---

[← All methods](../README.md#api-methods) · [Docs](https://mailkite.dev/docs) · [mailkite.dev](https://mailkite.dev)
