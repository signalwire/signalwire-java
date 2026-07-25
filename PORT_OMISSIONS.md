<!-- ══════════════════════════════════════════════════════════════════════════
BEFORE YOU ADD AN ENTRY TO THIS FILE — READ THIS.

Every entry here is a place the parity checker STOPS comparing. That is a real cost:
a divergence you list is a divergence no gate will ever catch again. So entries must
be RARE, and each one must earn its place. Default to skepticism: assume the entry is
NOT needed and make the case that it is.

The order of preference, always:
  1. FIX THE PORT so it matches the reference (add the missing member; make the
     signature match).
  2. FIX THE EMISSION so idiom folds onto the reference shape — the enumerator/emitter
     canonicalizes your language's spelling onto the oracle's (builder → __init__,
     getters → attributes, Result<T,E> → the plain return, CamelCase → the reference
     name, options-object/kwargs → the expanded param list, RAII/dispose → close).
     MOST divergences are idiom and belong here, not in this file.
  3. FIX THE REFERENCE if the oracle itself is wrong or stale (a Python-only symbol
     that leaked into the contract, a param the reference added and the oracle never
     re-enumerated). Fix Python / the oracle, then re-drift — do not paper over a
     broken reference with a per-port entry.
  4. Only when 1–3 genuinely cannot apply does an entry here become justified.

An entry is JUSTIFIED ONLY IF it is irreducible after correct emission — i.e. the
divergence survives because the two languages genuinely cannot express the same thing,
not because the emitter hasn't folded the idiom yet. If emission COULD fold it, the
entry is a bug in this file; go fix the emitter.

Each entry MUST state WHY, concretely, in one of these forms:
  • ADDITION — this symbol exists in the port but not the reference. Answer: is it
    genuine port-only surface with NO reference twin (say what it is and why the
    reference has no equivalent), or is it IDIOM the emitter should have folded (then
    it does not belong here — fold it)? A convenience/alias/back-compat wrapper is NOT
    a justification.
  • OMISSION — this reference symbol has no port member. Answer: WHY can it not exist
    here — what specific language feature is absent (e.g. no async-context-manager
    protocol, no __init__ method protocol)? "impossible:" means the construct cannot
    be expressed at all; if it merely LOOKS different, that's idiom → fold it, don't
    omit it. Cite a precedent when one exists (e.g. RelayClient omits the same dunder).
  • SIGNATURE — the symbol matches by name but its parameters differ. Answer: is the
    difference a foldable idiom collapse (options-object, leading context/self,
    builder) — then EXPAND it in the signature emitter so names+count match, don't list
    it — or a genuine reference-only parameter with no cross-language analogue?

If you cannot write a crisp, specific WHY that survives the "could emission fold this?"
test, the entry is not ready. Prove it's needed before you add it.
═══════════════════════════════════════════════════════════════════════════════ -->

# PORT_OMISSIONS — Python symbols the Java SDK does not implement

Every symbol listed here is a public class, method or function present in the
Python reference (`porting-sdk/python_surface.json`) that this Java port does
not expose. Each entry records a genuine, specific rationale prefixed
`impossible:` (a real static-typing / decorator / async-protocol limit the
OO-idiom cousins TS+PHP also hit) or `approved:` (an explicit human sign-off,
per the §I.1 Python-only-not-ported ruling). The surface-diff gate rejects any
Python symbol missing from the Java SDK that is also missing from this file, and
rejects banned soft tags.

# Format: `<fully.qualified.symbol>: <rationale>`

