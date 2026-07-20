# Doc Audit Ignore List

Identifiers appearing in `docs/`, `rest/docs/`, `relay/docs/`, and `examples/`
that `scripts/audit_docs.py` should ignore. Each line is of the form
`<Owner#member>: <rationale>`. The `audit_docs.py` tool matches on the trailing
member segment after the last `.`/`#`/`::` qualifier, so every key is written in
its **qualified** `Class#member` / `Class.member` form (never a bare token) —
the qualifier names the real owning type the reference resolves to, which is the
concrete reason the identifier is not SignalWire SDK surface. Grouped by category.

See `CHECKLIST_TEMPLATE.md` (Phase 13 — Doc↔code alignment) for the audit
contract.

---

## Java SE language / standard library

These are JDK identifiers that appear in Java example files and in
`` ```java `` code blocks. They are not part of the SignalWire SDK surface
and never will be.

### java.lang

System#exit: System.exit(int) (JDK) — CLI error exit in examples
String#equalsIgnoreCase: String#equalsIgnoreCase (JDK) — case-insensitive compare
String#toLowerCase: String#toLowerCase (JDK) — String normalization in examples
String#toUpperCase: String#toUpperCase (JDK) — String normalization in examples
Integer#parseInt: Integer.parseInt (JDK) — env-var parsing in examples
Double#parseDouble: Double.parseDouble (JDK) — env-var parsing in examples
Number#doubleValue: Number#doubleValue (JDK) — numeric unboxing in examples
PrintStream#println: PrintStream#println (JDK) — example console output
PrintStream#printf: PrintStream#printf (JDK) — example console output
Thread#interrupt: Thread#interrupt (JDK) — cooperative cancellation
Thread#setDaemon: Thread#setDaemon (JDK) — example background threads
Thread#currentThread: Thread#currentThread (JDK) — re-interrupt idiom
java.lang#java: a package/literal reference in test scaffolding (not a method call)

### java.util / java.util.function

DateTimeFormatter#ofPattern: DateTimeFormatter.ofPattern (java.time) — example formatters
Arrays#asList: Arrays.asList (JDK) — list construction in examples
Map#getOrDefault: Map#getOrDefault (JDK) — safe-get pattern in examples
Collections#emptyMap: Collections.emptyMap (JDK) — empty-map literal in examples
Map#entrySet: Map#entrySet (JDK) — iteration in examples
Stream#toList: Stream#toList (JDK 16+) — stream collection in examples
UUID#randomUUID: UUID.randomUUID (JDK) — id generation in examples / harnesses
FunctionResult#toMap: Collectors.toMap / SDK FunctionResult#toMap — name shared with JDK; SDK form is the SignalWire one
String#length: String/array length (JDK) — substring math in examples / harnesses
String#indexOf: String#indexOf (JDK) — template-expansion math in harnesses
String#substring: String#substring (JDK) — string slicing in harnesses
String#startsWith: String#startsWith (JDK) — prefix tests in examples / harnesses
String#endsWith: String#endsWith (JDK) — suffix tests in examples / harnesses
String#getBytes: String#getBytes (JDK) — UTF-8 bytes for HTTP I/O in examples
Math#min: Math.min (JDK) — numeric min in examples / harnesses

### java.net.http / java.net (JDK 11+ HTTP client)

HttpClient#newHttpClient: HttpClient.newHttpClient (JDK) — build a default client in examples
HttpClient#newBuilder: HttpClient/HttpRequest builders (JDK) — fluent client config in examples
HttpRequest.Builder#header: HttpRequest.Builder#header (JDK) — set request header in examples
HttpRequest.Builder#uri: HttpRequest.Builder#uri (JDK) — set request URI in examples / DataMap webhooks
BodyHandlers#ofString: HttpResponse.BodyHandlers.ofString (JDK) — string-body response handler
BodyPublishers#noBody: HttpRequest.BodyPublishers.noBody (JDK) — empty-body request publisher
HttpClient#send: HttpClient#send(request, bodyHandler) (JDK) — synchronous request dispatch in harnesses
HttpResponse#statusCode: HttpResponse#statusCode (JDK) — response status check in examples
URI#getRawPath: URI#getRawPath (JDK) — URL path extraction in harnesses
URI#getRawQuery: URI#getRawQuery (JDK) — URL query extraction in harnesses
HttpRequest.Builder#GET: HttpRequest.Builder#GET / HTTP method literal — JDK fluent client method
HttpRequest.Builder#POST: HttpRequest.Builder#POST / HTTP method literal — JDK fluent client method
HttpRequest.Builder#PUT: HttpRequest.Builder#PUT / HTTP method literal — JDK fluent client method
HttpRequest.Builder#DELETE: HttpRequest.Builder#DELETE / HTTP method literal — JDK fluent client method

### com.sun.net.httpserver (JDK built-in HTTP server)

HttpServer#createContext: HttpServer#createContext (JDK) — example server route binding
HttpExchange#sendResponseHeaders: HttpExchange#sendResponseHeaders (JDK) — example server reply
HttpExchange#getResponseHeaders: HttpExchange#getResponseHeaders (JDK) — example server reply
HttpExchange#getRequestBody: HttpExchange#getRequestBody (JDK) — example server input stream
InputStream#readAllBytes: InputStream#readAllBytes (JDK) — example server input read

### java.lang.System (timing / clock)

System#currentTimeMillis: System.currentTimeMillis (JDK) — deadline math in harnesses

---

## Relay reserved-word idiom rename (real method surfaced under its reference name)

`await` is a REAL public SDK method (`Action#await()` / `Message#await()`,
relay/Action.java + relay/Message.java) — a fluent alias of
`waitForCompletion()`. The surface enumerator renames the Java-reserved
`await` to the reference name `wait` (java.lang.Object.wait is final and
non-overridable), so the bare Java identifier `await` is not in the compared
(python-reference) surface. Docs written in Java naturally use `await()`.

