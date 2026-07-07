# Doc Audit Ignore List

Identifiers appearing in `docs/`, `rest/docs/`, `relay/docs/`, and `examples/`
that `scripts/audit_docs.py` should ignore. Each line is of the form
`<name>: <rationale>`. The `audit_docs.py` tool treats the name before the
`:` as an exact identifier. Grouped by category.

See `CHECKLIST_TEMPLATE.md` (Phase 13 — Doc↔code alignment) for the audit
contract.

---

## Java SE language / standard library

These are JDK identifiers that appear in Java example files and in
`` ```java `` code blocks. They are not part of the SignalWire SDK surface
and never will be.

### java.lang

Thread: java.lang.Thread (JDK) — used for background workers in examples
exit: System.exit(int) (JDK) — CLI error exit in examples
equals: Object#equals(Object) (JDK) — string / value comparison in examples
equalsIgnoreCase: String#equalsIgnoreCase (JDK) — case-insensitive compare
toLowerCase: String#toLowerCase (JDK) — String normalization in examples
toUpperCase: String#toUpperCase (JDK) — String normalization in examples
parseInt: Integer.parseInt (JDK) — env-var parsing in examples
parseDouble: Double.parseDouble (JDK) — env-var parsing in examples
doubleValue: Number#doubleValue (JDK) — numeric unboxing in examples
getMessage: Throwable#getMessage (JDK) — exception reporting in examples
println: PrintStream#println (JDK) — example console output
printf: PrintStream#printf (JDK) — example console output
interrupt: Thread#interrupt (JDK) — cooperative cancellation
setDaemon: Thread#setDaemon (JDK) — example background threads
currentThread: Thread#currentThread (JDK) — re-interrupt idiom
wait: Object#wait (JDK) — generic reference in docs; also matches Python wait_for_completion noise
java: a package/literal reference in test scaffolding (not a method call)

### java.util / java.util.function

of: Map.of / List.of / Set.of static factories (JDK 9+) — pervasive in examples
ofPattern: DateTimeFormatter.ofPattern (java.time) — example formatters
asList: Arrays.asList (JDK) — list construction in examples
getOrDefault: Map#getOrDefault (JDK) — safe-get pattern in examples
emptyMap: Collections.emptyMap (JDK) — empty-map literal in examples
entrySet: Map#entrySet (JDK) — iteration in examples
toList: Stream#toList (JDK 16+) — stream collection in examples
randomUUID: UUID.randomUUID (JDK) — id generation in examples / harnesses
toMap: Collectors.toMap / SDK FunctionResult#toMap — name shared with JDK; SDK form is the SignalWire one
length: String/array length (JDK) — substring math in examples / harnesses
indexOf: String#indexOf (JDK) — template-expansion math in harnesses
substring: String#substring (JDK) — string slicing in harnesses
startsWith: String#startsWith (JDK) — prefix tests in examples / harnesses
endsWith: String#endsWith (JDK) — suffix tests in examples / harnesses
getBytes: String#getBytes (JDK) — UTF-8 bytes for HTTP I/O in examples
min: Math.min (JDK) — numeric min in examples / harnesses

### java.net.http / java.net (JDK 11+ HTTP client)

newHttpClient: HttpClient.newHttpClient (JDK) — build a default client in examples
newBuilder: HttpClient/HttpRequest builders (JDK) — fluent client config in examples
header: HttpRequest.Builder#header (JDK) — set request header in examples
uri: HttpRequest.Builder#uri (JDK) — set request URI in examples / DataMap webhooks
ofString: HttpResponse.BodyHandlers.ofString (JDK) — string-body response handler
noBody: HttpRequest.BodyPublishers.noBody (JDK) — empty-body request publisher
send: HttpClient#send(request, bodyHandler) (JDK) — synchronous request dispatch in harnesses
statusCode: HttpResponse#statusCode (JDK) — response status check in examples
getRawPath: URI#getRawPath (JDK) — URL path extraction in harnesses
getRawQuery: URI#getRawQuery (JDK) — URL query extraction in harnesses
GET: HttpRequest.Builder#GET / HTTP method literal — JDK fluent client method
POST: HttpRequest.Builder#POST / HTTP method literal — JDK fluent client method
PUT: HttpRequest.Builder#PUT / HTTP method literal — JDK fluent client method
DELETE: HttpRequest.Builder#DELETE / HTTP method literal — JDK fluent client method

### com.sun.net.httpserver (JDK built-in HTTP server)

createContext: HttpServer#createContext (JDK) — example server route binding
sendResponseHeaders: HttpExchange#sendResponseHeaders (JDK) — example server reply
getResponseHeaders: HttpExchange#getResponseHeaders (JDK) — example server reply
getRequestBody: HttpExchange#getRequestBody (JDK) — example server input stream
readAllBytes: InputStream#readAllBytes (JDK) — example server input read

### Gson (JSON binding library used by SignalWire SDK)

fromJson: com.google.gson.Gson#fromJson — JSON parse helper in harnesses
getType: TypeToken#getType — generic-erasure helper for Gson in harnesses

### java.lang.System (timing / clock)

currentTimeMillis: System.currentTimeMillis (JDK) — deadline math in harnesses

### java.util.concurrent

call: Callable#call (JDK) — incidental match in harness lambda contexts

---

## Python-reference docs (doc↔code mismatch by design)

Most `docs/*.md`, `rest/docs/*.md`, and `relay/docs/*.md` files are shared
reference docs imported from the SignalWire Python SDK. Their `` ```python ``
code blocks describe the Python API. The Java port publishes a narrower,
idiomatic Java surface (see `PORT_OMISSIONS.md` for the full list of
deliberate gaps). Identifiers below appear only in Python code blocks; a
Java developer reading the surrounding prose is correctly pointed at the
Java equivalent or told the API is not ported.

### Python dunders and decorator targets

__init__: Python constructor convention in `` ```python `` blocks
tool: the `@AgentBase.tool(...)` Python decorator — Java uses defineTool()

### Python stdlib and logging

warning: logging.Logger.warning (Python stdlib)

### FastAPI / ASGI router (Python hosting, not ported)


### Python AgentBase methods not ported to Java

See PORT_OMISSIONS.md for the corresponding entries and rationale.

setPersonality: Python-docs artifact; Java uses promptAddSection("Personality", ...)
setGoal: Python-docs artifact; Java uses promptAddSection("Goal", ...)
setInstructions: Python-docs artifact; Java uses promptAddSection("Instructions", ...)
setGoal: Python-docs artifact; Java uses promptAddSection("Goal", ...)
get_config: user-defined example method, not SDK API

### Python REST namespace methods (documented for conceptual parity)

The Java port's REST namespaces expose flat CRUD (list/get/create/update/delete)
on `CrudResource` handles; Python's per-operation convenience methods
(`list_members`, `create_campaign`, `play_stop`, ...) are folded into
CRUD + a generic calling-control endpoint. See PORT_OMISSIONS.md and
`rest/docs/*.md` Java-native sections for the equivalent Java call.

start_recording: Python calling-control RPC — not surfaced
purchase: Python phone-numbers helper — Java uses phoneNumbers().create(body-with-number)
verify: Python verified-callers helper — Java uses compliance().cnamRegistrations()
sms: Python messaging namespace — Java uses client.messaging().messages()
phone_number: doc-link reference, not a callable
end: Python Call.end — Java uses Call#hangup

### Relay (Python Call)


### Generated-namespace accessors not yet enumerated by the surface tool

Real accessors on the generated ResourceTree / namespace classes
(client.phoneNumbers(), client.queues(), client.recordings(),
LogsNamespace.conferences()/VideoNamespace.conferences()) that the current
enumerator does not surface under their bare accessor name. Pre-existing
enumerator gap from the REST-generation (changeset A) layout; tracked
separately. Listed here so legitimate example references resolve.

phoneNumbers: real ResourceTree.phoneNumbers() accessor; enumerator does not surface the bare name
queues: real ResourceTree.queues() accessor; enumerator does not surface the bare name
recordings: real ResourceTree.recordings() accessor; enumerator does not surface the bare name
conferences: real LogsNamespace/VideoNamespace conferences() accessor; enumerator does not surface the bare name
fabric: real ResourceTree.fabric() top-level namespace accessor; mirrors Python client.fabric attribute (not method-surface in either language)
video: real ResourceTree.video() top-level namespace accessor; mirrors Python client.video attribute
calling: real ResourceTree.calling() top-level namespace accessor; mirrors Python client.calling attribute
datasphere: real ResourceTree.datasphere() top-level namespace accessor; mirrors Python client.datasphere attribute
registry: real ResourceTree.registry() top-level namespace accessor; mirrors Python client.registry attribute
resources: real FabricNamespace.resources() sub-resource accessor; mirrors Python client.fabric.resources attribute
aiAgents: real FabricNamespace.aiAgents() sub-resource accessor; mirrors Python client.fabric.ai_agents attribute
subscribers: real FabricNamespace.subscribers() sub-resource accessor; mirrors Python client.fabric.subscribers attribute
addresses: real FabricNamespace.addresses() sub-resource accessor; mirrors Python client.fabric.addresses attribute
callFlows: real FabricNamespace.callFlows() sub-resource accessor; mirrors Python client.fabric.call_flows attribute
conferenceRooms: real FabricNamespace.conferenceRooms() sub-resource accessor; mirrors Python client.fabric.conference_rooms attribute
sipEndpoints: real FabricNamespace.sipEndpoints() sub-resource accessor; mirrors Python client.fabric.sip_endpoints attribute
swmlScripts: real FabricNamespace.swmlScripts() sub-resource accessor; mirrors Python client.fabric.swml_scripts attribute
rooms: real VideoNamespace.rooms() sub-resource accessor; mirrors Python client.video.rooms attribute
roomSessions: real VideoNamespace.roomSessions() sub-resource accessor; mirrors Python client.video.room_sessions attribute
roomRecordings: real VideoNamespace.roomRecordings() sub-resource accessor; mirrors Python client.video.room_recordings attribute
documents: real DatasphereNamespace.documents() sub-resource accessor; mirrors Python client.datasphere.documents attribute
brands: real RegistryNamespace.brands() sub-resource accessor; mirrors Python client.registry.brands attribute
campaigns: real RegistryNamespace.campaigns() sub-resource accessor; mirrors Python client.registry.campaigns attribute

### Typed-REST request-builder setters (Java idiom for Python kwargs)

The generated REST command/resource methods take a closed typed *Request
object built via a fluent builder (e.g. Calling.DialRequest.builder()
.from(..).to(..).build()). Each builder setter carries one wire field;
`extras(Map)` is the escape door for fields outside the closed set. Python
passes these same wire fields as keyword arguments, so the setter *name* has
no method-surface analog in either language's enumerated surface — it is the
Java typed-request idiom for a Python kwarg. Real, compiling public builder
methods; listed so example references resolve.

extras: Calling.*Request.Builder#extras(Map) etc. — arbitrary-field escape door on every typed *Request builder
from: Calling.DialRequest.Builder#from(String) — 'from' wire field (dial)
to: Calling.DialRequest.Builder#to(String) — 'to' wire field (dial)
url: Calling.DialRequest.Builder#url(String) — 'url' wire field (dial)
initialTimeout: Calling.CollectRequest.Builder#initialTimeout(Double) — 'initial_timeout' wire field (collect)
digits: Calling.CollectRequest.Builder#digits(Map) — 'digits' wire field (collect)
audio: Calling.RecordRequest.Builder#audio(Map) — 'audio' wire field (record)
queryString: DatasphereDocuments.SearchRequest.Builder#queryString(String) — 'query_string' wire field (document search)
username: Subscribers.CreateSipEndpointRequest.Builder#username(String) — 'username' wire field (create SIP endpoint)
password: Subscribers.CreateSipEndpointRequest.Builder#password(String) — 'password' wire field (create SIP endpoint)

### More REST namespace / sub-resource accessors (enumerator does not surface the bare name)

Real fluent accessors on the generated ResourceTree / namespace classes
(`rest/namespaces/generated/*.java`), same category as phoneNumbers/queues/recordings
above; the surface enumerator does not emit them under their bare accessor name.
Listed so legitimate example references resolve.

channels: real PubSub.channels()/ChatNamespace accessor; enumerator does not surface the bare name
chat: real ResourceTree.chat() namespace accessor; enumerator does not surface the bare name
conferenceTokens: real VideoNamespace.conferenceTokens() accessor; enumerator does not surface the bare name
importedNumbers: real ResourceTree.importedNumbers() accessor; enumerator does not surface the bare name
logs: real ResourceTree.logs() namespace accessor; enumerator does not surface the bare name
messages: real LogsNamespace.messages() / MessagingNamespace accessor; enumerator does not surface the bare name
mfa: real ResourceTree.mfa() namespace accessor; enumerator does not surface the bare name
numberGroups: real ResourceTree.numberGroups() accessor; enumerator does not surface the bare name
numbers: real RegistryNamespace.numbers() accessor; enumerator does not surface the bare name
orders: real RegistryNamespace.orders() accessor; enumerator does not surface the bare name
pubsub: real ResourceTree.pubsub() namespace accessor; enumerator does not surface the bare name
roomTokens: real VideoNamespace.roomTokens() accessor; enumerator does not surface the bare name
shortCodes: real ResourceTree.shortCodes() accessor; enumerator does not surface the bare name
sipProfile: real ResourceTree.sipProfile() accessor; enumerator does not surface the bare name
streams: real VideoNamespace.streams() accessor; enumerator does not surface the bare name
tokens: real ProjectNamespace.tokens() accessor; enumerator does not surface the bare name
verifiedCallers: real ResourceTree.verifiedCallers() accessor; enumerator does not surface the bare name
voice: real LogsNamespace.voice() accessor; enumerator does not surface the bare name
permissions: real ProjectTokens.permissions() accessor; enumerator does not surface the bare name
lookup: real ResourceTree.lookup() namespace accessor; enumerator does not surface the bare name
fax: real LogsNamespace.fax() accessor; enumerator does not surface the bare name

### More typed-REST request-builder setters (Java idiom for a Python kwarg / wire field)

Real, compiling public builder setters on the generated `*Request` builders (each
carries one wire field); the enumerated surface has no method-surface analog for the
setter name in either language. Listed so example references resolve.

action: Calling.*Request.Builder#action(Map) — 'action' wire field
codec: Calling.*Request.Builder#codec(String) — 'codec' wire field
controlId: Calling.*Request.Builder#controlId(String) — 'control_id' wire field
dest: Calling.*Request.Builder#dest(Map) — 'destination' wire field
device: Calling.*Request.Builder#device(Map) — 'device' wire field
domainIdentifier: SipProfile.UpdateRequest.Builder#domainIdentifier(String) — 'domain_identifier' wire field
event: Calling.*Request.Builder#event(Map) — 'event' wire field
id: Calling.*Request.Builder#id(String) — resource id path/wire field
memberId: *Request.Builder#memberId(String) — 'member_id' wire field
message: *Request.Builder#message(String) — 'message' wire field
messageText: *Request.Builder#messageText(String) — 'message_text' wire field
phoneNumberId: NumberGroups.AddMembershipRequest.Builder#phoneNumberId(String) — 'phone_number_id' wire field
reason: Calling.EndRequest.Builder#reason(String) — 'reason' wire field
role: *Request.Builder#role(String) — 'role' wire field
speech: Calling.CollectRequest.Builder#speech(Map) — 'speech' wire field
statusUrl: *Request.Builder#statusUrl(String) — 'status_url' wire field
tags: *Request.Builder#tags(List) — 'tags' wire field
timeout: Calling.*Request.Builder#timeout(Long) — 'timeout' wire field
ttl: *Request.Builder#ttl(Long) — 'ttl' wire field
verificationCode: VerifiedCallers.SubmitVerificationRequest.Builder#verificationCode(String) — 'verification_code' wire field

### JDK / stdlib methods and example-local helpers (not SDK surface)

These appear in Java example code blocks: JDK/standard-library methods, the Azure/AWS
serverless-runtime API used by the cloud-functions guide, or user-defined helper
methods local to an example. They are not part of the SignalWire SDK surface.

abs: Math.abs (JDK) — example id math
add: List/Collection#add (JDK) — example list mutation
between: java.time.Duration.between (JDK) — example duration math
computeIfAbsent: Map#computeIfAbsent (JDK) — example config cache
contains: String/Collection#contains (JDK) — example membership test
containsKey: Map#containsKey (JDK) — example request-body check
encodeToString: Base64.Encoder#encodeToString (JDK) — example basic-auth header
getEncoder: Base64.getEncoder (JDK) — example basic-auth header
getSeconds: Duration#getSeconds (JDK) — example duration math
getUri: Azure Functions HttpRequestMessage#getUri — external serverless runtime, cloud-functions guide
getWriter: HttpServletResponse#getWriter (Servlet API) — example serverless handler output
ifPresent: Optional#ifPresent (JDK) — example optional handling
intValue: Number#intValue (JDK) — example JSON-Schema type cast
joining: Collectors.joining (JDK) — example stream collection
lines: BufferedReader#lines (JDK) — example request-body read
max: Math.max (JDK) — example numeric clamp
orElse: Optional#orElse (JDK) — example optional handling
trim: String#trim (JDK) — example arg normalization
getHttpMethod: Azure Functions HttpRequestMessage#getHttpMethod — external serverless runtime, cloud-functions guide
setStatusCode: Azure/serverless HttpResponse#setStatusCode — external serverless runtime, cloud-functions guide
createResponseBuilder: Azure Functions HttpRequestMessage#createResponseBuilder — external serverless runtime, cloud-functions guide
getCustomerSettings: user-defined example database helper, not SDK API
registerAll: user-defined example skill-bootstrap method, not SDK API
toAbsolutePath: java.nio.file.Path#toAbsolutePath (JDK) — example static-dir path
status: user-defined example serverless-result accessor, not SDK API
headers: user-defined example serverless-result accessor / JDK HttpResponse, not SDK API
