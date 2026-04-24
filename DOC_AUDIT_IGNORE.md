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
start: Thread#start() / Server#start() (JDK / HttpServer) — not a SDK method
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

abspath: os.path.abspath (Python stdlib) — example filesystem helper
isoformat: datetime.datetime.isoformat (Python stdlib) — example timestamp
fromisoformat: datetime.datetime.fromisoformat (Python stdlib)
total_seconds: datetime.timedelta.total_seconds (Python stdlib)
basicConfig: logging.basicConfig (Python stdlib) — log setup in doc snippets
setLevel: logging.Logger.setLevel (Python stdlib) — log level in doc snippets
warning: logging.Logger.warning (Python stdlib)

### FastAPI / ASGI router (Python hosting, not ported)

as_router: FastAPI convenience on AgentBase for ASGI embedding (Python only)
include_router: FastAPI APIRouter.include_router (Python web framework)

### Python AgentBase methods not ported to Java

See PORT_OMISSIONS.md for the corresponding entries and rationale.

_check_basic_auth: private helper on Python AgentBase (not public API)
_configure_instructions: private helper on Python AgentBase (not public API)
_get_new_messages: private helper on Python AgentBase (not public API)
_register_custom_tools: private helper on Python AgentBase (not public API)
_register_default_tools: private helper on Python AgentBase (not public API)
_setup_contexts: private helper on Python AgentBase (not public API)
_setup_static_config: private helper on Python AgentBase (not public API)
_test_api_connection: private helper on Python AgentBase (not public API)
setPersonality: Python-docs artifact; Java uses promptAddSection("Personality", ...)
setGoal: Python-docs artifact; Java uses promptAddSection("Goal", ...)
setInstructions: Python-docs artifact; Java uses promptAddSection("Instructions", ...)
setGoal: Python-docs artifact; Java uses promptAddSection("Goal", ...)
enable_record_call: builder-option in Java (record_call=true on AgentBaseBuilder), not a runtime method
delete_state: state-manager method on Python AgentBase; Java session management is internal
add_application: Python web-service helper; Java uses Service-based routing
add_directory: Python web-service static-file helper; Java uses Service-based routing
remove_directory: Python web-service static-file helper; Java uses Service-based routing
get_full_url: Python AgentBase helper; Java computes URLs via Service#getRouteUrl
get_parameter_schema: Python Skill introspection; Java Skill surface is narrower
validate_env_vars: Python Skill helper; Java skills validate at instantiation
validate_packages: Python Skill helper; Java skills validate at instantiation
handle_serverless_request: Python cloud-functions helper; Java uses LambdaAgent wrapper
setup_google_search: Python convenience on AgentBase for the google-search skill
setup_sip_routing: Python convenience on AgentBase; Java uses AgentBase#enableSipRouting
register_default_tools: Python helper; Java exposes defineTool() directly
register_knowledge_base_tool: Python helper; Java uses addSkill("knowledge_base", ...)
register_routing_callback: Python SWMLService helper; Java uses builder routing
register_verb_handler: Python SWMLService helper; Java uses Document.registerVerbHandler (removed for now)
register_customer_route: user-defined example method, not SDK API
register_product_route: user-defined example method, not SDK API
build_document: user-defined example method, not SDK API
build_voicemail_document: user-defined example method, not SDK API
reset_document: Python SWMLService method; Java rebuilds via new Service(...) per-request
load_skill: Python SkillManager; Java uses AgentBase#addSkill
unload_skill: Python SkillManager; Java skills persist for the agent lifetime
list_all_skill_sources: Python SkillRegistry; Java registry is static
on_completion_go_to: Python Context builder helper; Java uses Context#setCompletionStep
allow_functions: Python Step helper; Java uses Step#setFunctions
apply_custom_config: user-defined example method, not SDK API
apply_default_config: user-defined example method, not SDK API
alert_ops_team: user-defined example function, not SDK API
get_customer_config: user-defined example method, not SDK API
get_customer_settings: user-defined example method, not SDK API
get_customer_tier: user-defined example method, not SDK API
get_config: user-defined example method, not SDK API
get_section: user-defined example method, not SDK API
has_config: user-defined example method, not SDK API
is_valid_customer: user-defined example method, not SDK API
load_user_preferences: user-defined example method, not SDK API
schedule_follow_up: user-defined example method, not SDK API
send_to_analytics: user-defined example method, not SDK API
webhook_expressions: Python DataMap builder method; Java uses DataMap#webhook

### Python REST namespace methods (documented for conceptual parity)

