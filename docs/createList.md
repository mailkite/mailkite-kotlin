# `createList`

Create a contact list. Returns the list with its id (lst_…); add contacts with addListContacts.

**HTTP:** `POST /api/lists`

## Parameters

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | string | ✓ |  |

## Returns

`any`

## Example

```kotlin
val res = mk.createList(mapOf("name" to "Beta testers"))
```

---

[← All methods](../README.md#api-methods) · [Docs](https://mailkite.dev/docs) · [mailkite.dev](https://mailkite.dev)