Action#await: real Action#await() / Message#await() RELAY accessor (relay/Action.java, relay/Message.java) — fluent alias of waitForCompletion(); real generated/hand SDK method, surface fold-gap, enumerator does not surface the bare name

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

Calling.DialRequest.Builder#extras: Calling.*Request.Builder#extras(Map) etc. — arbitrary-field escape door on every typed *Request builder
Calling.DialRequest.Builder#from: Calling.DialRequest.Builder#from(String) — 'from' wire field (dial)
Calling.DialRequest.Builder#url: real 'url' wire-field Builder setters — Calling.DialRequest.Builder#url / Calling.UpdateRequest.Builder#url / Calling.StreamRequest.Builder#url / VideoRooms.CreateStreamRequest.Builder#url / VideoConferences.CreateStreamRequest.Builder#url / VideoStreams.UpdateRequest.Builder#url. The qualified setters listed are the complete real surface this key covers, not just dial.
Calling.CollectRequest.Builder#initialTimeout: Calling.CollectRequest.Builder#initialTimeout(Double) — 'initial_timeout' wire field (collect)
Calling.CollectRequest.Builder#digits: Calling.CollectRequest.Builder#digits(Map) — 'digits' wire field (collect)
Calling.RecordRequest.Builder#audio: Calling.RecordRequest.Builder#audio(Map) — 'audio' wire field (record)
DatasphereDocuments.SearchRequest.Builder#queryString: DatasphereDocuments.SearchRequest.Builder#queryString(String) — 'query_string' wire field (document search)
Subscribers.CreateSipEndpointRequest.Builder#username: Subscribers.CreateSipEndpointRequest.Builder#username(String) — 'username' wire field (create SIP endpoint)
Subscribers.CreateSipEndpointRequest.Builder#password: Subscribers.CreateSipEndpointRequest.Builder#password(String) — 'password' wire field (create SIP endpoint)
PubSub#channels: real PubSub.channels()/ChatNamespace accessor; enumerator does not surface the bare name
ProjectTokens#permissions: real ProjectTokens.permissions() accessor; enumerator does not surface the bare name
Calling.DialRequest.Builder#action: Calling.*Request.Builder#action(Map) — 'action' wire field
Addresses.CreateRequest.Builder#city: Addresses.CreateRequest.Builder#city(String) — 'city' wire field (address create)
Addresses.CreateRequest.Builder#state: real 'state' wire-field Builder setter — Addresses.CreateRequest.Builder#state(String) (address create). This is the sole builder that surfaces `.state(` in docs.
Addresses.CreateRequest.Builder#streetName: Addresses.CreateRequest.Builder#streetName(String) — 'street_name' wire field (address create)
Addresses.CreateRequest.Builder#streetNumber: Addresses.CreateRequest.Builder#streetNumber(String) — 'street_number' wire field (address create)
VideoRoomTokens.CreateRequest.Builder#roomName: VideoRoomTokens.CreateRequest.Builder#roomName(String) — 'room_name' wire field (room token create)
VideoRoomTokens.CreateRequest.Builder#userName: VideoRoomTokens.CreateRequest.Builder#userName(String) — 'user_name' wire field (room token create)
Calling.DialRequest.Builder#codec: Calling.*Request.Builder#codec(String) — 'codec' wire field
Calling.DialRequest.Builder#controlId: Calling.*Request.Builder#controlId(String) — 'control_id' wire field
Calling.DialRequest.Builder#dest: Calling.*Request.Builder#dest(Map) — 'destination' wire field
Calling.DialRequest.Builder#device: Calling.*Request.Builder#device(Map) — 'device' wire field
SipProfile.UpdateRequest.Builder#domainIdentifier: SipProfile.UpdateRequest.Builder#domainIdentifier(String) — 'domain_identifier' wire field
Calling.DialRequest.Builder#event: Calling.*Request.Builder#event(Map) — 'event' wire field
Calling.UpdateRequest.Builder#id: real 'id' path/wire-field Builder setter — Calling.UpdateRequest.Builder#id(String) (the call id targeted by calling().update). This is the sole builder that surfaces `.id(` in docs.
NumberGroups.AddMembershipRequest.Builder#memberId: *Request.Builder#memberId(String) — 'member_id' wire field
Calling.DialRequest.Builder#message: *Request.Builder#message(String) — 'message' wire field
Calling.DialRequest.Builder#messageText: *Request.Builder#messageText(String) — 'message_text' wire field
NumberGroups.AddMembershipRequest.Builder#phoneNumberId: NumberGroups.AddMembershipRequest.Builder#phoneNumberId(String) — 'phone_number_id' wire field
Calling.EndRequest.Builder#reason: Calling.EndRequest.Builder#reason(String) — 'reason' wire field
Calling.DialRequest.Builder#role: *Request.Builder#role(String) — 'role' wire field
Calling.CollectRequest.Builder#speech: Calling.CollectRequest.Builder#speech(Map) — 'speech' wire field
Calling.DialRequest.Builder#statusUrl: *Request.Builder#statusUrl(String) — 'status_url' wire field
Calling.DialRequest.Builder#tags: *Request.Builder#tags(List) — 'tags' wire field
Calling.AiHoldRequest.Builder#timeout: real 'timeout' wire-field Builder setter — Calling.AiHoldRequest.Builder#timeout(Long) (calling().aiHold). This is the sole builder that surfaces `.timeout(` in docs.
Calling.DialRequest.Builder#ttl: *Request.Builder#ttl(Long) — 'ttl' wire field
VerifiedCallers.SubmitVerificationRequest.Builder#verificationCode: VerifiedCallers.SubmitVerificationRequest.Builder#verificationCode(String) — 'verification_code' wire field