The Java port's REST namespaces expose flat CRUD (list/get/create/update/delete)
on `CrudResource` handles; Python's per-operation convenience methods
(`list_members`, `create_campaign`, `play_stop`, ...) are folded into
CRUD + a generic calling-control endpoint. See PORT_OMISSIONS.md and
`rest/docs/*.md` Java-native sections for the equivalent Java call.

add_membership: Python chat-channel helper — Java uses CrudResource CRUD
delete_membership: Python chat-channel helper — Java uses CrudResource CRUD
get_membership: Python chat-channel helper — Java uses CrudResource CRUD
list_memberships: Python chat-channel helper — Java uses CrudResource CRUD
ai_stop: Python calling-control RPC — not surfaced in Java's calling().calls()
collect_stop: Python calling-control RPC — not surfaced in Java's calling().calls()
collect_start_input_timers: Python calling-control RPC — not surfaced
detect_stop: Python calling-control RPC — not surfaced
play_pause: Python calling-control RPC — not surfaced
play_resume: Python calling-control RPC — not surfaced
play_stop: Python calling-control RPC — not surfaced
play_volume: Python calling-control RPC — not surfaced
receive_fax_stop: Python calling-control RPC — not surfaced
record_pause: Python calling-control RPC — not surfaced
record_resume: Python calling-control RPC — not surfaced
record_stop: Python calling-control RPC — not surfaced
send_fax_stop: Python calling-control RPC — not surfaced
start_recording: Python calling-control RPC — not surfaced
start_stream: Python calling-control RPC — not surfaced
stop_stream: Python calling-control RPC — not surfaced
stream_stop: Python calling-control RPC — not surfaced
tap_stop: Python calling-control RPC — not surfaced
transcribe_stop: Python calling-control RPC — not surfaced
update_recording: Python recording helper — Java uses recordings().recordings().update
create_campaign: Python campaign helper — Java uses client.campaign().campaigns().create
create_order: Python order helper — Java uses client.campaign().orders().create
list_campaigns: Python campaign helper — Java uses client.campaign().campaigns().list
list_orders: Python order helper — Java uses client.campaign().orders().list
create_stream: Python stream helper — Java uses client.streams().streams().create
list_streams: Python stream helper — Java uses client.streams().streams().list
delete_chunk: Python datasphere helper — Java uses client.datasphere().documents()
get_chunk: Python datasphere helper — Java uses client.datasphere().documents()
list_chunks: Python datasphere helper — Java uses client.datasphere().documents()
list_conference_tokens: Python conference helper — Java uses client.conferences().participants()
list_events: Python events helper — not ported (use REST listeners)
delete_media: Python compat helper — Java uses client.compat().messages().delete
list_media: Python compat helper — Java uses client.compat().messages().list
delete_recording: Python recording helper — Java uses recordings().recordings().delete
get_recording: Python recording helper — Java uses recordings().recordings().get
list_recordings: Python recording helper — Java uses recordings().recordings().list
list_members: Python chat/queue helper — Java uses CrudResource.list with filter
get_member: Python chat/queue helper — Java uses CrudResource.get
get_next_member: Python queue helper — not surfaced; list + client-side next
dequeue_member: Python queue helper — not surfaced
list_numbers: Python phone-numbers convenience — Java uses phoneNumbers().list
list_participants: Python conference helper — Java uses client.conferences().participants().list
get_participant: Python conference helper — Java uses participants().get
update_participant: Python conference helper — Java uses participants().update
remove_participant: Python conference helper — Java uses participants().delete
list_addresses: Python Fabric helper — Java uses fabric().addresses().list
list_available_countries: Python phone-numbers helper — not ported
import_number: Python compat number import — not ported
purchase: Python phone-numbers helper — Java uses phoneNumbers().create(body-with-number)
verify: Python verified-callers helper — Java uses compliance().cnamRegistrations()
redial_verification: Python verified-callers helper — not ported
submit_verification: Python verified-callers helper — not ported
search_local: Python compat number search — Java uses phoneNumbers().search
search_toll_free: Python compat number search — Java uses phoneNumbers().search
sms: Python messaging namespace — Java uses client.messaging().messages()
phone_number: doc-link reference, not a callable
end: Python Call.end — Java uses Call#hangup

### Relay (Python Call)

from_payload: Python RelayEvent classmethod — Java parses via RelayClient internals
wait_for: Python Call.wait_for — Java uses polling or Action#waitForCompletion
wait_for_ended: Python Call.wait_for_ended — Java uses Call#isEnded polling