signalwire.core.agent.tools.decorator.ToolDecorator: impossible: Python @tool class/instance decorator relies on the decorator protocol; Java has no method-decorator feature — tools register via defineTool(...) directly (TS + PHP omit as impossible)
signalwire.core.agent.tools.decorator.ToolDecorator.create_class_decorator: impossible: Python @tool class/instance decorator relies on the decorator protocol; Java has no method-decorator feature — tools register via defineTool(...) directly (TS + PHP omit as impossible)
signalwire.core.agent.tools.decorator.ToolDecorator.create_instance_decorator: impossible: Python @tool class/instance decorator relies on the decorator protocol; Java has no method-decorator feature — tools register via defineTool(...) directly (TS + PHP omit as impossible)
signalwire.core.agent_base.AgentBase.__init__: impossible: Java constructs AgentBase via the builder pattern (AgentBase.builder()...build()); the public entry point is the AgentBaseBuilder (a PORT_ADDITION), and the raw constructor is protected — a many-optional-arg public __init__ has no static-Java form (TS/other OO ports use the same builder idiom)
signalwire.core.agent_base.AgentBase.auto_map_sip_usernames: impossible: Python decorator-driven auto-mapping that inspects registered handler names at runtime; Java registers SIP usernames explicitly via AgentServer.registerSipUsername / route config — the reflection-driven auto-map FORM has no static-Java analog (TS/PHP map explicitly)
signalwire.core.agent_base.AgentBase.get_full_url: impossible: Python assembles the full callback URL from FastAPI request context (scheme/host/root_path); Java has no framework request-context object — the proxy base is set explicitly via manualSetProxyUrl and the URL composed at emit time (TS/PHP compose from their own framework context)
signalwire.core.contexts.Context.add_system_bullets: impossible: Python appends system-prompt bullets via a **kwargs POM mutation; Java's Context adds bullets through its typed API — the **kwargs system-bullets FORM has no static analog (TS/PHP add via typed builders)
signalwire.core.contexts.Context.add_system_section: impossible: Python injects a system-prompt SECTION onto a context via a runtime-dynamic POM mutation keyed by **kwargs; Java's Context exposes the same content through addBullets/typed setters — the **kwargs system-section FORM has no static analog (TS/PHP inject via typed section builders)
signalwire.core.contexts.create_simple_context: impossible: Python module-level convenience factory returning a ContextBuilder from **kwargs; Java uses new ContextBuilder()... directly — the free-function FORM has no static-Java analog (TS/PHP construct the builder directly likewise)
signalwire.core.data_map.create_expression_tool: impossible: Python module-level factory composing an expression tool from **kwargs + a Python callable pattern-map; Java's DataMap builds the same wire shape fluently via new DataMap(...).expression(...) — the free-function FORM has no static-Java analog (TS/PHP compose fluently likewise)
signalwire.core.data_map.create_simple_api_tool: impossible: Python module-level factory composing an API tool from **kwargs; Java builds the same wire shape fluently via new DataMap(...).webhook(...).output(...) — the free-function FORM has no static-Java analog (TS/PHP compose fluently likewise)
signalwire.core.mixins.mcp_server_mixin.MCPServerMixin: impossible: empty Python mixin container class (its methods are recorded on AIConfigMixin — add_mcp_server/enable_mcp_server, both present on Java's AgentBase); the bare mixin CLASS has no members and no Java counterpart (TS/PHP omit identically)
signalwire.core.mixins.serverless_mixin.ServerlessMixin: impossible: Python serverless-platform mixin (Lambda/GCF/Azure) dispatched by runtime environment detection; Java ships the Lambda runtime under signalwire.runtime.* (a PORT_ADDITION) — the mixin CLASS itself is a Python-composition artifact with no standalone Java form (TS/PHP handle serverless via their own runtime adapters)
signalwire.core.mixins.serverless_mixin.ServerlessMixin.handle_serverless_request: impossible: dispatches a serverless request by runtime-detecting the platform event shape; Java's Lambda runtime adapter (signalwire.runtime.*, a PORT_ADDITION) handles this per-platform — the single polymorphic-by-duck-typing entry has no static-Java form (TS/PHP use per-platform adapters)
signalwire.core.mixins.tool_mixin.ToolMixin.tool: impossible: Python @tool class/instance decorator relies on the decorator protocol; Java has no method-decorator feature — tools register via defineTool(...) directly (TS + PHP omit as impossible)
signalwire.core.security.webhook_middleware.make_webhook_validation_dependency: impossible: framework-bound factory returning a FastAPI dependency; Java has no web framework — the WebhookValidator static helpers are the native analog (a PORT_ADDITION); the FastAPI-dependency FORM has no Java equivalent (TS/PHP ship native middleware likewise)
signalwire.core.skill_base.SkillBase.__init__: impossible: SkillBase is a Java interface — interfaces cannot declare a constructor; skills are constructed via a no-arg implementation constructor + setup(params), so there is no __init__ member to enumerate (TS/PHP interfaces omit identically)
signalwire.core.swaig_function.SWAIGFunction.__init__: impossible: Java constructs SWAIGFunction via a builder (SWAIGFunctionBuilder) for its many optional args; the public entry is the builder, not a wide public constructor (TS/PHP use the same named-arg idiom)
signalwire.core.swml_service.SWMLService.__getattr__: impossible: Python runtime __getattr__ verb dispatch; Java's SWMLService expands every schema verb as an explicit statically-typed method, so there is no single catch-all member to enumerate (TS/PHP expand identically)
signalwire.relay.client.RelayClient.__aenter__: impossible: Python async-context-manager protocol dunder; Java uses explicit connect()/disconnect() — no __aenter__ equivalent (TS/PHP omit identically)
signalwire.relay.client.RelayClient.__aexit__: impossible: Python async-context-manager protocol dunder; Java uses explicit connect()/disconnect() (TS/PHP omit identically)
signalwire.relay.client.RelayClient.__del__: impossible: Python finalizer dunder; Java has no deterministic __del__ finalizer protocol (TS/PHP omit identically)
signalwire.relay.client.RelayClient.__init__: impossible: Java constructs RelayClient via a builder / factory (private constructor); the public entry is not a wide public constructor — a many-optional-arg __init__ has no static-Java form (TS/other OO ports use the same builder idiom)
signalwire.relay.client.RelayClient.relay_protocol: impossible: Python property exposing the internal relay-protocol object; Java keeps the protocol object private — internal plumbing, not public surface (TS/PHP omit identically)
signalwire.rest._base.CrudWithAddresses: impossible: abstract fabric CRUD+addresses mixin base — Java (like every port, AGENT_RULES L12) flattens list_addresses onto the 4 concrete fabric subclasses (CallFlows/ConferenceRooms/CxmlApplications/GenericResources) + FabricResource, so there is no standalone CrudWithAddresses base class to enumerate (TS/PHP flatten identically)
signalwire.rest._base.CrudWithAddresses.list_addresses: impossible: abstract fabric-base method — flattened onto the concrete fabric subclasses + FabricResource in Java (AGENT_RULES L12); no standalone base to carry it (TS/PHP flatten identically)
signalwire.rest._pagination.PaginatedIterator.__iter__: impossible: Python iterator-protocol dunder; Java's PaginatedIterator implements java.util.Iterator (hasNext/next) — the __iter__ NAME has no Java form (TS/PHP omit identically)
signalwire.rest._pagination.PaginatedIterator.__next__: impossible: Python iterator-protocol dunder; Java's PaginatedIterator implements java.util.Iterator.next() — the __next__ NAME has no Java form (TS/PHP omit identically)
signalwire.rest.client.RestClient.__init__: impossible: Java constructs RestClient via Signalwire.RestClient(...) / a builder (the raw constructor is not the public entry) — a many-optional-arg __init__ has no static-Java form (TS/other OO ports use the same idiom)
signalwire.skills.api_ninjas_trivia.skill.ApiNinjasTriviaSkill.__init__: impossible: Java skills use a no-arg constructor + setup(params) (registry factories require it); the Python __init__(agent, params) wide constructor has no static-Java equivalent (TS/PHP construct no-arg + setup likewise)
signalwire.skills.play_background_file.skill.PlayBackgroundFileSkill.__init__: impossible: Java skills use a no-arg constructor + setup(params) (registry factories require it); the Python __init__(agent, params) wide constructor has no static-Java equivalent (TS/PHP likewise)
signalwire.skills.spider.skill.SpiderSkill.__init__: impossible: Java skills use a no-arg constructor + setup(params) (registry factories require it); the Python __init__(agent, params) wide constructor has no static-Java equivalent (TS/PHP likewise)
signalwire.skills.weather_api.skill.WeatherApiSkill.__init__: impossible: Java skills use a no-arg constructor + setup(params) (registry factories require it); the Python __init__(agent, params) wide constructor has no static-Java equivalent (TS/PHP likewise)

# FOLDED (agentbase-family) omission keys for the SURFACE-DIFF AgentBase-mixin fold.
# The unfolded `signalwire.core.agent_base.AgentBase.*` / mixin twins above stay for
# the DRIFT/SIGNATURE gate (both key forms are load-bearing).
agentbase-family.__init__: impossible: Java constructs AgentBase via the builder pattern (AgentBase.builder()...build()); the public entry point is AgentBaseBuilder (a PORT_ADDITION) and the raw constructor is protected — a many-optional-arg public __init__ has no static-Java form (TS/other OO ports use the same builder idiom).
agentbase-family.auto_map_sip_usernames: impossible: Python decorator-driven auto-mapping that inspects registered handler names at runtime; Java registers SIP usernames explicitly via AgentServer.registerSipUsername / route config — the reflection-driven auto-map FORM has no static-Java analog (TS/PHP map explicitly).
agentbase-family.get_full_url: impossible: Python assembles the full callback URL from FastAPI request context (scheme/host/root_path); Java has no framework request-context object — the proxy base is set explicitly via manualSetProxyUrl and the URL composed at emit time (TS/PHP compose from their own framework context).
agentbase-family.handle_serverless_request: impossible: dispatches a serverless request by runtime-detecting the platform event shape; Java's Lambda runtime adapter (signalwire.runtime.*, a PORT_ADDITION) handles this per-platform — the single polymorphic-by-duck-typing entry has no static-Java form (TS/PHP use per-platform adapters).
agentbase-family.tool: impossible: Python @tool class/instance decorator relies on the decorator protocol; Java has no method-decorator feature — tools register via defineTool(...) directly (TS + PHP omit as impossible).

# B1 composition-attribute omissions (class-typed / per-instance attrs surfaced by the
# porting-sdk composition-attr enrichment; Java exposes no corresponding member).
signalwire.agent_server.AgentServer.agents: impossible: the reference records AgentServer with BOTH a bare `agents` dict attribute AND a `get_agents()` accessor (B1 enrichment surfaces the attribute); Java ships the single AgentServer.getAgents() accessor (→ the reference `get_agents` member) and holds no separate bare `agents` field member — wire-neutral (TS/PHP hit the same accessor idiom).
signalwire.core.swml_service.SWMLService.security: impossible: Python surfaces `security` as a SecurityConfig composition attribute on SWMLService (B1 enrichment); Java's SWML Service keeps its auth/security settings as internal builder-configured state with no public SecurityConfig accessor member on the service (wire-neutral; the WebService.security accessor folds via the getter idiom, but the SWML service exposes none — TS/PHP omit the same).

