#!/usr/bin/env python3
"""enumerate_surface.py — emit a Python-shaped surface JSON for the Java SDK.

Walks ``src/main/java/com/signalwire/sdk/**/*.java`` and produces a JSON file
matching the shape of ``porting-sdk/python_surface.json``. Class names stay
as-is (``AgentBase``, ``FunctionResult``, ...); method names are translated
from Java camelCase to Python snake_case (``setPromptText`` →
``set_prompt_text``); constructors ``ClassName(...)`` become ``__init__``;
``toString`` becomes ``__repr__``. Only ``public`` members are emitted —
package-private, ``protected`` and ``private`` are skipped.

Module paths use the **Python reference module names** so symbols line up in
``diff_port_surface.py``. For example Java's
``com.signalwire.sdk.agent.AgentBase`` is emitted under
``signalwire.core.agent_base`` because that is the Python-reference home of
``AgentBase``. Classes with no Python-reference equivalent (port-only, e.g.
``EnvProvider``) get a naturally-translated module path rooted at
``signalwire.*``.

Two emission modes:

- **Python-reference** (default, ``--output port_surface.json``) — method
  names translated to snake_case so ``diff_port_surface.py`` can diff
  against ``python_surface.json``. Used for Layer B parity auditing.
- **Native names** (``--native``, ``--output port_surface_native.json``) —
  method names kept in Java camelCase (``setPromptText``,
  ``addSkill``). Used for Layer C doc↔code alignment auditing via
  ``audit_docs.py``, which extracts method-call patterns from
  ``docs/`` and ``examples/*.java`` in their natural Java form.

Usage::

    python3 scripts/enumerate_surface.py                      # stdout
    python3 scripts/enumerate_surface.py --output port_surface.json
    python3 scripts/enumerate_surface.py --check --output port_surface.json
    python3 scripts/enumerate_surface.py --native --output port_surface_native.json
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path


# ---------------------------------------------------------------------------
# Python-reference module lookup, seeded from porting-sdk/python_surface.json.
# A Java class translates to the Python module that owns the same class name.
# ---------------------------------------------------------------------------

def build_class_to_module_map(reference_json: Path) -> dict[str, str]:
    """Return {ClassName: python.module} from the reference surface."""
    mapping: dict[str, str] = {}
    data = json.loads(reference_json.read_text(encoding="utf-8"))
    for mod, entry in data.get("modules", {}).items():
        for cls in entry.get("classes", {}):
            # First writer wins; if a class name appears twice we keep the
            # earliest alphabetical module (deterministic).
            if cls not in mapping or mod < mapping[cls]:
                mapping[cls] = mod
    return mapping


# ---------------------------------------------------------------------------
# Name translation helpers.
# ---------------------------------------------------------------------------

# Java nested/class renames → Python-reference name. Used when the Java port
# uses a structurally identical class under a different name (e.g. relay
# events keep a ``Call`` prefix in Java for grouping, Python drops it).
_CLASS_RENAMES: dict[str, str] = {
    # Java's SWML ``Service`` is the reference ``SWMLService`` (mirrors
    # enumerate_signatures.py::JAVA_EXTRA_RENAMES). The FQN override below routes
    # it to ``signalwire.core.swml_service``; this rename makes it compare
    # against the reference's ``SWMLService`` class name. The ~44 extra Java verb
    # methods are PORT_ADDITIONS; the reference-only delegator methods that Java
    # folds onto its Document/AgentBase are excused omissions (parity with the
    # SIGNATURE gate, which renames + excuses the same set).
    "Service": "SWMLService",
    # Built-in skill class-name spelling folds (mirror
    # enumerate_signatures.py::JAVA_SKILL_RENAMES) so the port's Java-spelled
    # skill class compares against the reference's canonical name.
    "DatasphereSkill": "DataSphereSkill",
    "DatasphereServerlessSkill": "DataSphereServerlessSkill",
    "ApiNinjaTriviaSkill": "ApiNinjasTriviaSkill",
    "DatetimeSkill": "DateTimeSkill",
    "SwmlTransferSkill": "SWMLTransferSkill",
    # Java's idiomatic McpGatewaySkill → the reference's MCPGatewaySkill
    # (Python upper-cases the MCP acronym). Rename, not omission (RULES.md §2).
    "McpGatewaySkill": "MCPGatewaySkill",
    # RELAY action-class folds. Java's ``PlayAndCollectAction`` (prefix
    # ``play_and_collect``, control surface stop/pause/resume/volume +
    # start_input_timers) is the reference's ``CollectAction``; Java's
    # ``CollectAction`` (prefix ``collect``, stop + start_input_timers) is the
    # reference's ``StandaloneCollectAction``. Java's inbound ``ReceiveFaxAction``
    # is the reference's ``FaxAction`` (SendFaxAction stays a port addition).
    # Rename-not-omission.
    "PlayAndCollectAction": "CollectAction",
    "CollectAction": "StandaloneCollectAction",
    "ReceiveFaxAction": "FaxAction",
    "CallDialEvent": "DialEvent",
    "CallPlayEvent": "PlayEvent",
    "CallRecordEvent": "RecordEvent",
    "CallDetectEvent": "DetectEvent",
    "CallCollectEvent": "CollectEvent",
    "CallFaxEvent": "FaxEvent",
    "CallTapEvent": "TapEvent",
    "CallStreamEvent": "StreamEvent",
    "CallTranscribeEvent": "TranscribeEvent",
    "CallConnectEvent": "ConnectEvent",
    "CallReferEvent": "ReferEvent",
    "CallSendDigitsEvent": "SendDigitsEvent",
    "CallPayEvent": "PayEvent",
    "MessagingReceiveEvent": "MessageReceiveEvent",
    "MessagingStateEvent": "MessageStateEvent",
    "AiAction": "AIAction",
    # Java merges Python's ``<Foo>Resource`` CRUD wrapper into its
    # ``<Foo>Namespace`` accessor — expose the namespace under both names so
    # Python's ``set_*`` helpers on the Resource line up.
    "PhoneNumbersNamespace": "PhoneNumbersResource",
    # Same idiom: Java exposes top-level helpers as <Foo>Namespace while the
    # Python reference exposes them as <Foo>Resource.  Rename so the audit
    # walks the same canonical class.
    "AddressesNamespace": "AddressesResource",
    "ImportedNumbersNamespace": "ImportedNumbersResource",
    "MfaNamespace": "MfaResource",
    "ShortCodesNamespace": "ShortCodesResource",
    "SipProfileNamespace": "SipProfileResource",
    "NumberGroupsNamespace": "NumberGroupsResource",
    # QueueNamespace and RecordingNamespace also map onto Python's
    # <Foo>Resource (Python files: queues.py::QueuesResource,
    # recordings.py::RecordingsResource).
    "QueueNamespace": "QueuesResource",
    "RecordingNamespace": "RecordingsResource",
    # Java's FabricNamespace.FabricSubscribers nested class corresponds to
    # Python's fabric.SubscribersResource.
    "FabricSubscribers": "SubscribersResource",
    # Java's RestError is the canonical port-only error; Python names it
    # SignalWireRestError.
    "RestError": "SignalWireRestError",
}

# The 6 generated namespace-container classes (package
# ...rest.namespaces.generated). The oracle's ``_client_tree_generated`` module
# records these with ONLY ``__init__``; their Java lazy-accessor methods are the
# client-tree wiring the oracle does not compare. Restrict them to ``__init__``.
_GENERATED_CONTAINERS = {
    "FabricNamespace", "VideoNamespace", "LogsNamespace",
    "RegistryNamespace", "ProjectNamespace", "DatasphereNamespace",
}

# Generated-resource bases whose subclasses inherit create + update (the oracle
# records exactly {create, update} on each such subclass; get/list/delete stay
# on the base module). ReadResource/BaseResource contribute no implicit route.
_CRUD_LIKE_BASES = {"CrudResource", "FabricResource", "FabricResourcePUT"}

# ---------------------------------------------------------------------------
# Generated wire-type / read-side-payload surface (item A/H + D). scripts/
# generate_rest.py emits one method-less Java data class / enum per
# components/schemas OBJECT into
# ``com.signalwire.sdk.rest.namespaces.generated.types.<sub>``; the SWML-verbs /
# RELAY-protocol / SWAIG-payload generators emit into their own packages. The
# reference records these as method-less type definitions in the
# ``<ns>_types_generated`` / ``swml_verbs_generated`` / ``protocol_types_generated`` /
# ``*_generated`` modules. Routing is BY JAVA PACKAGE (not class name) because the
# same type name recurs across namespaces (AIObject in calling AND fabric; the
# shared Types_StatusCodes_* in every namespace) and even collides with existing
# SDK class names (DataMap/Document/Section) — so the package route MUST win over
# class_to_module for these files (§H item 3). The SURFACE-DIFF gen-type leaf fold
# collapses the cross-module duplicates to one gen-type.<Leaf> on both ref and port.
#
# Each entry maps the Java package to the oracle module. The REST types share a
# common parent package with a per-namespace leaf segment; the SWML/RELAY/SWAIG
# groups have a single package each. Kept in sync with generate_rest.TYPE_NS and the
# three read-side generators.
_GEN_TYPE_PKG_PREFIX = "com.signalwire.sdk.rest.namespaces.generated.types."
_GEN_TYPE_REST_SUB_TO_MODULE = {
    "relayrest": "signalwire.rest.namespaces.relay_rest_types_generated",
    "fabric": "signalwire.rest.namespaces.fabric_types_generated",
    "calling": "signalwire.rest.namespaces.calling_types_generated",
    "video": "signalwire.rest.namespaces.video_types_generated",
    "datasphere": "signalwire.rest.namespaces.datasphere_types_generated",
    "logs": "signalwire.rest.namespaces.logs_types_generated",
    "message": "signalwire.rest.namespaces.message_types_generated",
    "messages": "signalwire.rest.namespaces.messages_types_generated",
    "voice": "signalwire.rest.namespaces.voice_types_generated",
    "fax": "signalwire.rest.namespaces.fax_types_generated",
    "project": "signalwire.rest.namespaces.project_types_generated",
    "projects": "signalwire.rest.namespaces.projects_types_generated",
    "chat": "signalwire.rest.namespaces.chat_types_generated",
    "pubsub": "signalwire.rest.namespaces.pubsub_types_generated",
    "swmlwebhooks": "signalwire.rest.namespaces.swml_webhooks_types_generated",
}
# Single-package read-side generated modules (D2/D1/relay-proto).
_GEN_TYPE_PKG_TO_MODULE = {
    "com.signalwire.sdk.swml.generated": "signalwire.core.swml_verbs_generated",
    "com.signalwire.sdk.relay.generated": "signalwire.relay.protocol_types_generated",
    "com.signalwire.sdk.swaig.generated.postprompt": "signalwire.core.post_prompt_generated",
    "com.signalwire.sdk.swaig.generated.swaigrequest": "signalwire.core.swaig_request_generated",
    "com.signalwire.sdk.swaig.generated.swaigactions": "signalwire.core.swaig_actions_generated",
}
# generate_rest.type_name suffixes a class name with `_` when its leaf collides with
# a hard Java keyword (Goto/Switch/… — actually only lowercase are keywords, so this
# is rare for PascalCase class names) OR a java.lang built-in type (``Record`` →
# ``Record_``, JavaLangClash). The gen-type enumerators strip that suffix back to the
# bare oracle leaf (the type-name analog of the reserved-word field rename / the TS
# BUILTIN_COLLISION_RENAME). Kept in sync with generate_rest.JAVA_KEYWORDS +
# JAVA_BUILTIN_COLLISION.
_GEN_TYPE_COLLISION_NAMES = {
    # java.lang built-ins that clash (PascalCase class names never hit a lowercase
    # keyword, so this set is the java.lang collision set generate_rest suffixes).
    "Record", "String", "Integer", "Long", "Double", "Boolean", "Object", "Void",
    "Number", "Character", "Byte", "Short", "Float", "System", "Thread", "Runnable",
    "Comparable", "Cloneable", "Iterable", "Error", "Exception", "Class", "Enum",
    "Math", "Process", "Runtime", "Package", "Module", "Override", "Deprecated",
    "SuppressWarnings", "FunctionalInterface", "SafeVarargs",
}


def _gen_type_unrename(name: str) -> str:
    """A generated-type class name generate_rest suffixed with ``_`` on a builtin
    collision (``Record_`` → ``Record``) renames back to the bare oracle leaf."""
    if name.endswith("_") and name[:-1] in _GEN_TYPE_COLLISION_NAMES:
        return name[:-1]
    return name


def _gen_type_module(java_package: str) -> str | None:
    """If ``java_package`` is a generated wire-type / read-side-payload package,
    return its oracle module; else None."""
    if java_package in _GEN_TYPE_PKG_TO_MODULE:
        return _GEN_TYPE_PKG_TO_MODULE[java_package]
    if java_package.startswith(_GEN_TYPE_PKG_PREFIX):
        sub = java_package[len(_GEN_TYPE_PKG_PREFIX):].split(".", 1)[0]
        return _GEN_TYPE_REST_SUB_TO_MODULE.get(sub)
    return None

# ``public class Foo extends Bar {`` header of a generated resource. Read from
# the RAW source (comments/imports already name ``BaseResource`` etc., so match
# the class-declaration line specifically).
_GENERATED_EXTENDS_RE = re.compile(
    r"\bpublic\s+(?:final\s+|abstract\s+)*class\s+[A-Z]\w*\s+extends\s+([A-Z]\w*)"
)


def _generated_extends(raw_src: str) -> str | None:
    """Return the direct superclass name of the file's top-level generated
    resource class, or None if it declares no ``extends`` (a container /
    command-dispatch class)."""
    m = _GENERATED_EXTENDS_RE.search(raw_src)
    return m.group(1) if m else None

_CAMEL_RE_1 = re.compile(r"(.)([A-Z][a-z]+)")
_CAMEL_RE_2 = re.compile(r"([a-z0-9])([A-Z])")
_PY_KEYWORDS = {"pass", "class", "def", "from", "import", "return", "yield",
                "global", "lambda", "raise", "try", "with", "async", "await"}

# Java method name → Python-reference method name. Applied after the usual
# camel→snake translation to bridge idiomatic naming gaps (Java's
# ``toMap`` vs Python's ``to_dict``).
_METHOD_RENAMES: dict[str, str] = {
    "to_map": "to_dict",
    # Java's boolean getter idiom prefixes ``is`` — the reference records the
    # SchemaUtils accessor as ``full_validation_available`` (no ``is_``).
    "is_full_validation_available": "full_validation_available",
    # Action/Message ``await()`` is the reference ``wait`` — Java cannot name a
    # method ``wait`` (java.lang.Object.wait is final and non-overridable), so
    # the reserved-name escape is ``await`` (rename-not-omission; wire behavior
    # identical). Only Action/Message declare ``await`` so the global rename is
    # unambiguous, and it applies to BOTH enumerators (signatures imports
    # _METHOD_RENAMES) so the surface and signature gates agree. The key is
    # ``await_`` because ``await`` is a Python keyword: translate_method_name
    # snake-cases ``await`` then appends ``_`` (keyword escape) BEFORE consulting
    # this table, so the pre-rename name seen here is ``await_``.
    "await_": "wait",
    # Java's ``pubSub`` field snake-cases to ``pub_sub``; Python keeps it
    # as a single token ``pubsub``.
    "pub_sub": "pubsub",
    # Python ``AgentBase.pom`` is a @property; Java exposes it as the
    # getter ``getPom()``. Project the Java getter onto the Python name.
    "get_pom": "pom",
    # Python ``SWMLService.schema_utils`` is a public attribute exposed
    # via Java's ``getSchemaUtils()`` getter.  Strip the get_ prefix.
    "get_schema_utils": "schema_utils",
    # Python's RestClient property names (rest.client.RestClient.X) are
    # served by Java's ``getX()`` accessor — strip the ``get_`` prefix so
    # both surfaces line up at the same canonical name.
    "get_chat": "chat",
    "get_pubsub": "pubsub",
    "get_fabric": "fabric",
    "get_video": "video",
    "get_logs": "logs",
    "get_messaging": "messaging",
    "get_phone_numbers": "phone_numbers",
    "get_addresses": "addresses",
    "get_subscribers": "subscribers",
    "get_imported_numbers": "imported_numbers",
    "get_mfa": "mfa",
    "get_number_groups": "number_groups",
    "get_short_codes": "short_codes",
    "get_sip_profile": "sip_profile",
    "get_registry": "registry",
}

# Fully-qualified-class → Python module overrides (item H). MIRRORS
# enumerate_signatures.py's JAVA_MODULE_OVERRIDES so the surface and signature
# gates route every class to the SAME reference module. The surface enumerator
# had drifted: it relied ONLY on the class-name→module map built from
# python_surface.json (build_class_to_module_map), which picks the
# alphabetically-first module when a class NAME appears in several reference
# modules. That misroutes a hand class whose name collides with a generated
# DTO — e.g. the real ``com.signalwire.sdk.pom.Section`` was routed to
# ``signalwire.core.swml_verbs_generated`` (a generated ``Section`` DTO lives
# there and sorts first) instead of ``signalwire.pom.pom``; likewise the real
# SWML ``Document`` collided with the datasphere generated ``Document``. These
# FQN overrides WIN over the class-name map (checked first in
# java_to_python_module) so the collision is resolved deterministically by the
# Java package, exactly as the signature enumerator does it.
#
# Keyed by the Java FQN (``package.ClassName``). Kept in sync with
# enumerate_signatures.py::JAVA_MODULE_OVERRIDES plus the item-I subsystem
# classes implemented this turn (routed to their reference core modules).
_JAVA_SURFACE_MODULE_OVERRIDES: dict[str, str] = {
    # Collisions with generated DTOs / classes absent from the ref name-map.
    # Java's rich ``Service`` (62 schema-driven verb + SWAIG + serve methods)
    # is a port-only fold that PROVIDES the reference SWMLService surface plus
    # the verb builders; it keeps its port-only home ``signalwire.swml.service``
    # (all PORT_ADDITIONS) while the reference ``SWMLService`` class is projected
    # from it (see _SWML_SERVICE_PROJECTION). Java's ``Document`` (the SWML doc
    # model) collides by NAME with the datasphere generated ``Document`` DTO —
    # pin it to a port-only home so it does not leak into that generated module.
    "com.signalwire.sdk.swml.SWMLService": "signalwire.core.swml_service",
    "com.signalwire.sdk.swml.Document": "signalwire.swml.document",
    "com.signalwire.sdk.pom.Section": "signalwire.pom.pom",
    "com.signalwire.sdk.pom.PromptObjectModel": "signalwire.pom.pom",
    "com.signalwire.sdk.logging.Logger": "signalwire.core.logging_config",
    "com.signalwire.sdk.swaig.ToolDefinition": "signalwire.core.swaig_function",
    "com.signalwire.sdk.swaig.ToolHandler": "signalwire.core.swaig_function",
    "com.signalwire.sdk.swaig.FunctionResult": "signalwire.core.function_result",
    "com.signalwire.sdk.skills.SkillBase": "signalwire.core.skill_base",
    "com.signalwire.sdk.skills.SkillManager": "signalwire.core.skill_manager",
    "com.signalwire.sdk.contexts.Context": "signalwire.core.contexts",
    "com.signalwire.sdk.contexts.ContextBuilder": "signalwire.core.contexts",
    "com.signalwire.sdk.contexts.Step": "signalwire.core.contexts",
    "com.signalwire.sdk.contexts.GatherInfo": "signalwire.core.contexts",
    "com.signalwire.sdk.contexts.GatherQuestion": "signalwire.core.contexts",
    "com.signalwire.sdk.datamap.DataMap": "signalwire.core.data_map",
    # Item-I subsystem classes implemented this turn — route the new Java
    # classes to their reference core modules (class name matches the reference
    # leaf verbatim, but these packages differ from the natural fallback).
    "com.signalwire.sdk.swaig.SWAIGFunction": "signalwire.core.swaig_function",
    "com.signalwire.sdk.agents.BedrockAgent": "signalwire.agents.bedrock",
    "com.signalwire.sdk.core.agent.prompt.PromptManager":
        "signalwire.core.agent.prompt.manager",
    "com.signalwire.sdk.core.agent.tools.ToolRegistry":
        "signalwire.core.agent.tools.registry",
    "com.signalwire.sdk.swml.SWMLBuilder": "signalwire.core.swml_builder",
    "com.signalwire.sdk.swml.SwmlRenderer": "signalwire.core.swml_renderer",
    "com.signalwire.sdk.swml.SWMLVerbHandler": "signalwire.core.swml_handler",
    "com.signalwire.sdk.swml.AIVerbHandler": "signalwire.core.swml_handler",
    "com.signalwire.sdk.swml.VerbHandlerRegistry": "signalwire.core.swml_handler",
    "com.signalwire.sdk.web.WebService": "signalwire.web.web_service",
    "com.signalwire.sdk.core.ConfigLoader": "signalwire.core.config_loader",
    "com.signalwire.sdk.core.SecurityConfig": "signalwire.core.security_config",
    "com.signalwire.sdk.core.AuthHandler": "signalwire.core.auth_handler",
    "com.signalwire.sdk.core.PomBuilder": "signalwire.core.pom_builder",
}

# Free-function projection at surface level (mirrors FREE_FUNCTION_PROJECTIONS
# in enumerate_signatures.py). Static methods on certain Java helper classes
# are projected to module-level free functions so they line up with Python's
# free-function namespace. Each entry: java FQN class + Java method name →
# (target python module, target python free function).
#
# The signature audit already projects these at the signature layer; the
# surface enumerator needs the same projection to avoid `signalwire.RestClient`
# / `signalwire.register_skill` showing up as missing-port at the surface
# level. Without the projection, Java's class methods appear under
# `signalwire.signalwire.Signalwire.*` and Python's module-level
# `signalwire.RestClient` appears as missing.
_FREE_FUNCTION_SURFACE_PROJECTIONS: dict[tuple[str, str], tuple[str, str]] = {
    # signalwire.signalwire.Signalwire static helpers → signalwire.X
    ("Signalwire", "RestClient"): ("signalwire", "RestClient"),
    ("Signalwire", "registerSkill"): ("signalwire", "register_skill"),
    ("Signalwire", "addSkillDirectory"): ("signalwire", "add_skill_directory"),
    ("Signalwire", "listSkillsWithParams"): ("signalwire", "list_skills_with_params"),
    ("Signalwire", "listSkills"): ("signalwire", "list_skills"),
    # ExecutionMode helpers
    ("ExecutionMode", "getExecutionMode"): ("signalwire.core.logging_config", "get_execution_mode"),
    # logging_config module-level free functions — Java groups them on the
    # Logger static-helper class; project back to the reference free-function
    # names (mirrors enumerate_signatures.py's FREE_FUNCTION_PROJECTIONS).
    ("Logger", "getLogger"): ("signalwire.core.logging_config", "get_logger"),
    ("Logger", "configureLogging"): ("signalwire.core.logging_config", "configure_logging"),
    ("Logger", "resetLoggingConfiguration"):
        ("signalwire.core.logging_config", "reset_logging_configuration"),
    ("Logger", "stripControlChars"): ("signalwire.core.logging_config", "strip_control_chars"),
    ("ExecutionMode", "isServerlessMode"): ("signalwire.utils", "is_serverless_mode"),
    # UrlValidator
    ("UrlValidator", "validateUrl"): ("signalwire.utils.url_validator", "validate_url"),
    # RelayEvent.parseEvent → module-level signalwire.relay.event.parse_event
    # (Python ships it as a free function; Java groups it as a static factory).
    ("RelayEvent", "parseEvent"): ("signalwire.relay.event", "parse_event"),
    # SecurityUtils static helpers → signalwire.core.security.security_utils
    # free functions (the Python reference exports them as bare module
    # functions; Java groups them on a static-only utility class).
    ("SecurityUtils", "filterSensitiveHeaders"):
        ("signalwire.core.security.security_utils", "filter_sensitive_headers"),
    ("SecurityUtils", "redactUrl"):
        ("signalwire.core.security.security_utils", "redact_url"),
    ("SecurityUtils", "isValidHostname"):
        ("signalwire.core.security.security_utils", "is_valid_hostname"),
    # WebhookValidator static helpers → signalwire.core.security.webhook_validator
    # free functions (mirrors FREE_FUNCTION_PROJECTIONS in enumerate_signatures.py).
    ("WebhookValidator", "validateWebhookSignature"):
        ("signalwire.core.security.webhook_validator", "validate_webhook_signature"),
    ("WebhookValidator", "validateRequest"):
        ("signalwire.core.security.webhook_validator", "validate_request"),
    # WebhookValidator.validate → the framework-free decomposed webhook-validation
    # core in signalwire.core.security.webhook_middleware (mirrors the same
    # projection in enumerate_signatures.py). The WebhookFilter servlet wrapper on
    # top of it stays a PORT_ADDITION idiom.
    ("WebhookValidator", "validate"):
        ("signalwire.core.security.webhook_middleware", "validate"),
    # TypeInference static helpers → signalwire.core.agent.tools.type_inference
    # free functions (mirrors FREE_FUNCTION_PROJECTIONS in enumerate_signatures.py).
    # Java has no runtime lambda reflection; the typed params come from the
    # ParameterSchema builder and inferSchema decomposes that built schema.
    ("TypeInference", "inferSchema"):
        ("signalwire.core.agent.tools.type_inference", "infer_schema"),
    ("TypeInference", "createTypedHandlerWrapper"):
        ("signalwire.core.agent.tools.type_inference", "create_typed_handler_wrapper"),
    # RequestOptionsSupport static helpers → signalwire.rest._request_options free
    # functions (mirrors FREE_FUNCTION_PROJECTIONS in enumerate_signatures.py). The
    # reference exports resolve / status_is_retryable as bare module functions; Java
    # has no module-level free functions, so they live on a static-only facade and
    # are lifted back to the canonical free-function home.
    ("RequestOptionsSupport", "resolve"):
        ("signalwire.rest._request_options", "resolve"),
    ("RequestOptionsSupport", "statusIsRetryable"):
        ("signalwire.rest._request_options", "status_is_retryable"),
}


# Mixin projection at surface level (mirrors MIXIN_PROJECTIONS in
# enumerate_signatures.py). Python composes AgentBase from several mixin
# classes (AIConfigMixin, PromptMixin, ...); Java collapses them into a
# flat AgentBase. The signatures adapter projects each mixin's method set
# onto its canonical Python mixin path so the cross-port signature audit
# sees the same symbol locations. This table is the surface-level analog,
# applied after class collection in ``enumerate_sdk``.
#
# Scope: only methods whose mixin-path entry is NOT already documented in
# PORT_OMISSIONS.md / PORT_ADDITIONS.md need a projection here. Most legacy
# mixin methods (``add_hint`` etc.) are exposed on AgentBase and documented
# as omissions; their AgentBase home is documented in PORT_ADDITIONS.md.
# Methods newly added to Python (``set_language_params`` /
# ``get_language_params`` from python 029ca6f) are projected so their
# AIConfigMixin path matches Python's reference without expanding the
# omissions/additions ledger.
#
# Each entry: (target_python_module, target_python_class) → [snake_case
# method names already present on Java's AgentBase]. The projection emits
# the method at the mixin path and removes it from AgentBase so the diff
# does not flag a port-side extra.
_MIXIN_SURFACE_PROJECTIONS: dict[tuple[str, str], list[str]] = {
    ("signalwire.core.mixins.ai_config_mixin", "AIConfigMixin"): [
        "add_function_include", "add_hint", "add_hints", "add_internal_filler",
        "add_language", "add_mcp_server", "add_pattern_hint", "add_pronunciation",
        "enable_debug_events", "enable_mcp_server", "get_language_params",
        "set_function_includes", "set_global_data", "set_internal_fillers",
        "set_language_params", "set_languages", "set_multilingual",
        "set_native_functions", "set_param", "set_params",
        "set_post_prompt_llm_params", "set_prompt_llm_params",
        "set_pronunciations", "update_global_data",
    ],
    ("signalwire.core.mixins.prompt_mixin", "PromptMixin"): [
        "contexts", "define_contexts", "get_post_prompt", "get_prompt",
        "prompt_add_section",
        "prompt_add_subsection", "prompt_add_to_section",
        "prompt_has_section", "reset_contexts", "set_post_prompt",
        "set_prompt_pom", "set_prompt_text",
    ],
    ("signalwire.core.mixins.skill_mixin", "SkillMixin"): [
        "add_skill", "has_skill", "list_skills", "remove_skill",
    ],
    ("signalwire.core.mixins.tool_mixin", "ToolMixin"): [
        "define_tool", "define_tools", "on_function_call",
        "register_swaig_function",
    ],
    ("signalwire.core.mixins.auth_mixin", "AuthMixin"): [
        "validate_basic_auth", "get_basic_auth_credentials",
    ],
    ("signalwire.core.mixins.web_mixin", "WebMixin"): [
        "as_router", "enable_debug_routes", "get_app", "manual_set_proxy_url",
        "on_request", "on_swml_request", "register_routing_callback", "run",
        "serve", "set_dynamic_config_callback", "setup_graceful_shutdown",
    ],
    ("signalwire.core.mixins.state_mixin", "StateMixin"): [
        "validate_tool_token",
    ],
}


# Per-(module, class) method-NAME aliases: Java-idiom method name → the
# reference's method name so the two compare EQUAL (Rule 2 — reconcile idiom in
# the enumerator, not via an omission). Applied per class during module
# assignment. Currently: the SWMLBuilder ``verb(name, config)`` catch-all is the
# Java analog of the reference's runtime ``__getattr__`` verb dispatch (Java is
# statically typed and has no ``__getattr__``, so a single explicit catch-all
# method fills that role — the same way the Ruby port projects ``method_missing``).
_SURFACE_METHOD_ALIASES: dict[tuple[str, str], dict[str, str]] = {
    ("signalwire.core.swml_builder", "SWMLBuilder"): {"verb": "__getattr__"},
    # SWAIGFunction's ``call(args, rawData)`` is the Java analog of the
    # reference's Python callable protocol ``__call__`` (Java has no callable
    # object protocol — a named method fills that role).
    ("signalwire.core.swaig_function", "SWAIGFunction"): {"call": "__call__"},
}


# Idiom-scaffolding classes to DROP from the compared surface. These are the
# Java expression of a Python kwargs bundle / return tuple / value object — a
# static-typing NECESSITY, not reference surface: options-builders (the Java
# NAMED-param idiom for a method with many optional kwargs), validation-RESULT
# records (Python returns a ``(bool, list)`` tuple; Java returns a small
# record), and credential/exception value types nested inside a handler. The
# reference has no counterpart for any of them, so — like the generated
# ``<Method>Request``/``Builder`` scaffolding already dropped in §8 — they are
# excluded here rather than laundered as PORT_ADDITIONS. Keyed by the
# fully-qualified SURFACE class name (outer-qualified for nested types, exactly
# as parse_type_body emits them).
_SURFACE_EXCLUDED_CLASSES: set[str] = {
    # Options-builder for SwmlRenderer's many-optional-param static methods.
    "SwmlRendererRenderOptions",
    # Options-builder for SWAIGFunction's many-optional-param constructor.
    "SWAIGFunctionBuilder",
    # Validation-result records (Python returns a (valid, errors) tuple).
    "SWMLVerbHandlerValidationResult",
    "SWAIGFunctionValidationResult",
    "SecurityConfigValidationResult",
    # AuthHandler nested value/handler types (credentials, exception, response,
    # framework-neutral request-handler wrapper) — Python uses plain dicts /
    # framework decorators; Java models them as small nested types.
    "AuthHandlerAuthException",
    "AuthHandlerAuthResult",
    "AuthHandlerBasicCredentials",
    "AuthHandlerBearerCredentials",
    "AuthHandlerRequestHandler",
    "AuthHandlerResponse",
    # SWAIGFunction handler @FunctionalInterface + ToolRegistry nested tool type.
    "SWAIGFunctionHandler",
    "ToolRegistryTool",
    # WebhookValidator.validate's reject-triple return record — the Java native
    # stand-in for the language-neutral (status, headers, body) tuple the
    # decomposed webhook_middleware.validate core returns (Python uses a plain
    # tuple; .NET a ValueTuple). Idiom-scaffolding nested value type.
    "WebhookValidatorWebhookRejection",
    # Service.handleRequest's (status, headers, body) return record — the Java
    # native stand-in for the language-neutral (status, headers, body) tuple the
    # framework-free handle_request dispatch core returns (Python uses a plain
    # tuple; .NET a ValueTuple). Idiom-scaffolding nested value type. (Named
    # SWMLServiceHttpResult after the Service→SWMLService class rename.)
    "SWMLServiceHttpResult",
    # TypeInference.inferSchema's (parameters, required, description, isTyped,
    # hasRawData) return record — the Java native stand-in for the language-neutral
    # 5-tuple Python's infer_schema returns. Idiom-scaffolding nested value type.
    "TypeInferenceInferredSchema",
    # TypeInference static-utility host class — its two methods (inferSchema /
    # createTypedHandlerWrapper) are projected to the module-level free functions
    # signalwire.core.agent.tools.type_inference.{infer_schema,create_typed_handler_wrapper}
    # via _FREE_FUNCTION_SURFACE_PROJECTIONS. Unlike SecurityUtils/WebhookValidator
    # (whose Java package maps to a DIFFERENT module than the projection target),
    # TypeInference's package IS the canonical module, so the host class would
    # otherwise leak into that module alongside the projected functions. Python has
    # only the free functions (classes: {}); drop the host class.
    "TypeInference",
    # RequestOptions envelope (plan 4.2) idiom-scaffolding types with no reference
    # class counterpart:
    #  - RequestOptionsBuilder: the Java NAMED-param builder for the reference's
    #    keyword-only RequestOptions dataclass constructor (like SWAIGFunctionBuilder).
    #  - AbortSignal: the @FunctionalInterface cooperative-cancellation primitive, the
    #    port's stand-in for the reference's PRIVATE _AbortSignal protocol (like
    #    SWAIGFunctionHandler).
    #  - RequestOptionsSupport: the static-only free-function host — its resolve /
    #    statusIsRetryable methods are projected to the module-level free functions
    #    signalwire.rest._request_options.{resolve,status_is_retryable} via
    #    _FREE_FUNCTION_SURFACE_PROJECTIONS. Its package IS the canonical module, so
    #    (like TypeInference) the host class would otherwise leak alongside the
    #    projected functions; Python has only the free functions, so drop the host.
    #  - RequestOptionsSupportEffectiveOptions: the nested value record standing in for
    #    the reference's PRIVATE _EffectiveOptions (like SWMLServiceHttpResult).
    "RequestOptionsBuilder",
    "AbortSignal",
    "RequestOptionsSupport",
    "RequestOptionsSupportEffectiveOptions",
}


def camel_to_snake(name: str) -> str:
    """``setPromptText`` → ``set_prompt_text``; ``URL`` stays ``url``."""
    s1 = _CAMEL_RE_1.sub(r"\1_\2", name)
    s2 = _CAMEL_RE_2.sub(r"\1_\2", s1).lower()
    # Collapse accidental double-underscores from all-caps runs.
    while "__" in s2 and not (s2.startswith("__") and s2.endswith("__")):
        s2 = s2.replace("__", "_")
    return s2


def translate_method_name(java_name: str, class_name: str,
                           native: bool = False) -> str | list[str] | None:
    """Java method → Python-reference method name, or None if skipped.

    - Constructors (``ClassName``) map to ``__init__``.
    - ``toString`` maps to ``__repr__``.
    - Everything else: camelCase → snake_case.
    - If the result collides with a Python keyword (``pass``), a trailing
      underscore is added (``pass_``) — this matches signalwire-python's
      convention for ``Call.pass_``.

    When ``native=True`` the method appears under BOTH names — its Java
    identifier (``addSkill``) and its Python-reference form (``add_skill``).
    This lets ``audit_docs.py`` resolve calls written in either convention:
    Java examples naturally use camelCase, while the reference docs imported
    from ``porting-sdk`` (``docs/agent_guide.md``, ``rest/docs/calling.md``,
    etc.) carry snake_case Python snippets. Both refer to the same port
    method. A doc-audit "phantom" is only a phantom when neither form
    resolves.
    """
    if native:
        names: list[str] = [java_name]
        # Also include the Python-reference form so snake_case doc snippets
        # resolve. Skip ``__init__``/``__repr__`` — those Python-only
        # dunders never appear as ``.something()`` call patterns in docs.
        snake = camel_to_snake(java_name)
        if snake in _PY_KEYWORDS:
            snake += "_"
        snake = _METHOD_RENAMES.get(snake, snake)
        if snake != java_name:
            names.append(snake)
        return names
    if java_name == class_name:
        return "__init__"
    if java_name == "toString":
        return "__repr__"
    snake = camel_to_snake(java_name)
    if snake in _PY_KEYWORDS:
        snake += "_"
    return _METHOD_RENAMES.get(snake, snake)


# ---------------------------------------------------------------------------
# Java source parser (regex-based, no external deps).
#
# We intentionally avoid pulling in a full Java parser: the shape we need is
# narrow — top-level public type, nested public types, and public method /
# constructor signatures. javac-style comment-stripping + a balanced-brace
# walker is enough.
# ---------------------------------------------------------------------------

_LINE_COMMENT = re.compile(r"//[^\n]*")
_BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)
_STRING_LIT = re.compile(r'"(?:\\.|[^"\\])*"')
_CHAR_LIT = re.compile(r"'(?:\\.|[^'\\])*'")


def strip_comments_and_strings(src: str) -> str:
    """Replace comments and string/char literals with same-length placeholders.

    We care about structural tokens (``public``, ``class``, ``{``, ``}``).
    Comments and string contents can contain those tokens and would confuse
    a brace walker; blanking them is safer than matching around them.

    This is a SINGLE-PASS state machine (not sequential regexes). A multi-pass
    approach blanks line-comments before strings, so a ``//`` INSIDE a string
    literal (e.g. ``"https://" + x``) is mistaken for a comment and the rest of
    the line — including the string's closing quote and any braces — is eaten,
    desyncing the brace walker so later methods leak (AGENT_RULES L20). The
    state machine only recognises a comment/string opener when NOT already
    inside a string/char/comment, so ``//`` and ``/*`` inside a literal are
    left as literal content (which is itself blanked). Text blocks (``\"\"\"``)
    are handled too. Length is preserved so downstream indices stay aligned.
    """
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        # Block comment.
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append(" " * (j - i))
            i = j
            continue
        # Line comment.
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i))
            i = j
            continue
        # Text block (\"\"\" ... \"\"\").
        if c == '"' and src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            # Keep the delimiters; blank the (newline-containing) interior,
            # preserving newlines so line numbers/anchors stay aligned.
            interior = src[i + 3 : max(i + 3, j - 3)]
            blanked = "".join("\n" if ch == "\n" else " " for ch in interior)
            out.append('"""' + blanked + '"""')
            i = j
            continue
        # String literal.
        if c == '"':
            j = i + 1
            while j < n and src[j] != '"':
                if src[j] == "\\":
                    j += 2
                else:
                    j += 1
            j = min(j + 1, n)
            out.append('"' + " " * max(0, j - i - 2) + '"')
            i = j
            continue
        # Char literal.
        if c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                if src[j] == "\\":
                    j += 2
                else:
                    j += 1
            j = min(j + 1, n)
            out.append("'" + " " * max(0, j - i - 2) + "'")
            i = j
            continue
        out.append(c)
        i += 1
    return "".join(out)


# Matches a ``public class/interface/enum/record Name`` header.
_TYPE_HEADER = re.compile(
    r"\bpublic\s+"
    r"(?:static\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+)*"
    r"(?P<kind>class|interface|enum|record|@interface)\s+"
    r"(?P<name>[A-Z][A-Za-z0-9_]*)"
)

# A ``private``/``protected``/package-private nested type declaration. Its body
# is NOT surface (a port-only helper) and — crucially — its members must never
# leak up to the enclosing class: without this, the walker skips the non-public
# type HEADER (which _TYPE_HEADER requires ``public``) yet still matches the
# ``@Override public`` methods inside it with _METHOD_HEADER and attributes them
# to the enclosing class (Service.asRouter's private RouteCollector extends
# HttpServer and re-declares bind/createContext/start/... — which then surfaced
# as bogus SWMLService members). Detect such a header and skip its whole body.
_NONPUBLIC_TYPE_HEADER = re.compile(
    r"\b(?:private|protected)\s+"
    r"(?:static\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+)*"
    r"(?P<kind>class|interface|enum|record|@interface)\s+"
    r"(?P<name>[A-Za-z_$][\w$]*)"
)

# Matches a ``public ... methodName(...)`` or constructor signature. The type
# can be arbitrarily complex (generics, arrays, qualified names), so we allow
# any run of non-special characters up to the identifier + ``(``.
_METHOD_HEADER = re.compile(
    r"\bpublic\s+"
    r"(?:static\s+|final\s+|abstract\s+|synchronized\s+|native\s+|default\s+|strictfp\s+)*"
    r"(?:<[^>]+>\s+)?"                                  # generic params
    r"(?:[\w.$<>,\[\]\s?]+\s+)?"                        # return type (omitted for ctors)
    r"(?P<name>[A-Za-z_$][\w$]*)"
    r"\s*\("
)

# Interface members are implicitly public — they carry no ``public`` keyword
# (``default List<String> getHints()`` / ``String getVerbName();``). Match a
# method declaration anchored at a statement boundary (start of line or after
# ``{``/``}``/``;``), optionally led by ``default``/``static``/``abstract``,
# then a return type and the method name + ``(``. Declarations led by
# ``private`` or ``protected`` are NOT public interface API and are filtered in
# code (the modifier alternation below does not admit them; a private helper
# starting with ``private`` won't reach the return-type group as a public one).
_INTERFACE_METHOD_HEADER = re.compile(
    r"(?:^|[{};])\s*"
    r"(?P<mods>(?:default\s+|static\s+|abstract\s+|strictfp\s+|final\s+)*)"
    r"(?:<[^>]+>\s+)?"                                  # generic params
    r"(?P<rtype>[A-Za-z_$][\w.$<>,\[\]?\s]*?\s+)"       # return type (required)
    r"(?P<name>[A-Za-z_$][\w$]*)"
    r"\s*\(",
    re.MULTILINE,
)


def find_matching_brace(src: str, open_idx: int) -> int:
    """Return the index of the ``}`` matching ``src[open_idx] == '{'``."""
    depth = 0
    i = open_idx
    while i < len(src):
        ch = src[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(src) - 1  # unclosed; shouldn't happen in well-formed code


def parse_type_body(
    src: str, outer_name: str,
    known_python_classes: set[str] | None = None,
    java_outer_name: str | None = None,
    native: bool = False,
    is_interface: bool = False,
) -> dict[str, dict]:
    """Parse a class/interface/enum body. Returns ``{ClassName: [methods]}``
    for the outer class and any public nested classes found inside it.

    Nested classes that appear in ``known_python_classes`` (e.g.
    ``PlayAction`` nested inside ``Action``) keep their bare name so they
    line up with the Python reference. Nested classes not in Python get
    their parent prepended (``AgentBase.Builder`` → ``AgentBaseBuilder``) so
    multiple port-only ``Builder`` inner classes don't collide at the same
    module path.

    ``java_outer_name`` is the bare Java class name used for constructor
    detection (``public Foo(...)`` → ``__init__``); distinct from the
    exposed class name when we've prepended the parent.
    """
    known_python_classes = known_python_classes or set()
    if java_outer_name is None:
        java_outer_name = outer_name
    classes: dict[str, list[str]] = {outer_name: []}
    methods: list[str] = classes[outer_name]

    # Walk the body extracting public methods + public nested types. In an
    # interface, members are implicitly public and carry no ``public`` keyword,
    # so use the interface-aware header (and skip private/protected helpers).
    method_re = _INTERFACE_METHOD_HEADER if is_interface else _METHOD_HEADER
    i = 0
    while i < len(src):
        m_type = _TYPE_HEADER.search(src, i)
        m_meth = method_re.search(src, i)
        m_priv = _NONPUBLIC_TYPE_HEADER.search(src, i)

        # Pick whichever comes first.
        next_type_pos = m_type.start() if m_type else len(src) + 1
        next_meth_pos = m_meth.start() if m_meth else len(src) + 1
        next_priv_pos = m_priv.start() if m_priv else len(src) + 1

        if m_type is None and m_meth is None and m_priv is None:
            break

        # A non-public nested type reached before the next public type/method:
        # skip its ENTIRE body so its members (which may be ``@Override public``,
        # e.g. a private HttpServer-decorator) don't leak up to this class.
        if m_priv is not None and next_priv_pos < next_type_pos and next_priv_pos < next_meth_pos:
            body_open = src.find("{", m_priv.end())
            if body_open < 0:
                i = m_priv.end()
                continue
            body_close = find_matching_brace(src, body_open)
            i = body_close + 1
            continue

        if next_type_pos <= next_meth_pos:
            # Nested public type. Recurse into its body.
            name = m_type.group("name")
            # Apply structural-rename table (Java's CallDialEvent → Python's
            # DialEvent, etc.).
            renamed = _CLASS_RENAMES.get(name, name)
            # Qualify port-only nested class names with their outer class
            # to avoid collisions across files (multiple ``Builder``s).
            effective_name = (
                renamed if renamed in known_python_classes
                else outer_name + renamed
            )
            # Skip compact record headers (record Foo(...) {}) — still treat body.
            body_open = src.find("{", m_type.end())
            if body_open < 0:
                i = m_type.end()
                continue
            body_close = find_matching_brace(src, body_open)
            inner_body = src[body_open + 1 : body_close]
            inner_is_interface = m_type.group("kind") == "interface"
            inner_classes = parse_type_body(
                inner_body, effective_name, known_python_classes,
                java_outer_name=name, native=native,
                is_interface=inner_is_interface,
            )
            for cls_name, cls_methods in inner_classes.items():
                if cls_name in classes:
                    classes[cls_name].extend(cls_methods)
                else:
                    classes[cls_name] = cls_methods
            i = body_close + 1
        else:
            name = m_meth.group("name")
            # For interface bodies, reject private/protected helpers (not public
            # API) and obvious non-declarations (control-flow keywords whose
            # ``rtype`` capture is a keyword like ``return``/``if``/``for``).
            if is_interface:
                rtype = (m_meth.groupdict().get("rtype") or "").strip()
                first = rtype.split()[0] if rtype.split() else ""
                if first in ("private", "protected", "return", "if", "for",
                             "while", "switch", "catch", "new", "throw", "else",
                             "do", "synchronized", "assert"):
                    i = m_meth.end()
                    continue
            # Skip if this identifier is actually a type header (we saw
            # ``public class Foo {`` — _METHOD_HEADER would not match because
            # there is no ``(``, but a ``public record Foo(int x)`` does have
            # parens; filter those out by checking the word before ``name``).
            # Also skip method headers inside the body of a nested type: the
            # walker above routes to ``parse_type_body`` for those.
            head_start = m_meth.start()
            # Ensure we don't treat "public SomeClass foo" inside a nested
            # body — but since we only reach here when we didn't see a nested
            # type header closer, this is safe.
            # Find the opening paren and then the next ``{`` or ``;``.
            paren_open = src.find("(", m_meth.end() - 1)
            if paren_open < 0:
                i = m_meth.end()
                continue
            # Balance parens to skip past a multi-line parameter list.
            depth = 0
            j = paren_open
            while j < len(src):
                if src[j] == "(":
                    depth += 1
                elif src[j] == ")":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            after_params = j + 1
            # Abstract methods / interface methods end with ``;`` (no body).
            # Concrete methods have ``{ ... }``. Either is acceptable.
            brace = src.find("{", after_params)
            semi = src.find(";", after_params)
            body_end: int
            if brace >= 0 and (semi < 0 or brace < semi):
                body_end = find_matching_brace(src, brace)
            elif semi >= 0:
                body_end = semi
            else:
                body_end = after_params

            translated = translate_method_name(name, java_outer_name, native=native)
            if translated is not None:
                if isinstance(translated, list):
                    methods.extend(translated)
                else:
                    methods.append(translated)
            i = body_end + 1

    return classes


# ---------------------------------------------------------------------------
# Module-path derivation.
# ---------------------------------------------------------------------------

def java_to_python_module(java_package: str, class_name: str,
                          class_to_module: dict[str, str]) -> str:
    """Pick the Python module path to emit a class under.

    Priority:
      0. If the Java FQN (``package.ClassName``) has an explicit override in
         ``_JAVA_SURFACE_MODULE_OVERRIDES``, use it. This WINS over the
         class-name map so a hand class whose name collides with a generated
         DTO (``pom.Section`` vs the generated ``Section``; ``swml.Document`` vs
         the datasphere generated ``Document``) routes by its Java package,
         exactly as enumerate_signatures.py does.
      1. If the class name exists in ``python_surface.json``, use the
         reference module.
      2. Otherwise translate the Java package naturally (drop
         ``com.signalwire.sdk``, snake_case each segment, prepend
         ``signalwire.``), then append the snake_cased class name.
         Port-only classes end up at ``signalwire.<pkg>.<snake_class>``.
    """
    fqn = f"{java_package}.{class_name}" if java_package else class_name
    if fqn in _JAVA_SURFACE_MODULE_OVERRIDES:
        return _JAVA_SURFACE_MODULE_OVERRIDES[fqn]
    if class_name in class_to_module:
        return class_to_module[class_name]

    # Strip com.signalwire.sdk. prefix.
    pkg = java_package
    if pkg.startswith("com.signalwire.sdk"):
        pkg = pkg[len("com.signalwire.sdk"):].lstrip(".")

    parts = [p for p in pkg.split(".") if p]
    # Each package segment is already lowercase by convention; pass through.
    segments = ["signalwire"] + parts
    # Append a snake-cased class-name segment so we get a unique module per
    # class — mirrors the Python reference style (one .py per class).
    segments.append(camel_to_snake(class_name))
    return ".".join(segments)


# ---------------------------------------------------------------------------
# Main enumerator.
# ---------------------------------------------------------------------------

_PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)


def enumerate_file(path: Path, class_to_module: dict[str, str],
                   native: bool = False) -> dict[str, dict]:
    """Return {module: {"classes": {Name: [methods]}, "functions": []}}."""
    raw = path.read_text(encoding="utf-8", errors="replace")
    stripped = strip_comments_and_strings(raw)

    pkg_match = _PACKAGE_RE.search(stripped)
    java_package = pkg_match.group(1) if pkg_match else ""

    # Find top-level public type.
    m_type = _TYPE_HEADER.search(stripped)
    if m_type is None:
        return {}

    outer_name_raw = m_type.group("name")
    # In native mode we skip the Python-reference class renames — Java docs
    # reference Java names like CallDialEvent, not DialEvent.
    outer_name = (outer_name_raw if native
                  else _CLASS_RENAMES.get(outer_name_raw, outer_name_raw))
    body_open = stripped.find("{", m_type.end())
    if body_open < 0:
        return {}
    body_close = find_matching_brace(stripped, body_open)
    body = stripped[body_open + 1 : body_close]

    known_python_classes = set(class_to_module.keys())
    classes = parse_type_body(
        body, outer_name, known_python_classes,
        java_outer_name=outer_name_raw, native=native,
        is_interface=(m_type.group("kind") == "interface"),
    )

    # Generated wire-type / read-side-payload files (item A/H + D): a method-less
    # DTO class (or a public enum) per components/schemas / $defs OBJECT. Route the
    # file's top-level type BY PACKAGE to its oracle <ns>_types_generated /
    # *_generated module (winning over class_to_module — a type name recurs across
    # namespaces / collides with an SDK class). Record it method-less: the reference
    # records these as method-less type definitions, so drop any nested types and
    # synthetic members (a generated enum's own values are constants, not methods).
    gen_type_mod = _gen_type_module(java_package)
    if gen_type_mod is not None:
        canonical = _gen_type_unrename(outer_name)
        return {gen_type_mod: {"classes": {canonical: []}, "functions": []}}

    # Generated REST layer projection (§8). The generated resource/container
    # classes live in ``...rest.namespaces.generated`` and carry two kinds of
    # PORT-ONLY surface the Python oracle does not record:
    #   * the typed-input BUILDER scaffolding — every write/command/set method
    #     emits a nested ``<Method>Request`` + its ``Builder`` (the Java NAMED
    #     idiom for keyword params, L13). These are implementation detail of the
    #     typed input, not a route/resource, so the oracle has no counterpart.
    #     Drop ALL nested classes in the generated package (the resource and
    #     container classes are each the file's single top-level type; the only
    #     nested types are this Request/Builder scaffolding).
    #   * the client-tree WIRING — the ``ResourceTree`` plumbing base and the
    #     namespace containers expose lazy accessor methods (``aiAgents()`` …).
    #     These are the Java statically-typed idiom for Python's ``client.fabric``
    #     / ``client.fabric.subscribers`` INSTANCE ATTRIBUTES (Python wires them
    #     as ``self.<name> = <Resource>(http)`` in ``__init__`` /
    #     ``_wire_resources``; Java, having no ``__getattr__``, exposes each as a
    #     public zero-arg accessor method). The Python surface ENUMERATOR records
    #     only ``ClassDef``/``FunctionDef`` — never instance attributes — so the
    #     accessor NAMES have NO method-surface counterpart in the reference
    #     (``FabricNamespace`` etc. carry only ``__init__``; ``RestClient`` too).
    #     They are therefore REAL public Java methods that must be VISIBLE to the
    #     surface (so DOC-AUDIT resolves ``client.phoneNumbers()`` and the
    #     enumeration is not blind to shipped API) and declared as PORT_ADDITIONS
    #     (the Java lazy-accessor idiom for a Python instance attribute — the
    #     reference expresses the same capability as an attribute the oracle does
    #     not enumerate, so relative to the compared METHOD surface they are
    #     port-side extras, not omissions). Emit them in BOTH modes:
    #       - ResourceTree's accessors are inherited by ``RestClient`` (which
    #         ``extends ResourceTree``); route them onto the reference's
    #         ``signalwire.rest.client.RestClient`` (the Python home of the
    #         client-tree attributes, wired by ``_GeneratedResourceTree``).
    #       - each namespace container's accessors stay on that container class
    #         (``FabricNamespace`` etc. in ``_client_tree_generated``).
    if java_package == "com.signalwire.sdk.rest.namespaces.generated":
        # 1. Drop every nested class (keep only the file's top-level type).
        classes = {outer_name: classes.get(outer_name, [])}
        # 2. ResourceTree plumbing base. RestClient ``extends ResourceTree`` and
        #    inherits its accessors; the TEXT enumerator sees them only in
        #    ResourceTree.java. Route the accessor methods onto the reference's
        #    RestClient (their Python home, wired as instance attributes by
        #    ``_GeneratedResourceTree``) so the port surface exposes them and
        #    DOC-AUDIT resolves ``client.fabric()`` etc. Strip constructor
        #    spellings and any ``__init__``/``generated_http_client`` plumbing
        #    (``generatedHttpClient`` is protected+abstract, not public API).
        if outer_name == "ResourceTree":
            ctor_names = {outer_name, camel_to_snake(outer_name), "__init__",
                          "generated_http_client"}
            accessors = sorted(
                {m for m in classes[outer_name] if m not in ctor_names}
            )
            return {
                "signalwire.rest.client": {
                    "classes": {"RestClient": accessors},
                    "functions": [],
                }
            }
        # 3. Namespace containers. Keep the container's lazy-accessor METHODS
        #    (``brands()``/``campaigns()``/``subscribers()``…) on the container
        #    class so the surface exposes the shipped accessors and DOC-AUDIT
        #    resolves ``client.registry().brands()``. Strip constructor spellings
        #    (native has no ``__init__``; the ctor translates to
        #    ``<ClassName>``/``<snake_class>``). These accessors are PORT_ADDITIONS
        #    (Java idiom for Python's ``self.<name>`` container attributes — the
        #    oracle records these containers with only ``__init__``).
        if outer_name in _GENERATED_CONTAINERS:
            ctor_names = {outer_name, camel_to_snake(outer_name)}
            classes[outer_name] = [m for m in classes[outer_name]
                                   if m not in ctor_names]
        else:
            # 4. Implicit-base projection. SignatureDump/the text parser only see
            #    DECLARED methods, so a generated resource that INHERITS
            #    create/update from a CRUD/Fabric base shows neither. The oracle
            #    records create + update on every such subclass (verified: the
            #    CrudResource/FabricResource/FabricResourcePUT bases contribute
            #    exactly {create, update} to their subclasses — get/list/delete
            #    live on the base module, not projected onto the resource).
            #    Inject the two method NAMES the class doesn't already declare.
            base = _generated_extends(raw)
            if base in _CRUD_LIKE_BASES:
                have = set(classes[outer_name])
                for m in ("create", "update"):
                    if m not in have:
                        classes[outer_name].append(m)

    # Free-function projection: a static method on certain Java classes
    # surfaces as a Python module-level free function. Without this, the
    # surface diff sees Java's ``Signalwire.registerSkill`` as a port-only
    # method and Python's ``signalwire.register_skill`` as missing. Mirrors
    # FREE_FUNCTION_PROJECTIONS in enumerate_signatures.py. We use the bare
    # Java class name (not full path) because the surface enumerator works
    # at the source-file level, one Java type per file.
    projected_free_fns: list[tuple[str, str]] = []  # (target_mod, target_fn)
    if not native:
        for (java_cls, java_method), (target_mod, target_fn) in (
            _FREE_FUNCTION_SURFACE_PROJECTIONS.items()
        ):
            if java_cls != outer_name_raw:
                continue
            cls_methods = classes.get(outer_name, [])
            # The surface parser stores method names already-translated;
            # check both Java-native and snake_case forms.
            snake_form = camel_to_snake(java_method)
            if snake_form in _PY_KEYWORDS:
                snake_form += "_"
            snake_form = _METHOD_RENAMES.get(snake_form, snake_form)
            if java_method in cls_methods or snake_form in cls_methods:
                projected_free_fns.append((target_mod, target_fn))

    # Assign each class to its Python-reference module.
    out: dict[str, dict] = {}
    for cls_name, methods in classes.items():
        if cls_name in _SURFACE_EXCLUDED_CLASSES:
            continue  # idiom-scaffolding (options-builder / result record / nested value type)
        mod = java_to_python_module(java_package, cls_name, class_to_module)
        # Per-(module, class) method-NAME aliases: a Java-idiom method whose
        # name differs from the reference's (e.g. SWMLBuilder's ``verb`` catch-
        # all is the Java analog of the reference's runtime ``__getattr__`` verb
        # dispatch). Reconcile via the enumerator so the two compare EQUAL
        # (Rule 2 — idiom via rename, not omission). Applied before dedupe so the
        # renamed name folds with any pre-existing copy.
        aliases = _SURFACE_METHOD_ALIASES.get((mod, cls_name))
        if aliases and not native:
            methods = [aliases.get(m, m) for m in methods]
        # Deduplicate overloaded methods; stable ordering.
        unique_sorted = sorted(set(methods))
        entry = out.setdefault(mod, {"classes": {}, "functions": []})
        if cls_name in entry["classes"]:
            entry["classes"][cls_name] = sorted(
                set(entry["classes"][cls_name]) | set(unique_sorted)
            )
        else:
            entry["classes"][cls_name] = unique_sorted

    # Add free-function projections after class assignment so they don't
    # collide with the class-method emission.
    for target_mod, target_fn in projected_free_fns:
        entry = out.setdefault(target_mod, {"classes": {}, "functions": []})
        if target_fn not in entry["functions"]:
            entry["functions"] = sorted(set(entry["functions"]) | {target_fn})

    return out


def enumerate_sdk(java_src_root: Path, class_to_module: dict[str, str],
                  native: bool = False) -> dict[str, dict]:
    """Walk ``java_src_root/com/signalwire/sdk`` and collect all classes."""
    merged: dict[str, dict] = {}
    for path in sorted(java_src_root.rglob("*.java")):
        per_file = enumerate_file(path, class_to_module, native=native)
        for mod, entry in per_file.items():
            dest = merged.setdefault(mod, {"classes": {}, "functions": []})
            for cls_name, methods in entry["classes"].items():
                if cls_name in dest["classes"]:
                    dest["classes"][cls_name] = sorted(
                        set(dest["classes"][cls_name]) | set(methods)
                    )
                else:
                    dest["classes"][cls_name] = methods
            dest["functions"] = sorted(
                set(dest["functions"]) | set(entry["functions"])
            )

    # Mixin projection (skipped in native mode — Java docs reference the
    # AgentBase home of these methods, not the Python mixin path).
    # Mirrors the post-collection projection in enumerate_signatures.py:
    # for each (target_mod, target_cls) → method list, move any matching
    # methods from AgentBase onto the mixin path. Class-level entries on
    # the mixin path are created on demand.
    if not native:
        ab_entry = merged.get("signalwire.core.agent_base", {})
        ab_methods = ab_entry.get("classes", {}).get("AgentBase", [])
        # AgentBase extends Service (→ SWMLService); the TEXT enumerator only
        # sees DECLARED methods, so AgentBase's INHERITED Service methods
        # (serve / on_request / on_swml_request / validate_basic_auth /
        # get_basic_auth_credentials …) are invisible on AgentBase even though
        # they are part of its surface. Include the SWMLService method set when
        # detecting which mixin methods are "present on AgentBase" so the
        # WebMixin/AuthMixin projections fire for inherited methods too. The
        # signature enumerator sees these via JAR reflection; this restores
        # parity for the source-based surface enumerator. Inherited methods are
        # ADDED to the mixin path but NOT removed from SWMLService (they
        # legitimately belong to both — the reference records them on both).
        svc_methods = (
            merged.get("signalwire.core.swml_service", {})
            .get("classes", {})
            .get("SWMLService", [])
        )
        ab_visible = set(ab_methods) | set(svc_methods)
        for (target_mod, target_cls), expected in _MIXIN_SURFACE_PROJECTIONS.items():
            present = [m for m in expected if m in ab_visible]
            if not present:
                continue
            target = merged.setdefault(target_mod, {"classes": {}, "functions": []})
            existing = target["classes"].get(target_cls, [])
            target["classes"][target_cls] = sorted(set(existing) | set(present))
            # Only strip the projected methods from AgentBase's OWN declared
            # list (never from the inherited SWMLService copy).
            ab_methods = [m for m in ab_methods if m not in present]
        if ab_entry:
            if ab_methods:
                ab_entry["classes"]["AgentBase"] = sorted(set(ab_methods))
            else:
                ab_entry.get("classes", {}).pop("AgentBase", None)
                if not ab_entry.get("classes") and not ab_entry.get("functions"):
                    merged.pop("signalwire.core.agent_base", None)

    return merged


# NOTE (pass-2 RELAY action-contract reconcile): the oracle no longer factors
# the call-action control verbs into shared ``StoppableAction`` /
# ``PausableAction`` / ``VolumeAction`` base classes. It PROJECTS stop / pause /
# resume / volume directly onto each concrete action (PlayAction, RecordAction,
# CollectAction, …). Java already declares the control methods directly on each
# concrete action, so it matches the reference as-is — no base-class hoisting is
# performed (the former ``_project_relay_action_mixins`` synthesized the three
# obsolete bases and is removed).


def git_sha(repo: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "-C", str(repo), "rev-parse", "HEAD"],
            stderr=subprocess.DEVNULL,
        ).decode().strip()
    except Exception:
        return "N/A"


def build_snapshot(repo_root: Path, reference_json: Path,
                   native: bool = False) -> dict:
    class_to_module = build_class_to_module_map(reference_json)
    java_src = repo_root / "src" / "main" / "java"
    if not java_src.is_dir():
        raise SystemExit(f"error: java source not found at {java_src}")
    modules = enumerate_sdk(java_src, class_to_module, native=native)
    return {
        "version": "1",
        "generated_from": f"signalwire-java @ {git_sha(repo_root)}",
        "language": "java",
        "names": "java-native" if native else "python-reference",
        "modules": modules,
    }


def _default_reference() -> Path:
    """Locate porting-sdk/python_surface.json without a ~/src hardcode.

    Order: $PORTING_SDK → adjacency (porting-sdk as a sibling of this repo, the
    CLAUDE.md §7 layout used in CI's workspace checkout) → legacy ~/src. The old
    hardcoded ~/src/porting-sdk default failed the SURFACE-FRESH gate in CI,
    where porting-sdk is checked out beside the port repo, not under $HOME/src.
    """
    repo_root = Path(__file__).resolve().parent.parent
    candidates = [
        Path(os.environ["PORTING_SDK"]) if os.environ.get("PORTING_SDK") else None,
        repo_root.parent / "porting-sdk",          # adjacency (CI + local)
        Path.home() / "src" / "porting-sdk",        # legacy local fallback
    ]
    for base in candidates:
        if base is not None and (base / "python_surface.json").is_file():
            return base / "python_surface.json"
    # Fall back to the adjacency path for the error message if none exist.
    return repo_root.parent / "porting-sdk" / "python_surface.json"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo", type=Path, default=Path(__file__).resolve().parent.parent,
        help="Path to the signalwire-java repo root (default: script's repo)",
    )
    parser.add_argument(
        "--reference", type=Path,
        default=_default_reference(),
        help="Path to porting-sdk/python_surface.json for class→module lookup "
             "(default: $PORTING_SDK or porting-sdk adjacent to this repo)",
    )
    parser.add_argument(
        "--output", type=Path, default=None,
        help="Write JSON to this path (default: stdout)",
    )
    parser.add_argument(
        "--check", action="store_true",
        help="Compare against file at --output; exit 1 on drift",
    )
    parser.add_argument(
        "--native", action="store_true",
        help="Emit Java-native method names (camelCase) instead of "
             "Python-reference snake_case. Used for Layer C doc↔code "
             "alignment auditing via audit_docs.py.",
    )
    args = parser.parse_args(argv)

    if args.check and not args.output:
        parser.error("--check requires --output")
    if not args.reference.is_file():
        print(f"error: reference {args.reference} not found", file=sys.stderr)
        return 1

    snapshot = build_snapshot(args.repo, args.reference, native=args.native)
    rendered = json.dumps(snapshot, indent=2, sort_keys=True) + "\n"

    if args.check:
        if not args.output.is_file():
            print(f"error: {args.output} does not exist", file=sys.stderr)
            return 1
        existing = args.output.read_text(encoding="utf-8")

        def strip_meta(s: str) -> str:
            obj = json.loads(s)
            obj.pop("generated_from", None)
            return json.dumps(obj, indent=2, sort_keys=True) + "\n"

        if strip_meta(rendered) != strip_meta(existing):
            print(
                "DRIFT: port_surface.json is stale relative to the Java SDK.\n"
                "  Regenerate:\n"
                "    python3 scripts/enumerate_surface.py "
                "--output port_surface.json",
                file=sys.stderr,
            )
            return 1
        return 0

    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        sys.stdout.write(rendered)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
