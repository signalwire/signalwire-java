# Fabric Resources

The Fabric API (`/api/fabric`) manages resource types in your SignalWire project. The Java SDK surfaces the three most common resource groupings — `subscribers`, `addresses`, and the generic `resources` handle — through `client.fabric()`. Other resource types are accessed through the generic handle by ID.

## Standard CRUD Pattern

Each handle exposes list/get/create/update/delete via `CrudResource`:

<!-- snippet-setup -->
```java
import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.PhoneCallHandler;
import com.signalwire.sdk.rest.RestError;

RestClient client = RestClient.builder().build();
String pnSid = "pn-uuid";
```

```java
// List all fabric resources
var items = client.fabric().resources().list(Map.of());

// Get a resource by ID
var resource = client.fabric().resources().get("resource-uuid", Map.of());

// Create a subscriber
var sub = client.fabric().subscribers().create(Map.of(
        "reference", "user@example.com",
        "password",  "secret"
));

// Update a subscriber
client.fabric().subscribers().update("subscriber-uuid", Map.of("display_name", "Ada"));

// Delete a resource
client.fabric().resources().delete("resource-uuid");
```

## Binding a phone number to a handler

**This is the section that cost a user hours in the post-mortem.** Read it before writing code that creates SWML or cXML webhook resources manually.

Phone-number bindings live on `client.phoneNumbers()`, not on Fabric. The server materializes the Fabric webhook resource automatically when you set `call_handler` on the phone number. Use the typed helpers:

```java
// SWML webhook — your backend returns an SWML document per call
client.phoneNumbers().setSwmlWebhook(pnSid, "https://example.com/swml", Map.of());

// cXML (Twilio-compat / LAML) webhook (optional fallback + status callback URLs)
client.phoneNumbers().setCxmlWebhook(pnSid, "https://example.com/voice.xml", null, null, Map.of());

// Existing cXML application by ID
client.phoneNumbers().setCxmlApplication(pnSid, "app-uuid", Map.of());

// AI Agent by ID
client.phoneNumbers().setAiAgent(pnSid, "agent-uuid", Map.of());

// Call flow (optionally pin a version)
client.phoneNumbers().setCallFlow(pnSid, "flow-uuid", "current_deployed", Map.of());

// Named RELAY application
client.phoneNumbers().setRelayApplication(pnSid, "my-relay-app", Map.of());

// RELAY topic (client subscription)
client.phoneNumbers().setRelayTopic(pnSid, "office", null, Map.of());
```

The wire-level form is always available:

```java
client.phoneNumbers().update(pnSid, Map.of(
        "call_handler",          PhoneCallHandler.RELAY_SCRIPT.wireValue(),
        "call_relay_script_url", "https://example.com/swml"
));
```

See **[phone-binding.md](phone-binding.md)** for the full `PhoneCallHandler` enum, the mapping from each handler value to its auto-materialized Fabric resource, and the runnable example at `rest/examples/RestBindPhoneToSwmlWebhook.java`.

### What the Java SDK deliberately does NOT expose

The Java SDK does not surface `assignPhoneRoute`, `swmlWebhooks().create()`, or `cxmlWebhooks().create()` on the Fabric namespace. Those endpoints exist on the server but are **not the way to bind a phone number** — doing so leaves orphan Fabric resources and the phone number unchanged. Every common binding case is covered by the `phoneNumbers().set*` helpers above; use them instead. If you have a truly unusual case that requires a direct Fabric call, build the request with `client.getHttpClient()` and the full REST reference.

## Generic Resources

`client.fabric().resources()` is the generic handle for fabric resources referenced by ID regardless of type:

```java
var all = client.fabric().resources().list(Map.of());
var one = client.fabric().resources().get("resource-uuid", Map.of());
client.fabric().resources().delete("resource-uuid");
```

## Subscribers

Subscribers are fabric-level user records. Use the helper for CRUD, and the generic `resources` handle for cross-type queries. The Java SDK currently exposes subscriber CRUD via the standard `CrudResource` surface (`list`, `get`, `create`, `update`, `delete`). Nested sub-resource helpers (SIP endpoints attached to a subscriber) are not yet surfaced as typed methods; fetch via `resources()` by ID if you need them.

## Fabric Addresses

Read-mostly access to fabric addresses:

```java
var addresses = client.fabric().addresses().list();
var address   = client.fabric().addresses().get("address-uuid");
```

## Error Handling

Every CRUD call throws `RestError` on non-2xx:

```java
try {
    client.fabric().resources().get("bad-id", Map.of());
} catch (RestError e) {
    System.out.println(e.getStatusCode()); // 404
    System.out.println(e.getMethod());     // "GET"
    System.out.println(e.getPath());       // "/fabric/resources/bad-id"
    System.out.println(e.getResponseBody());
}
```
