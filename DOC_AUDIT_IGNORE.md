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

exit: System.exit(int) (JDK) — CLI error exit in examples
equalsIgnoreCase: String#equalsIgnoreCase (JDK) — case-insensitive compare
toLowerCase: String#toLowerCase (JDK) — String normalization in examples
toUpperCase: String#toUpperCase (JDK) — String normalization in examples
parseInt: Integer.parseInt (JDK) — env-var parsing in examples
parseDouble: Double.parseDouble (JDK) — env-var parsing in examples
doubleValue: Number#doubleValue (JDK) — numeric unboxing in examples
println: PrintStream#println (JDK) — example console output
printf: PrintStream#printf (JDK) — example console output
interrupt: Thread#interrupt (JDK) — cooperative cancellation
setDaemon: Thread#setDaemon (JDK) — example background threads
currentThread: Thread#currentThread (JDK) — re-interrupt idiom
java: a package/literal reference in test scaffolding (not a method call)

### java.util / java.util.function

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


### java.lang.System (timing / clock)

currentTimeMillis: System.currentTimeMillis (JDK) — deadline math in harnesses

### java.util.concurrent


---

## Relay reserved-word idiom rename (real method surfaced under its reference name)

`await` is a REAL public SDK method (`Action#await()` / `Message#await()`,
relay/Action.java + relay/Message.java) — a fluent alias of
`waitForCompletion()`. The surface enumerator renames the Java-reserved
`await` to the reference name `wait` (java.lang.Object.wait is final and
non-overridable), so the bare Java identifier `await` is not in the compared
(python-reference) surface. Docs written in Java naturally use `await()`.

await: real Action#await() / Message#await() RELAY accessor (relay/Action.java, relay/Message.java) — fluent alias of waitForCompletion(); real generated/hand SDK method, surface fold-gap, enumerator does not surface the bare name

## Typed-REST request-builder setters (Java idiom for a Python kwarg / wire field)

Real, compiling public builder setters on the generated `*Request` builders
(e.g. `Calling.DialRequest.builder().from(..).to(..).build()`). Each setter
carries one wire field; `extras(Map)` is the escape door for fields outside
the closed set. Python passes these same wire fields as keyword arguments, so
the setter *name* has no method-surface analog in either language's enumerated
surface — it is the Java typed-request idiom for a Python kwarg. (`channels` and
`permissions` are such Builder setters — Chat/PubSub.Builder#channels,
ProjectTokens/VideoRoomTokens.Builder#permissions — not namespace accessors.)
Listed so example references resolve.

extras: Calling.*Request.Builder#extras(Map) etc. — arbitrary-field escape door on every typed *Request builder
from: Calling.DialRequest.Builder#from(String) — 'from' wire field (dial)
url: real 'url' wire-field Builder setters — Calling.DialRequest.Builder#url / Calling.UpdateRequest.Builder#url / Calling.StreamRequest.Builder#url / VideoRooms.CreateStreamRequest.Builder#url / VideoConferences.CreateStreamRequest.Builder#url / VideoStreams.UpdateRequest.Builder#url. Bare-word KEY (audit_docs matches the token before ':' only); the qualified setters above are the complete real surface it covers, not just dial.
initialTimeout: Calling.CollectRequest.Builder#initialTimeout(Double) — 'initial_timeout' wire field (collect)
digits: Calling.CollectRequest.Builder#digits(Map) — 'digits' wire field (collect)
audio: Calling.RecordRequest.Builder#audio(Map) — 'audio' wire field (record)
queryString: DatasphereDocuments.SearchRequest.Builder#queryString(String) — 'query_string' wire field (document search)
username: Subscribers.CreateSipEndpointRequest.Builder#username(String) — 'username' wire field (create SIP endpoint)
password: Subscribers.CreateSipEndpointRequest.Builder#password(String) — 'password' wire field (create SIP endpoint)
channels: real PubSub.channels()/ChatNamespace accessor; enumerator does not surface the bare name
permissions: real ProjectTokens.permissions() accessor; enumerator does not surface the bare name
action: Calling.*Request.Builder#action(Map) — 'action' wire field
city: Addresses.CreateRequest.Builder#city(String) — 'city' wire field (address create)
state: real 'state' wire-field Builder setter — Addresses.CreateRequest.Builder#state(String) (address create). Bare-word KEY (audit_docs matches the token before ':' only); this is the sole builder that surfaces `.state(` in docs.
streetName: Addresses.CreateRequest.Builder#streetName(String) — 'street_name' wire field (address create)
streetNumber: Addresses.CreateRequest.Builder#streetNumber(String) — 'street_number' wire field (address create)
roomName: VideoRoomTokens.CreateRequest.Builder#roomName(String) — 'room_name' wire field (room token create)
userName: VideoRoomTokens.CreateRequest.Builder#userName(String) — 'user_name' wire field (room token create)
codec: Calling.*Request.Builder#codec(String) — 'codec' wire field
controlId: Calling.*Request.Builder#controlId(String) — 'control_id' wire field
dest: Calling.*Request.Builder#dest(Map) — 'destination' wire field
device: Calling.*Request.Builder#device(Map) — 'device' wire field
domainIdentifier: SipProfile.UpdateRequest.Builder#domainIdentifier(String) — 'domain_identifier' wire field
event: Calling.*Request.Builder#event(Map) — 'event' wire field
id: real 'id' path/wire-field Builder setter — Calling.UpdateRequest.Builder#id(String) (the call id targeted by calling().update). Bare-word KEY (audit_docs matches the token before ':' only); this is the sole builder that surfaces `.id(` in docs.
memberId: *Request.Builder#memberId(String) — 'member_id' wire field
message: *Request.Builder#message(String) — 'message' wire field
messageText: *Request.Builder#messageText(String) — 'message_text' wire field
phoneNumberId: NumberGroups.AddMembershipRequest.Builder#phoneNumberId(String) — 'phone_number_id' wire field
reason: Calling.EndRequest.Builder#reason(String) — 'reason' wire field
role: *Request.Builder#role(String) — 'role' wire field
speech: Calling.CollectRequest.Builder#speech(Map) — 'speech' wire field
statusUrl: *Request.Builder#statusUrl(String) — 'status_url' wire field
tags: *Request.Builder#tags(List) — 'tags' wire field
timeout: real 'timeout' wire-field Builder setter — Calling.AiHoldRequest.Builder#timeout(Long) (calling().aiHold). Bare-word KEY (audit_docs matches the token before ':' only); this is the sole builder that surfaces `.timeout(` in docs.
ttl: *Request.Builder#ttl(Long) — 'ttl' wire field
verificationCode: VerifiedCallers.SubmitVerificationRequest.Builder#verificationCode(String) — 'verification_code' wire field

## JDK / stdlib / external-runtime / example-local helpers (not SDK surface)

These appear in Java example code blocks: JDK/standard-library methods, the
Azure/AWS serverless-runtime API used by the cloud-functions guide, or
user-defined helper methods local to an example. They are not part of the
SignalWire SDK surface.

abs: Math.abs (JDK) — example id math
add: List/Collection#add (JDK) — example list mutation
isEqual: MessageDigest.isEqual (JDK) — timing-safe credential compare cited in api_reference/sdk_features/swml_service_guide prose
size: Map/Collection#size (JDK) — example field-count in agent_guide analytics snippet
WeatherSkill: user-defined example skill class (com.example.skills.WeatherSkill), not SDK API — reader's own third-party-skill in third_party_skills.md
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
