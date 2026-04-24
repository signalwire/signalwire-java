#!/usr/bin/env python3
"""generate_exemptions.py — bootstrap PORT_OMISSIONS.md / PORT_ADDITIONS.md.

Runs the surface diff and writes one line per unexcused symbol with a
rationale chosen from a module-prefix lookup table. Re-running this script
after a PR regenerates the files — the explicit rationale table means the
files are reproducible and diff-reviewable.

Two hand-curated entries are seeded into PORT_OMISSIONS.md regardless of
what the diff shows (they are explicitly called out in the task): the
legacy ``assign_phone_route`` and the ``SwmlWebhooksResource.create`` /
``CxmlWebhooksResource.create`` holes that Java routes through
``phone_numbers.set_*`` instead.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path


# -- Rationale lookup ------------------------------------------------------
# Ordered: longest/most-specific prefix first; first match wins. Prefixes are
# matched against the symbol with ``startswith``.
#
# not_yet_implemented means: the symbol has no rationale for being omitted,
# but is also not yet ported. A future PR closes the gap.

OMISSION_RATIONALES: list[tuple[str, str]] = [
    # --- Hand-curated phone-binding omissions (from the task) ---
    (
        "signalwire.rest.namespaces.fabric.GenericResources.assign_phone_route",
        "narrow-use legacy API; Java ships only the good path via "
        "phone_numbers.set_* helpers, per porting-sdk/phone-binding.md",
    ),
    (
        "signalwire.rest.namespaces.fabric.SwmlWebhooksResource.create",
        "auto-materialized by phone_numbers.set_swml_webhook; Java doesn't "
        "expose the create method to avoid the trap",
    ),
    (
        "signalwire.rest.namespaces.fabric.CxmlWebhooksResource.create",
        "auto-materialized by phone_numbers.set_cxml_webhook; Java doesn't "
        "expose the create method to avoid the trap",
    ),
    # Class-level entries for the two webhook resources. Python's surface
    # lists them as empty classes, so the rationale is about why the class
    # itself is missing, not about any member.
    (
        "signalwire.rest.namespaces.fabric.SwmlWebhooksResource",
        "auto-materialized by phone_numbers.set_swml_webhook; Java doesn't "
        "expose the SwmlWebhooksResource class to avoid the trap of "
        "hand-materializing webhooks out of band — per porting-sdk/"
        "phone-binding.md",
    ),
    (
        "signalwire.rest.namespaces.fabric.CxmlWebhooksResource",
        "auto-materialized by phone_numbers.set_cxml_webhook; Java doesn't "
        "expose the CxmlWebhooksResource class to avoid the trap of "
        "hand-materializing webhooks out of band — per porting-sdk/"
        "phone-binding.md",
    ),
    (
        "signalwire.rest.namespaces.fabric.GenericResources",
        "Java routes generic Fabric operations through CrudResource "
        "accessors on FabricNamespace; the per-phone-route bind is "
        "handled by phone_numbers.set_* helpers per porting-sdk/"
        "phone-binding.md",
    ),

    # --- Whole subsystems intentionally not ported ---
    (
        "signalwire.search.",
        "search subsystem (local/pgvector document search) not ported — "
        "Java apps delegate search to managed services or an external "
        "Elasticsearch/Postgres setup",
    ),
    (
        "signalwire.agents.bedrock.",
        "Bedrock agent is not ported — Java ships AgentBase + SWML only; "
        "Bedrock integration is Python-specific",
    ),
    (
        "signalwire.livewire.",
        "LiveKit compatibility shim is Python-specific; Java apps interop "
        "with realtime systems directly via the SignalWire REST/RELAY APIs",
    ),
    (
        "signalwire.mcp_gateway.",
        "MCP gateway service is not ported; Java AgentBase exposes MCP "
        "client-side integration only",
    ),
    (
        "signalwire.web.web_service.",
        "Python's WebService abstraction is not a Java idiom — Java uses "
        "AgentBase's built-in HTTP server or the user's chosen framework",
    ),
    (
        "signalwire.pom.pom.",
        "POM (Prompt Object Model) is embedded directly in Java's "
        "AgentBase.setPromptPom / addPomSection helpers; no separate class",
    ),

    # --- CLI & tooling ---
    (
        "signalwire.cli.init_project.",
        "Python project scaffolder; Java users initialize with Gradle/Maven",
    ),
    (
        "signalwire.cli.dokku.",
        "Dokku deploy helper is a Python shell-wrapper; Java deploys via "
        "standard JVM tooling",
    ),
    (
        "signalwire.cli.core.",
        "Python CLI loader internals — the Java swaig-test CLI is "
        "self-contained and doesn't need these modules",
    ),
    (
        "signalwire.cli.execution.",
        "Python CLI executors for DataMap / webhook simulation — Java's "
        "SwaigTest covers the execution cases in-process",
    ),
    (
        "signalwire.cli.output.",
        "CLI pretty-print helpers; Java's SwaigTest prints via the standard "
        "logger",
    ),
    (
        "signalwire.cli.simulation.",
        "Python CLI simulation scaffolding; Java's ServerlessSimulator "
        "covers the subset the SwaigTest CLI drives",
    ),
    (
        "signalwire.cli.build_search.",
        "builds search-index artifacts for the Python search subsystem — "
        "not applicable (see signalwire.search omission)",
    ),
    (
        "signalwire.cli.types.",
        "CLI-internal typed dict shims; not exposed in any runtime API",
    ),
    (
        "signalwire.cli.",
        "Python-only CLI helper not mirrored in Java's SwaigTest",
    ),

    # --- Core internals / mixins ---
    (
        "signalwire.core.mixins.",
        "Python composes AgentBase from mixins; Java uses a single flat "
        "AgentBase class — the mixin methods are folded into AgentBase",
    ),
    (
        "signalwire.core.agent.prompt.manager.",
        "Python's PromptManager is embedded in Java's AgentBase prompt APIs",
    ),
    (
        "signalwire.core.agent.tools.decorator.",
        "Python function-decorator mechanism; Java uses AgentBase.defineTool "
        "with a lambda/method reference instead",
    ),
    (
        "signalwire.core.agent.tools.registry.",
        "Python's ToolRegistry is an internal detail of its decorator "
        "system; Java registers tools via AgentBase.defineTool directly",
    ),
    (
        "signalwire.core.auth_handler.",
        "Python AuthHandler class; Java folds auth into AgentBase's HTTP "
        "handler (basic auth via authUser/authPassword)",
    ),
    (
        "signalwire.core.config_loader.",
        "Python YAML/env config loader; Java uses standard Properties / "
        "application.yml patterns via the user's build tool",
    ),
    (
        "signalwire.core.pom_builder.",
        "PomBuilder helper merged into AgentBase's prompt APIs in Java",
    ),
    (
        "signalwire.core.security.session_manager.",
        "Sub-package alias for signalwire.core.security_config in Python; "
        "Java exposes SessionManager at signalwire.security.SessionManager",
    ),
    (
        "signalwire.core.security_config.",
        "Python security-config dataclass; Java exposes equivalent "
        "configuration via AgentBase builder methods",
    ),
    (
        "signalwire.core.security.",
        "Python security-internals shim; Java exposes the SessionManager "
        "equivalent via signalwire.security.SessionManager",
    ),
    (
        "signalwire.core.skill_base.",
        "Python SkillBase supports plugin-discovery patterns and async "
        "helpers not applicable to Java; Java's SkillBase is a leaner "
        "abstract class",
    ),
    (
        "signalwire.core.skill_manager.",
        "Java's SkillManager mirrors Python's public surface minus the "
        "plugin-discovery API (which relies on Python's entry_points)",
    ),
    (
        "signalwire.core.swaig_function.",
        "Python SWAIGFunction DTO; Java uses ToolDefinition/ToolHandler as "
        "the equivalent pair",
    ),
    (
        "signalwire.core.swml_builder.",
        "SWMLBuilder methods are consumed by AgentBase internally in Java; "
        "users build SWML via the Document/Service helpers",
    ),
    (
        "signalwire.core.swml_handler.",
        "Python's verb-handler registry is folded into Java's Schema and "
        "Document helpers",
    ),
    (
        "signalwire.core.swml_renderer.",
        "SWML rendering is owned by Java's Document class directly",
    ),
    (
        "signalwire.core.swml_service.",
        "Python's SWMLService wraps a stand-alone SWML endpoint; Java's "
        "AgentBase embeds the SWML-serving path directly",
    ),
    (
        "signalwire.core.contexts.Context.add_system_bullets",
        "Python helper; Java's Context.addBullets covers both prompt and "
        "system cases via explicit flags on Step",
    ),
    (
        "signalwire.core.contexts.Context.add_system_section",
        "Python helper; Java's Context.addSection covers both prompt and "
        "system cases via explicit flags on Step",
    ),
    (
        "signalwire.core.contexts.create_simple_context",
        "convenience factory present only in Python; Java users call new "
        "Context(name).addStep(...) directly",
    ),
    (
        "signalwire.core.agent_base.AgentBase.__init__",
        "constructor is package-private in Java — public initialization "
        "goes through AgentBase.builder() to enforce required fields",
    ),

    # --- REST namespaces: Java merges Python's *Resource/*Namespace pairs ---
    (
        "signalwire.rest._base.",
        "Python splits base resource classes across _base.py; Java exposes "
        "the equivalent via CrudResource / HttpClient / RestError",
    ),
    (
        "signalwire.rest._pagination.",
        "Python pagination iterator; Java returns raw Maps and users drive "
        "pagination via query params on CrudResource.list()",
    ),
    (
        "signalwire.rest.client.RestClient.__init__",
        "Java's RestClient constructor is package-private — public "
        "initialization goes through RestClient.builder()",
    ),
    (
        "signalwire.rest.namespaces.fabric.",
        "Java's FabricNamespace exposes subresources through "
        "CrudResource accessors instead of Python's per-subresource class; "
        "see rest/docs/fabric.md for the mapping",
    ),
    (
        "signalwire.rest.namespaces.compat.",
        "Java exposes the Compat API through a flat CompatNamespace + "
        "CrudResource accessors; Python splits into one class per resource",
    ),
    (
        "signalwire.rest.namespaces.video.",
        "Java exposes the Video API through a flat VideoNamespace + "
        "CrudResource accessors; Python splits into one class per resource",
    ),
    (
        "signalwire.rest.namespaces.calling.",
        "Java exposes the Calling API through a flat CallingNamespace + "
        "CrudResource accessors; Python splits into one class per resource",
    ),
    (
        "signalwire.rest.namespaces.registry.",
        "Java exposes the 10DLC/TCR registry through a flat "
        "CampaignNamespace + CrudResource accessors; Python splits into "
        "one class per subresource",
    ),
    (
        "signalwire.rest.namespaces.phone_numbers.PhoneNumbersResource.__init__",
        "Java merges PhoneNumbersResource into PhoneNumbersNamespace, "
        "whose constructor is package-private — access via "
        "RestClient.phoneNumbers()",
    ),
    (
        "signalwire.rest.namespaces.phone_numbers.",
        "Java merges PhoneNumbersResource into PhoneNumbersNamespace; the "
        "namespace exposes all helpers via RestClient.phoneNumbers().set_*",
    ),
    (
        "signalwire.rest.namespaces.logs.",
        "Java ships a flat LogsNamespace that routes to the voice/message/"
        "fax/conference logs; Python splits into one class per log family",
    ),
    (
        "signalwire.rest.namespaces.project.",
        "Java merges Project tokens/config into a flat ProjectNamespace",
    ),
    (
        "signalwire.rest.namespaces.addresses.",
        "Java's FabricNamespace.addresses() returns a CrudResource that "
        "covers AddressesResource's surface",
    ),
    (
        "signalwire.rest.namespaces.chat.",
        "Java's ChatNamespace exposes the equivalent operations via "
        "CrudResource accessors",
    ),
    (
        "signalwire.rest.namespaces.datasphere.",
        "Java exposes the Datasphere API through DatasphereNamespace + "
        "CrudResource accessors",
    ),
    (
        "signalwire.rest.namespaces.imported_numbers.",
        "Java's PhoneNumbersNamespace covers imported numbers via the "
        "standard list/create flow",
    ),
    (
        "signalwire.rest.namespaces.lookup.",
        "Java's NumberLookupNamespace exposes the equivalent operations",
    ),
    (
        "signalwire.rest.namespaces.mfa.",
        "MFA API not yet exposed as a first-class namespace in Java — "
        "available via RestClient HTTP primitives",
    ),
    (
        "signalwire.rest.namespaces.number_groups.",
        "Number-groups API not yet exposed as a first-class namespace in "
        "Java — available via RestClient HTTP primitives",
    ),
    (
        "signalwire.rest.namespaces.pubsub.",
        "Java exposes Pub/Sub through PubSubNamespace with list/create "
        "operations on a CrudResource",
    ),
    (
        "signalwire.rest.namespaces.queues.",
        "Java's QueueNamespace exposes the queues API via CrudResource "
        "accessors",
    ),
    (
        "signalwire.rest.namespaces.recordings.",
        "Java's RecordingNamespace exposes the recordings API via "
        "CrudResource accessors",
    ),
    (
        "signalwire.rest.namespaces.short_codes.",
        "Short codes not yet exposed as a first-class namespace in Java — "
        "available via RestClient HTTP primitives",
    ),
    (
        "signalwire.rest.namespaces.sip_profile.",
        "SIP profile API not yet exposed as a first-class namespace in "
        "Java — available via RestClient HTTP primitives",
    ),
    (
        "signalwire.rest.namespaces.verified_callers.",
        "Verified callers not yet exposed as a first-class namespace in "
        "Java — available via RestClient HTTP primitives",
    ),
    (
        "signalwire.rest.namespaces.",
        "Python-only REST namespace not yet ported to Java",
    ),

    # --- Skills ---
    (
        "signalwire.skills.web_search.skill_improved.",
        "Python internal experiment; not a public API",
    ),
    (
        "signalwire.skills.web_search.skill_original.",
        "Python legacy version; not a public API",
    ),
    (
        "signalwire.skills.web_search.",
        "Java ships a lighter web-search skill; Python's scraper variant "
        "classes are Python-specific",
    ),
    (
        "signalwire.skills.datasphere_serverless.",
        "Java ships DatasphereServerlessSkill with equivalent one-liner "
        "surface; Python exposes additional internal helpers",
    ),
    (
        "signalwire.skills.datasphere.",
        "Java ships DatasphereSkill with equivalent one-liner surface; "
        "Python exposes additional internal helpers",
    ),
    (
        "signalwire.skills.api_ninjas_trivia.",
        "Java ships ApiNinjaTriviaSkill with equivalent one-liner surface",
    ),
    (
        "signalwire.skills.mcp_gateway.",
        "Java's McpGatewaySkill proxies to a running gateway — no gateway "
        "code is bundled, consistent with signalwire.mcp_gateway omission",
    ),
    (
        "signalwire.skills.swml_transfer.",
        "Java's SwmlTransferSkill exposes the transfer helper with a "
        "simplified public surface",
    ),
    (
        "signalwire.skills.registry.",
        "Java's SkillRegistry mirrors the Python registry but exposes a "
        "narrower public surface (register/list only)",
    ),
    (
        "signalwire.skills.",
        "Java ships a leaner skill class with the same public surface; "
        "helper methods from Python's skill modules are inlined",
    ),

    # --- Prefabs ---
    (
        "signalwire.prefabs.",
        "Python prefab exposes additional internal helpers not needed in "
        "Java's equivalent prefab class",
    ),

    # --- Relay internals ---
    (
        "signalwire.relay.call.",
        "Java's Call class exposes the equivalent surface; the listed "
        "Python method is an internal helper or uses a Python-specific "
        "signature (e.g. kwargs) that has no direct Java analog",
    ),
    (
        "signalwire.relay.client.",
        "Java's RelayClient builder provides equivalent configuration; "
        "Python exposes additional internal helpers",
    ),
    (
        "signalwire.relay.event.",
        "Java's RelayEvent family has the equivalent static-class "
        "hierarchy; the Python method is an internal accessor",
    ),
    (
        "signalwire.relay.message.",
        "Python Message exposes additional internal helpers; Java's Message "
        "sticks to the public send/reply API",
    ),

    # --- Utilities ---
    (
        "signalwire.utils.schema_utils.",
        "Python's schema utils include load/validate helpers used by the "
        "SWMLService; Java does schema loading inline in Document/Schema",
    ),

    # --- Top-level re-exports ---
    (
        "signalwire.RestClient",
        "Python's ``from signalwire import RestClient`` is a re-export; "
        "Java users import com.signalwire.sdk.rest.RestClient directly",
    ),
    (
        "signalwire.add_skill_directory",
        "Python top-level skill-discovery helper; Java registers skills "
        "via SkillRegistry directly",
    ),
    (
        "signalwire.list_skills",
        "Python top-level helper; Java exposes SkillRegistry.list()",
    ),
    (
        "signalwire.list_skills_with_params",
        "Python top-level helper; Java exposes SkillRegistry.list() and "
        "users inspect each registered skill for params",
    ),
    (
        "signalwire.register_skill",
        "Python top-level helper; Java exposes SkillRegistry.register()",
    ),
    (
        "signalwire.run_agent",
        "Python top-level helper; Java users call AgentBase.run() on their "
        "built agent",
    ),
    (
        "signalwire.start_agent",
        "Python top-level helper; Java users call AgentBase.run() or "
        "AgentServer.register() + run()",
    ),

    # --- Other specific symbols ---
    (
        "signalwire.core.agent.tools.type_inference.",
        "Python's runtime type-inference from function annotations is not "
        "applicable — Java's defineTool requires an explicit "
        "ToolDefinition with parameter types",
    ),
    (
        "signalwire.core.agent_base.AgentBase.auto_map_sip_usernames",
        "Python convenience that auto-registers all public methods as SIP "
        "usernames; Java's static typing makes that unsafe — users call "
        "registerSipUsername explicitly",
    ),
    (
        "signalwire.core.agent_base.AgentBase.get_full_url",
        "Python constructs the full-URL string for self-referencing "
        "webhooks; Java's AgentBase exposes host/port/route accessors so "
        "users assemble the URL as needed",
    ),
    (
        "signalwire.core.data_map.DataMap.webhook_expressions",
        "Python ergonomic wrapper over addWebhook + addExpression; Java's "
        "DataMap.addWebhook / addExpression cover the same ground",
    ),
    (
        "signalwire.core.data_map.create_expression_tool",
        "Python factory function; Java users instantiate DataMap and call "
        "addExpression directly",
    ),
    (
        "signalwire.core.data_map.create_simple_api_tool",
        "Python factory function; Java users instantiate DataMap and call "
        "addWebhook directly",
    ),
    (
        "signalwire.core.logging_config.",
        "Python logging bootstrap helpers; Java uses java.util.logging "
        "configured via standard logging.properties — equivalent surface "
        "is exposed through signalwire.sdk.logging.Logger",
    ),
    (
        "signalwire.pom.pom_tool.",
        "Python CLI for rendering POM files; Java embeds POM rendering "
        "directly in AgentBase's prompt builder with no separate CLI",
    ),
    (
        "signalwire.utils.is_serverless_mode",
        "Python helper; Java uses ExecutionMode.isServerless() on the "
        "injected ExecutionMode instance",
    ),
    (
        "signalwire.utils.url_validator.validate_url",
        "Python URL-validation helper used by SWMLService; Java's "
        "Document.addExternal / addWebhook validate URLs at call time",
    ),

    # --- AgentServer extras ---
    (
        "signalwire.agent_server.AgentServer.get_agents",
        "Java's AgentServer exposes getRoutes() and getAgent(route); a "
        "bulk getAgents() listing is redundant",
    ),
    (
        "signalwire.agent_server.AgentServer.register_global_routing_callback",
        "Python global-routing callback hook; Java apps install equivalent "
        "behaviour via AgentServer.register() with a routed AgentBase",
    ),
    (
        "signalwire.agent_server.AgentServer.register_sip_username",
        "SIP routing by username is configured on AgentBase directly in "
        "Java (registerSipUsername), not at the AgentServer level",
    ),
    (
        "signalwire.agent_server.AgentServer.setup_sip_routing",
        "Java's AgentServer.registerSipRoute covers the SIP setup in one "
        "method; setup_sip_routing is a Python-specific split",
    ),
]


def rationale_for(sym: str) -> str:
    """Return the first-matching rationale, or ``not_yet_implemented`` if "
    there isn't one. Exact symbol matches win over prefix matches.
    """
    # Exact match first
    for prefix, rationale in OMISSION_RATIONALES:
        if sym == prefix:
            return rationale
    # Then prefix match
    for prefix, rationale in OMISSION_RATIONALES:
        if prefix.endswith(".") and sym.startswith(prefix):
            return rationale
    return ("not_yet_implemented: Python surface has no matching Java "
            "symbol — a follow-up PR will add equivalent functionality")


# -- Additions rationales --------------------------------------------------

ADDITIONS_RATIONALES: list[tuple[str, str]] = [
    (
        "signalwire.runtime.env_provider.",
        "Java Lambda support — injectable env-var abstraction required "
        "because Java can't mutate System.getenv() at runtime",
    ),
    (
        "signalwire.runtime.execution_mode.",
        "Java Lambda support — distinguishes HTTP-server vs Lambda handler "
        "execution so AgentBase can skip HTTP bootstrap in serverless mode",
    ),
    (
        "signalwire.runtime.lambda_url_resolver.",
        "Java Lambda support — resolves the Lambda Function URL at runtime "
        "to populate AgentBase's self-URL (Python uses env vars directly)",
    ),
    (
        "signalwire.runtime.lambda.",
        "Java Lambda support — AWS Lambda RequestStreamHandler adapter; "
        "Python ships a thin WSGI-level adapter instead",
    ),
    (
        "signalwire.agent.agent_base_builder.",
        "Java builder pattern — public constructor is package-private, "
        "initialization goes through AgentBase.builder()",
    ),
    (
        "signalwire.agent.agent_base_dynamic_config_callback.",
        "Java functional interface for AgentBase.setDynamicConfigCallback "
        "(Python uses bare callables)",
    ),
    (
        "signalwire.rest.rest_error.",
        "Java exception class — Python exposes SignalWireRestError under "
        "signalwire.rest._base; Java names it RestError for idiomatic "
        "reasons",
    ),
    (
        "signalwire.rest.phone_call_handler.",
        "Java enum mirroring Python's PhoneCallHandler constants (the "
        "Python class is effectively an enum via class attributes)",
    ),
    (
        "signalwire.relay.relay_client_builder.",
        "Java builder pattern — RelayClient.builder() is the idiomatic "
        "constructor",
    ),
    (
        "signalwire.rest.client.RestClient.Builder",
        "Java builder pattern — RestClient.builder() is the idiomatic "
        "constructor; RestClient's constructor is package-private",
    ),
    (
        "signalwire.rest.client.RestClient.builder",
        "Java builder pattern — RestClient.builder() exposes the idiomatic "
        "constructor entry point",
    ),
    (
        "signalwire.agent.agent_base.AgentBase.builder",
        "Java builder pattern — AgentBase.builder() exposes the idiomatic "
        "constructor entry point",
    ),
    (
        "signalwire.relay.constants.",
        "Java constants class grouping RELAY protocol constants used by "
        "RelayClient; Python uses module-level attributes",
    ),
    (
        "signalwire.logging.logger_level.",
        "Java enum for log severity; Python uses the stdlib logging module",
    ),
    (
        "signalwire.logging.logger.",
        "Java wraps java.util.logging with a uniform API; Python re-exports "
        "stdlib logging directly",
    ),
    (
        "signalwire.cli.swaig_test.",
        "Java swaig-test CLI entry point; Python's equivalent is the "
        "swaig-test executable in signalwire.cli",
    ),
    (
        "signalwire.cli.simulation.serverless_simulator_platform.",
        "Java enum for ServerlessSimulator.Platform; Python uses string "
        "constants",
    ),
    (
        "signalwire.cli.simulation.mock_env.ServerlessSimulator.parse_platform",
        "Java helper exposed for CLI flag parsing; Python uses argparse "
        "choices directly",
    ),
    (
        "signalwire.cli.simulation.mock_env.ServerlessSimulator.preset_for",
        "Java helper exposed for CLI preset lookup; Python inlines the "
        "lookup in its ServerlessSimulator constructor",
    ),
    (
        "signalwire.rest.namespaces.phone_numbers_namespace.",
        "Java's PhoneNumbersNamespace merges Python's "
        "PhoneNumbersResource into a namespace-level class",
    ),
    (
        "signalwire.rest.namespaces.billing_namespace.",
        "Java's BillingNamespace exposes the billing API one-liner; Python "
        "accesses billing via the compat namespace",
    ),
    (
        "signalwire.rest.namespaces.campaign_namespace.",
        "Java's CampaignNamespace is the equivalent of Python's "
        "RegistryNamespace for 10DLC/TCR registration",
    ),
    (
        "signalwire.rest.namespaces.chat_namespace.",
        "Java's ChatNamespace is the namespace-level accessor for the "
        "Chat API; Python has a flat ChatResource class",
    ),
    (
        "signalwire.rest.namespaces.compliance_namespace.",
        "Java's ComplianceNamespace exposes TCR/carrier compliance APIs; "
        "Python routes these through RegistryNamespace",
    ),
    (
        "signalwire.rest.namespaces.conference_namespace.",
        "Java's ConferenceNamespace wraps the conference API with a "
        "namespace-level accessor; Python routes through CompatConferences",
    ),
    (
        "signalwire.rest.namespaces.fax_namespace.",
        "Java's FaxNamespace exposes fax APIs; Python routes through "
        "CompatFaxes under the compat namespace",
    ),
    (
        "signalwire.rest.namespaces.messaging_namespace.",
        "Java's MessagingNamespace is the namespace-level accessor for "
        "messaging; Python routes through CompatMessages",
    ),
    (
        "signalwire.rest.namespaces.number_lookup_namespace.",
        "Java's NumberLookupNamespace wraps the number-lookup API; Python "
        "exposes a flat LookupResource",
    ),
    (
        "signalwire.rest.namespaces.pub_sub_namespace.",
        "Java's PubSubNamespace wraps the Pub/Sub API; Python exposes a "
        "flat PubSubResource",
    ),
    (
        "signalwire.rest.namespaces.queue_namespace.",
        "Java's QueueNamespace wraps the queues API; Python exposes a "
        "flat QueuesResource",
    ),
    (
        "signalwire.rest.namespaces.recording_namespace.",
        "Java's RecordingNamespace wraps the recordings API; Python "
        "exposes a flat RecordingsResource",
    ),
    (
        "signalwire.rest.namespaces.sip_namespace.",
        "Java's SipNamespace exposes SIP-endpoint configuration; Python "
        "routes through SubscribersResource on the Fabric namespace",
    ),
    (
        "signalwire.rest.namespaces.stream_namespace.",
        "Java's StreamNamespace wraps the media-stream API; Python "
        "routes through the compat namespace",
    ),
    (
        "signalwire.rest.namespaces.swml_namespace.",
        "Java's SwmlNamespace exposes SWML-endpoint management as a "
        "dedicated namespace; Python routes through the SwmlWebhooks "
        "resource on the Fabric namespace",
    ),
    (
        "signalwire.rest.namespaces.transcription_namespace.",
        "Java's TranscriptionNamespace wraps the transcription API as a "
        "dedicated namespace; Python routes through the compat namespace",
    ),
    (
        "signalwire.rest.call_handler.PhoneCallHandler.from_wire",
        "Java enum static helper to construct a PhoneCallHandler from its "
        "wire string representation",
    ),
    (
        "signalwire.rest.call_handler.PhoneCallHandler.get_call_handler_key",
        "Java enum accessor exposing the wire 'call_handler' key — used "
        "internally by phoneNumbers().update(...)",
    ),
    (
        "signalwire.rest.call_handler.PhoneCallHandler.get_companion_field",
        "Java enum accessor exposing the required companion field name "
        "for each call_handler value",
    ),
    (
        "signalwire.rest.call_handler.PhoneCallHandler.get_wire_value",
        "Java enum accessor exposing the wire-level string value; Python "
        "uses the class attribute directly",
    ),
    (
        "signalwire.rest.call_handler.PhoneCallHandler.value_of_wire",
        "Java enum static helper mirroring Enum.valueOf but keyed on the "
        "wire representation",
    ),
    (
        "signalwire.rest.call_handler.PhoneCallHandler.values",
        "Java enum's compiler-generated values() accessor; Python iterates "
        "via __members__",
    ),
    (
        "signalwire.rest.call_handler.PhoneCallHandler.__init__",
        "Java enum constructor — takes wire value + companion field; "
        "Python uses class attributes instead",
    ),
    (
        "signalwire.agent_server.AgentServer.stop",
        "Java explicit server-stop method; Python's AgentServer stops when "
        "its process is signalled",
    ),
    (
        "signalwire.agent_server.AgentServer.get_routes",
        "Java accessor exposing the registered-agent route list for "
        "diagnostics",
    ),
    (
        "signalwire.agent_server.AgentServer.get_sip_route",
        "Java accessor exposing the registered SIP route for diagnostics",
    ),
    (
        "signalwire.agent_server.AgentServer.register_sip_route",
        "Java's split of Python's setup_sip_routing into explicit "
        "per-username registration",
    ),
    (
        "signalwire.agent_server.AgentServer.set_static_files_dir",
        "Java setter for the static-files directory; Python configures "
        "this through AgentServer constructor arguments",
    ),
]


def rationale_for_addition(sym: str) -> str:
    # Exact match
    for prefix, rationale in ADDITIONS_RATIONALES:
        if sym == prefix:
            return rationale
    # Prefix match
    for prefix, rationale in ADDITIONS_RATIONALES:
        if prefix.endswith(".") and sym.startswith(prefix):
            return rationale
    return ("idiomatic Java surface extension (builder, getter/setter, "
            "or overload) not present in Python")


# -- IO --------------------------------------------------------------------

def run_diff(diff_script: Path, reference: Path, port_surface: Path) -> dict:
    """Run the diff in --json mode and return its parsed payload.

    The diff script exits 1 when drift exists; that's fine here — we're
    generating the exemption files from that drift.
    """
    result = subprocess.run(
        [
            sys.executable, str(diff_script),
            "--reference", str(reference),
            "--port-surface", str(port_surface),
            "--json",
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode not in (0, 1):
        raise SystemExit(
            f"diff_port_surface.py failed (code {result.returncode}): "
            f"{result.stderr}"
        )
    return json.loads(result.stdout)


def write_exemption_file(
    path: Path, title: str, intro: str,
    symbols: list[str], rationale: callable,
) -> None:
    """Write an ordered markdown file keyed on symbol name."""
    lines: list[str] = []
    lines.append(f"# {title}\n")
    lines.append(intro.rstrip() + "\n")
    lines.append("")
    lines.append("# Format: `<fully.qualified.symbol>: <rationale>`")
    lines.append(
        "# Regenerate with `python3 scripts/generate_exemptions.py` after")
    lines.append("# a surface change.")
    lines.append("")
    for sym in sorted(symbols):
        lines.append(f"{sym}: {rationale(sym)}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo", type=Path, default=Path(__file__).resolve().parent.parent,
    )
    parser.add_argument(
        "--porting-sdk", type=Path,
        default=Path.home() / "src" / "porting-sdk",
    )
    args = parser.parse_args(argv)

    # Ensure fresh surface.
    subprocess.check_call(
        [
            sys.executable,
            str(args.repo / "scripts" / "enumerate_surface.py"),
            "--output", str(args.repo / "port_surface.json"),
            "--reference", str(args.porting_sdk / "python_surface.json"),
        ]
    )

    payload = run_diff(
        args.porting_sdk / "scripts" / "diff_port_surface.py",
        args.porting_sdk / "python_surface.json",
        args.repo / "port_surface.json",
    )

    omissions = payload["unexcused_missing"]
    additions = payload["unexcused_extra"]

    write_exemption_file(
        args.repo / "PORT_OMISSIONS.md",
        title="PORT_OMISSIONS — Python symbols the Java SDK does not implement",
        intro=(
            "Every symbol listed here is a public class, method or function "
            "present in the Python reference (`porting-sdk/python_surface.json`) "
            "that this Java port deliberately does not expose. Each entry "
            "records a one-line rationale; the Phase 13 surface audit in CI "
            "will reject any Python symbol missing from the Java SDK that is "
            "also missing from this file.\n\n"
            "Entries marked `not_yet_implemented:` are honest — a future PR "
            "will close the gap. Everything else is intentional divergence "
            "with a design reason."
        ),
        symbols=omissions,
        rationale=rationale_for,
    )
    write_exemption_file(
        args.repo / "PORT_ADDITIONS.md",
        title="PORT_ADDITIONS — Java-only public symbols with no Python equivalent",
        intro=(
            "Symbols here exist in the Java SDK but have no matching entry "
            "in the Python reference. These fall into three buckets:\n"
            "  1. Java-idiom necessities (builders, enums, explicit getters).\n"
            "  2. Java Lambda support (the `signalwire.runtime.*` tree).\n"
            "  3. Refactors where Java merged Python's split classes "
            "(`*Namespace` vs `*Resource`).\n\n"
            "Every entry must carry a rationale. Reviewers use this file to "
            "catch accidental additions."
        ),
        symbols=additions,
        rationale=rationale_for_addition,
    )
    print(
        f"Wrote {len(omissions)} omission(s) and {len(additions)} "
        f"addition(s)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