## JDK / stdlib / external-runtime / example-local helpers (not SDK surface)

These appear in Java example code blocks: JDK/standard-library methods, the
Azure/AWS serverless-runtime API used by the cloud-functions guide, or
user-defined helper methods local to an example. They are not part of the
SignalWire SDK surface.

Math#abs: Math.abs (JDK) — example id math
List#add: List/Collection#add (JDK) — example list mutation
MessageDigest#isEqual: MessageDigest.isEqual (JDK) — timing-safe credential compare cited in api_reference/sdk_features/swml_service_guide prose
Map#size: Map/Collection#size (JDK) — example field-count in agent_guide analytics snippet
com.example.skills.WeatherSkill#WeatherSkill: user-defined example skill class (com.example.skills.WeatherSkill), not SDK API — reader's own third-party-skill in third_party_skills.md
Duration#between: java.time.Duration.between (JDK) — example duration math
Map#computeIfAbsent: Map#computeIfAbsent (JDK) — example config cache
String#contains: String/Collection#contains (JDK) — example membership test
Map#containsKey: Map#containsKey (JDK) — example request-body check
Base64.Encoder#encodeToString: Base64.Encoder#encodeToString (JDK) — example basic-auth header
Base64#getEncoder: Base64.getEncoder (JDK) — example basic-auth header
Duration#getSeconds: Duration#getSeconds (JDK) — example duration math
HttpRequestMessage#getUri: Azure Functions HttpRequestMessage#getUri — external serverless runtime, cloud-functions guide
HttpServletResponse#getWriter: HttpServletResponse#getWriter (Servlet API) — example serverless handler output
Optional#ifPresent: Optional#ifPresent (JDK) — example optional handling
Number#intValue: Number#intValue (JDK) — example JSON-Schema type cast
Collectors#joining: Collectors.joining (JDK) — example stream collection
BufferedReader#lines: BufferedReader#lines (JDK) — example request-body read
Math#max: Math.max (JDK) — example numeric clamp
Optional#orElse: Optional#orElse (JDK) — example optional handling
String#trim: String#trim (JDK) — example arg normalization
HttpRequestMessage#getHttpMethod: Azure Functions HttpRequestMessage#getHttpMethod — external serverless runtime, cloud-functions guide
HttpResponse#setStatusCode: Azure/serverless HttpResponse#setStatusCode — external serverless runtime, cloud-functions guide
HttpRequestMessage#createResponseBuilder: Azure Functions HttpRequestMessage#createResponseBuilder — external serverless runtime, cloud-functions guide
ExampleDb#getCustomerSettings: user-defined example database helper, not SDK API
ExampleBootstrap#registerAll: user-defined example skill-bootstrap method, not SDK API
Path#toAbsolutePath: java.nio.file.Path#toAbsolutePath (JDK) — example static-dir path
ServerlessResult#status: user-defined example serverless-result accessor, not SDK API
ServerlessResult#headers: user-defined example serverless-result accessor / JDK HttpResponse, not SDK API
