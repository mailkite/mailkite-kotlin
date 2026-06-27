// AUTO-GENERATED from sdks/spec/api.json by sdks/clients/codegen.mjs — DO NOT EDIT.
// Docs: docs/architecture/client-side-oauth-libraries.md

package dev.mailkite.client

/** The transport every generated method calls. Implemented by MailKiteClient. */
interface Transport {
    suspend fun request(method: String, path: String, body: Any? = null): Any?
}

/** HTTP method surface, generated from the API spec. MailKiteClient extends this. */
abstract class GeneratedMethods : Transport {

    /** Send a message over a verified domain. Pass `templateId` (+ optional `templateData`) to send from a saved or base template. */
    suspend fun send(message: Map<String, Any?>): Any? =
        request("POST", "/v1/send", message)

    /** List your saved email templates (light metadata only — no body). Use getTemplate for the full template. */
    suspend fun listTemplates(): Any? =
        request("GET", "/api/templates")

    /** List the premade base templates (light metadata). Clone one with createTemplate({ baseId }) or send from it directly via send({ templateId }). */
    suspend fun listBaseTemplates(): Any? =
        request("GET", "/api/templates/base")

    /** Get one template (full: subject, html, text, theme). Works for your templates (tpl_…) and base templates (base_…). */
    suspend fun getTemplate(id: String): Any? =
        request("GET", "/api/templates/${enc(id)}")

    /** Create a template. Pass `baseId` to clone a base template into your own, or provide name/subject/html/text/theme directly. */
    suspend fun createTemplate(body: Map<String, Any?>): Any? =
        request("POST", "/api/templates", body)

    /** List your domains, each with its webhook URL. */
    suspend fun listDomains(): Any? =
        request("GET", "/api/domains")

    /** Add a domain. Returns the domain + DNS records. */
    suspend fun createDomain(body: Map<String, Any?>): Any? =
        request("POST", "/api/domains", body)

    /** Get one domain with DNS records + webhook. */
    suspend fun getDomain(id: String): Any? =
        request("GET", "/api/domains/${enc(id)}")

    /** Remove a domain. */
    suspend fun deleteDomain(id: String): Any? =
        request("DELETE", "/api/domains/${enc(id)}")

    /** Check DNS and update status. */
    suspend fun verifyDomain(id: String): Any? =
        request("POST", "/api/domains/${enc(id)}/verify")

    /** Set or replace the domain's catch-all webhook. */
    suspend fun setWebhook(id: String, body: Map<String, Any?>): Any? =
        request("PUT", "/api/domains/${enc(id)}/webhook", body)

    /** Remove the domain's webhook. */
    suspend fun deleteWebhook(id: String): Any? =
        request("DELETE", "/api/domains/${enc(id)}/webhook")

    /** Send a signed test event to the domain's webhook. */
    suspend fun testWebhook(id: String): Any? =
        request("POST", "/api/domains/${enc(id)}/webhook/test")

    /** Check whether a domain is available to register, and at what price. Read-only — no charge. */
    suspend fun checkDomainAvailability(domain: String): Any? =
        request("GET", "/api/domains/register/check?domain=${enc(domain)}")

    /** Register (buy) a domain on the customer's behalf; provisions mail DNS and adds it to the account in one call. Charges the registrar. */
    suspend fun registerDomain(body: Map<String, Any?>): Any? =
        request("POST", "/api/domains/register", body)

    /** List inbound routing rules. */
    suspend fun listRoutes(): Any? =
        request("GET", "/api/routes")

    /** Create a route (match, action, destination). */
    suspend fun createRoute(body: Map<String, Any?>): Any? =
        request("POST", "/api/routes", body)

    /** Send a message to one of your inbox agents and get its reply. Defaults to the account's default agent; pass `routeId` or `address` to target a specific agent, or `model` to override the model. This is separate from inbound routing — it does not match or override routes. */
    suspend fun agent(message: Map<String, Any?>): Any? =
        request("POST", "/v1/agent", message)

    /** Route a message to one of your registered routes (by `routeId` or `address`), running that route's action — agent, webhook, or forward. The route must already exist on your account; arbitrary destinations are not allowed. */
    suspend fun route(message: Map<String, Any?>): Any? =
        request("POST", "/v1/route", message)

    /** List stored messages. */
    suspend fun listMessages(): Any? =
        request("GET", "/api/messages")

    /** Get a message with deliveries + attachments. */
    suspend fun getMessage(id: String): Any? =
        request("GET", "/api/messages/${enc(id)}")

    /** Re-deliver a stored message to its webhook. */
    suspend fun retryDelivery(id: String): Any? =
        request("POST", "/api/deliveries/${enc(id)}/retry")

    /** List your contact lists (static, curated broadcast audiences), each with its member count. */
    suspend fun listLists(): Any? =
        request("GET", "/api/lists")

    /** Create a contact list. Returns the list with its id (lst_…); add contacts with addListContacts. */
    suspend fun createList(body: Map<String, Any?>): Any? =
        request("POST", "/api/lists", body)

    /** Get one contact list with its member count. */
    suspend fun getList(id: String): Any? =
        request("GET", "/api/lists/${enc(id)}")

    /** Rename a contact list. */
    suspend fun updateList(id: String, body: Map<String, Any?>): Any? =
        request("PATCH", "/api/lists/${enc(id)}", body)

    /** Delete a contact list. The list is removed; the contacts themselves are kept. */
    suspend fun deleteList(id: String): Any? =
        request("DELETE", "/api/lists/${enc(id)}")

    /** List the contacts that are members of a list. */
    suspend fun listListContacts(id: String): Any? =
        request("GET", "/api/lists/${enc(id)}/contacts")

    /** Add contacts (by id, ctr_…) to a list. Returns how many were newly added; contacts already on the list are ignored. */
    suspend fun addListContacts(id: String, body: Map<String, Any?>): Any? =
        request("POST", "/api/lists/${enc(id)}/contacts", body)

    /** Remove one contact from a list (the contact itself is kept). */
    suspend fun removeListContact(id: String, contactId: String): Any? =
        request("DELETE", "/api/lists/${enc(id)}/contacts/${enc(contactId)}")

    /** List your broadcasts (one-to-many sends) with status and send stats. */
    suspend fun listBroadcasts(): Any? =
        request("GET", "/api/broadcasts")

    /** Create a broadcast draft. `from` is required; set `audience` to { type: "all" } or { type: "list", id: "lst_…" }. Returns the broadcast with its id (bct_…). Send it with sendBroadcast. */
    suspend fun createBroadcast(body: Map<String, Any?>): Any? =
        request("POST", "/api/broadcasts", body)

    /** Get one broadcast with its status and recipient summary. */
    suspend fun getBroadcast(id: String): Any? =
        request("GET", "/api/broadcasts/${enc(id)}")

    /** Edit a draft broadcast (any of from/subject/audience/html/… ). Drafts only. */
    suspend fun updateBroadcast(id: String, body: Map<String, Any?>): Any? =
        request("PATCH", "/api/broadcasts/${enc(id)}", body)

    /** Delete a broadcast draft. */
    suspend fun deleteBroadcast(id: String): Any? =
        request("DELETE", "/api/broadcasts/${enc(id)}")

    /** Send a broadcast now, or pass an ISO 8601 `scheduledAt` to schedule it. A one-click unsubscribe is always added. Returns the status and resolved audience count. */
    suspend fun sendBroadcast(id: String, body: Map<String, Any?>): Any? =
        request("POST", "/api/broadcasts/${enc(id)}/send", body)
}

/** URL-encode a single path/query value (encodeURIComponent-equivalent). */
private fun enc(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
