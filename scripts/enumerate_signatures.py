#!/usr/bin/env python3
"""enumerate_signatures.py — emit port_signatures.json for the Java SDK.

Phase 4-Java of the cross-language signature audit. The pipeline:

    1. Build the SDK JAR (./gradlew build) with -parameters compile flag
       so reflection preserves method parameter names.
    2. Run scripts/SignatureDump (a small Java program that uses
       java.lang.reflect to dump every public constructor + method
       signature as raw JSON).
    3. This wrapper reads that raw JSON, applies the existing
       enumerate_surface.py translation tables (_CLASS_RENAMES,
       _METHOD_RENAMES, build_class_to_module_map, camel_to_snake),
       translates Java types to canonical via porting-sdk/type_aliases.yaml
       (java section), and emits port_signatures.json conforming to
       porting-sdk/surface_schema_v2.json.

Usage:
    python3 scripts/enumerate_signatures.py
    python3 scripts/enumerate_signatures.py --strict
    python3 scripts/enumerate_signatures.py --raw raw_dump.json
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
PORT_ROOT = HERE.parent
# Locate porting-sdk without a machine-specific hardcode:
# $PORTING_SDK -> adjacency (sibling of this repo, the CI + local layout) ->
# legacy ~/src fallback.
_psdk_candidates = [
    Path(os.environ["PORTING_SDK"]) if os.environ.get("PORTING_SDK") else None,
    PORT_ROOT.parent / "porting-sdk",
    Path.home() / "src" / "porting-sdk",
]
PSDK = next((c.resolve() for c in _psdk_candidates if c and c.is_dir()),
            (PORT_ROOT.parent / "porting-sdk").resolve())

sys.path.insert(0, str(HERE))
from enumerate_surface import (  # type: ignore
    _CLASS_RENAMES, _METHOD_RENAMES, _PY_KEYWORDS, build_class_to_module_map,
    camel_to_snake, translate_method_name,
    _gen_type_module, _gen_type_unrename,
)


# Hard Java keywords generate_rest.type_field_name suffixes with `_` when a wire
# key collides (``default`` → ``default_``, ``enum`` → ``enum_``). The gen-type
# field-accessor recording strips that suffix back to the bare wire key the oracle
# records. Kept in sync with generate_rest.JAVA_KEYWORDS.
_JAVA_FIELD_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new",
    "package", "private", "protected", "public", "return", "short", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while", "true", "false", "null",
    "var", "record", "yield",
}


class TypeTranslationError(RuntimeError):
    pass


def load_aliases() -> dict[str, str]:
    data = yaml.safe_load((PSDK / "type_aliases.yaml").read_text(encoding="utf-8"))
    return {str(k): str(v) for k, v in data.get("aliases", {}).get("java", {}).items()}


# ---------------------------------------------------------------------------
# Generated-REST typed-input sidecar (rest_signatures.json)
# ---------------------------------------------------------------------------
#
# The generated REST resource methods take the Java NAMED idiom for keyword
# params — a builder ``<Method>Request`` object (``create(CreateRequest req)``)
# — which reflection sees as a SINGLE ``request`` param (L13: the builder is the
# user-facing idiom, never flattened to positionals). The oracle records the
# exploded flat keyword set (body fields → keyword, ``extras`` → keyword, a
# leading path-id → positional, a GET/list query bag → ``var_keyword``). Java
# reflection cannot express keyword-only params, so the generator emits a
# canonical unfold sidecar next to the generated resources; this enumerator
# REPLACES the reflected builder-``request`` param list with the sidecar's flat
# set so the drift gate compares count+kind against the oracle (L10 — the
# generator explodes, the enumerator reclassifies). Body-field types are the
# open value type (``optional<any>`` / ``string`` / …) — the gate compares
# param count+kind, not the static field type, so this is idiom-safe.
#
# Static-typed Java has no ``**kwargs`` mechanism, so (like go/dotnet) the
# unfold keeps ``extras`` only and does NOT synthesize the oracle's trailing
# ``**kwargs`` var_keyword. The 1-param residual that leaves (methods whose
# oracle signature carries a trailing ``kwargs``) is documented per-method in
# PORT_SIGNATURE_OMISSIONS.md with a ``java-no-kwargs`` honest reason — never a
# BACKLOG/loose-body tag.
_GENERATED_PKG = "com.signalwire.sdk.rest.namespaces.generated"


def load_rest_sidecar() -> dict[str, list[dict]]:
    """Load the generator's canonical typed-param records for generated REST
    resource methods. Keyed by ``<ResourceClass>::<javaMethod>`` → param list
    (each ``{name, kind, type, required[, default]}``)."""
    path = (
        PORT_ROOT / "src" / "main" / "java" / "com" / "signalwire" / "sdk"
        / "rest" / "namespaces" / "generated" / "rest_signatures.json"
    )
    if not path.is_file():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    return data.get("methods", {})


# ---------------------------------------------------------------------------
# Java type translation
# ---------------------------------------------------------------------------

GENERIC_RE = re.compile(r"^([A-Za-z_$][\w.$]*)<(.+)>$")


def split_generic_args(s: str) -> list[str]:
    """Split top-level generic args (depth-aware comma split)."""
    parts: list[str] = []
    buf: list[str] = []
    depth = 0
    for ch in s:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append("".join(buf).strip())
            buf.clear()
            continue
        buf.append(ch)
    if buf:
        parts.append("".join(buf).strip())
    return parts


def translate_java_type(t: str, aliases: dict[str, str], context: str) -> str:
    if t is None or t == "":
        return "any"
    t = t.strip()

    # Wildcard bounds — ``? extends Foo`` collapses to ``Foo``,
    # ``? super Foo`` collapses to ``any`` (covariant write-side has no
    # canonical analog), bare ``?`` collapses to ``any``.
    if t.startswith("? extends "):
        return translate_java_type(t[len("? extends "):], aliases, context)
    if t.startswith("? super "):
        return "any"
    if t == "?":
        return "any"

    # Array suffix
    while t.endswith("[]"):
        inner = translate_java_type(t[:-2], aliases, context)
        if inner == "int":
            # byte[] is special-cased via aliases; other primitive arrays
            # become list<int> etc.
            return "list<int>"
        return f"list<{inner}>"

    if t in aliases:
        return aliases[t]

    # Strip generic instantiation: java.util.List<T> → list<T>, etc.
    m = GENERIC_RE.match(t)
    if m:
        head, inner = m.group(1), m.group(2)
        if head in aliases:
            # If the alias defines a parameterized canonical, splat the args
            mapped = aliases[head]
            args = split_generic_args(inner)
            canon_args = [translate_java_type(a, aliases, context) for a in args]
            if mapped == "list" and len(canon_args) == 1:
                return f"list<{canon_args[0]}>"
            if mapped == "dict" and len(canon_args) == 2:
                return f"dict<{canon_args[0]},{canon_args[1]}>"
            return mapped
        # Built-in collection mappings
        args = split_generic_args(inner)
        canon_args = [translate_java_type(a, aliases, context) for a in args]
        if head in (
            "java.util.List", "java.util.ArrayList", "java.util.LinkedList",
            "java.util.Set", "java.util.HashSet", "java.util.LinkedHashSet",
            "java.util.Collection", "java.lang.Iterable",
        ):
            return f"list<{canon_args[0]}>" if canon_args else "list<any>"
        if head in (
            "java.util.Map", "java.util.HashMap", "java.util.LinkedHashMap",
            "java.util.TreeMap", "java.util.concurrent.ConcurrentHashMap",
        ):
            if len(canon_args) >= 2:
                return f"dict<{canon_args[0]},{canon_args[1]}>"
            return "dict<string,any>"
        if head == "java.util.Optional":
            return f"optional<{canon_args[0]}>" if canon_args else "optional<any>"
        if head in ("java.util.concurrent.CompletableFuture", "java.util.concurrent.Future"):
            return canon_args[0] if canon_args else "any"
        if head == "java.util.Map$Entry":
            # Java's Map.Entry<K, V> is the idiomatic two-arity tuple shape;
            # surfaces as Python's Tuple[K, V] in the canonical vocab.
            if len(canon_args) >= 2:
                return f"tuple<{canon_args[0]},{canon_args[1]}>"
            return "tuple<any>"
        if head in ("java.util.function.Function", "java.util.function.BiFunction"):
            ret = canon_args[-1] if canon_args else "any"
            args_canon = canon_args[:-1]
            return f"callable<list<{','.join(args_canon)}>,{ret}>"
        if head in ("java.util.function.Consumer", "java.util.function.BiConsumer"):
            return f"callable<list<{','.join(canon_args)}>,void>"
        if head in ("java.util.function.Supplier",):
            return f"callable<list<>,{canon_args[0] if canon_args else 'any'}>"
        if head in ("java.util.function.Predicate",):
            return f"callable<list<{canon_args[0] if canon_args else 'any'}>,bool>"
        # SDK class with generic args — drop the args (Python reference
        # doesn't carry generic instantiations)
        if head.startswith("com.signalwire."):
            return _translate_sdk_class_ref(head)
        # Unknown parameterized: fail
        raise TypeTranslationError(
            f"unknown generic Java type {head!r}<{inner}> at {context}"
        )

    # Bare class name
    if t.startswith("com.signalwire."):
        return _translate_sdk_class_ref(t)
    if t in ("byte", "short", "int", "long", "float", "double", "boolean", "char"):
        # Should have been hit in aliases
        raise TypeTranslationError(
            f"primitive {t!r} not in aliases at {context}"
        )

    # Fall back to last segment lookup
    last = t.rsplit(".", 1)[-1]
    if last in aliases:
        return aliases[last]

    raise TypeTranslationError(
        f"unknown Java type {t!r} at {context}; "
        f"add to porting-sdk/type_aliases.yaml under aliases.java"
    )


def _translate_sdk_class_ref(full: str) -> str:
    """Translate ``com.signalwire.sdk.<sub>.<Class>`` to ``class:<canonical>``."""
    # Strip trailing inner-class component like AgentBase$Builder
    name = full.rsplit(".", 1)[-1]
    if "$" in name:
        name = name.split("$", 1)[0]
    canonical = _CLASS_RENAMES.get(name, name)
    if canonical in CLASS_TO_MODULE:
        return f"class:{CLASS_TO_MODULE[canonical]}.{canonical}"
    # Fallback: derive a Python module from the Java package
    pkg = full.rsplit(".", 1)[0]
    return f"class:{_pkg_to_module(pkg)}.{canonical}"


def _pkg_to_module(pkg: str) -> str:
    """com.signalwire.sdk.agent → signalwire.agent_base / signalwire.<seg>"""
    if not pkg.startswith("com.signalwire.sdk"):
        return "signalwire." + pkg.lower()
    rest = pkg[len("com.signalwire.sdk"):].lstrip(".")
    if not rest:
        return "signalwire"
    parts = [camel_to_snake(p) for p in rest.split(".")]
    return "signalwire." + ".".join(parts)


def _pkg_to_module_with_class(pkg: str, class_name: str) -> str:
    """Mirror surface enumerator's java_to_python_module: append a snake_cased
    class-name segment so each Java class lands at its own module path —
    ``signalwire.<pkg>.<snake_class>``. Used for classes that are neither in
    CLASS_TO_MODULE nor in JAVA_MODULE_OVERRIDES; without the class-name
    segment, multiple Java classes in the same package collide at one path
    (port-only Builder + AgentBase both at ``signalwire.agent`` etc.).
    """
    base = _pkg_to_module(pkg)
    snake = camel_to_snake(class_name)
    if base == "signalwire":
        return f"signalwire.{snake}"
    return f"{base}.{snake}"


# ---------------------------------------------------------------------------
# Building canonical inventory
# ---------------------------------------------------------------------------

# Loaded lazily via main()
CLASS_TO_MODULE: dict[str, str] = {}


# Additional Java class renames not in enumerate_surface.py:
# These are core SDK classes that Java names differently from Python.
JAVA_EXTRA_RENAMES = {
    "Service": "SWMLService",       # SignalWire.SWML.Service → core.swml_service.SWMLService
    "AgentBase": "AgentBase",       # already matches
}

# Nested-class qualification: SignatureDump emits each nested class with its
# bare ``getSimpleName()`` ("Builder"), losing the parent-class context. The
# surface enumerator (text-based) qualifies those names with the outer class
# (``AgentBaseBuilder``); without the same projection here, the signature
# diff would see ``signalwire.agent.Builder.host`` while the surface diff
# sees ``signalwire.agent.agent_base_builder.AgentBaseBuilder.host`` — two
# different identities for the same Java member.
#
# Keys: ``(java_package, java_simple_name)`` of the nested class as emitted
# by SignatureDump. Values: ``(canonical_class_name, canonical_module)``.
# Mirrors the surface enumerator's ``effective_name = outer_name + renamed``
# convention plus its ``java_to_python_module`` placement, so the two layers
# end up at identical fully-qualified symbol paths.
JAVA_NESTED_CLASS_RENAMES: dict[tuple[str, str], tuple[str, str]] = {
    # AgentBase.Builder, AgentBase.DynamicConfigCallback (nested in AgentBase.java)
    ("com.signalwire.sdk.agent", "Builder"):
        ("AgentBaseBuilder", "signalwire.agent.agent_base_builder"),
    ("com.signalwire.sdk.agent", "DynamicConfigCallback"):
        ("AgentBaseDynamicConfigCallback", "signalwire.agent.agent_base_dynamic_config_callback"),
    # RelayClient.Builder (nested in RelayClient.java)
    ("com.signalwire.sdk.relay", "Builder"):
        ("RelayClientBuilder", "signalwire.relay.relay_client_builder"),
    # RestClient.Builder (nested in RestClient.java)
    ("com.signalwire.sdk.rest", "Builder"):
        ("RestClientBuilder", "signalwire.rest.rest_client_builder"),
    # Action subtypes nested under Call.java's relay package. The reference
    # projects each concrete action's control methods (stop/pause/resume/volume)
    # directly onto the action class in ``signalwire.relay.call``, so these must
    # land there (matching the SURFACE enumerator's folds) rather than in their
    # own per-class modules. Semantics fix (pass-2 reconcile): Java's
    # ``PlayAndCollectAction`` (prefix ``play_and_collect``, carries
    # stop/pause/resume/volume + start_input_timers) IS the reference's
    # ``CollectAction``; Java's ``CollectAction`` (prefix ``collect``,
    # stop + start_input_timers) IS the reference's ``StandaloneCollectAction``.
    # Java's inbound ``ReceiveFaxAction`` is the reference's ``FaxAction``.
    ("com.signalwire.sdk.relay", "PlayAndCollectAction"):
        ("CollectAction", "signalwire.relay.call"),
    ("com.signalwire.sdk.relay", "CollectAction"):
        ("StandaloneCollectAction", "signalwire.relay.call"),
    ("com.signalwire.sdk.relay", "ReceiveFaxAction"):
        ("FaxAction", "signalwire.relay.call"),
    ("com.signalwire.sdk.relay", "SendFaxAction"):
        ("CallSendFaxAction", "signalwire.relay.call_send_fax_action"),
    # AuthorizationStateEvent — nested under RelayEvent in Java
    ("com.signalwire.sdk.relay", "AuthorizationStateEvent"):
        ("RelayEventAuthorizationStateEvent", "signalwire.relay.relay_event_authorization_state_event"),
    # Constants — top-level in Java but with no Python counterpart
    ("com.signalwire.sdk.relay", "Constants"):
        ("RelayConstants", "signalwire.relay.relay_constants"),
    # SwaigTest.Platform (the inner enum in SwaigTest.java)
    ("com.signalwire.sdk.cli", "SwaigTest"):
        ("SwaigTest", "signalwire.cli.swaig_test"),
    # ServerlessSimulator.Platform — Java nests Platform inside ServerlessSimulator
    ("com.signalwire.sdk.cli.simulation", "Platform"):
        ("ServerlessSimulatorPlatform", "signalwire.cli.simulation.serverless_simulator_platform"),
    # Logger — top-level in com.signalwire.sdk.logging
    ("com.signalwire.sdk.logging", "Logger"):
        ("Logger", "signalwire.core.logging_config"),
    ("com.signalwire.sdk.logging", "Level"):
        ("LoggingLevel", "signalwire.logging.logging_level"),
    # ToolDefinition / ToolHandler — top-level swaig classes
    ("com.signalwire.sdk.swaig", "ToolDefinition"):
        ("ToolDefinition", "signalwire.swaig.tool_definition"),
    ("com.signalwire.sdk.swaig", "ToolHandler"):
        ("ToolHandler", "signalwire.swaig.tool_handler"),
    # ParameterSchema.Builder (nested in ParameterSchema.java) — like
    # AgentBase.Builder, the bare ``Builder`` is qualified with its outer
    # class to avoid collision, mirroring the surface enumerator's
    # ``effective_name = outer_name + renamed`` → ``ParameterSchemaBuilder``.
    ("com.signalwire.sdk.swaig", "Builder"):
        ("ParameterSchemaBuilder", "signalwire.swaig.parameter_schema_builder"),
    # Document — Java's swml.Document (note: there's also a JAVA_MODULE_OVERRIDES entry
    # routing it to signalwire.core.swml_builder; the projection here keeps the class
    # name aligned with the surface emission for the audit walker).
    # Document is already correctly named; routing handled elsewhere.
    # Skill builtin classes — Java exposes builtin skills under
    # signalwire.skills.builtin.* with their own naming, but Python uses
    # signalwire.skills.<name>.skill.<NameSkill>. Map per-class.
    ("com.signalwire.sdk.skills.builtin", "ApiNinjasTriviaSkill"):
        ("ApiNinjasTriviaSkill", "signalwire.skills.builtin"),
    ("com.signalwire.sdk.skills.builtin", "DateTimeSkill"):
        ("DateTimeSkill", "signalwire.skills.builtin"),
    # PhoneCallHandler — top-level enum in rest.call_handler
    ("com.signalwire.sdk.rest", "PhoneCallHandler"):
        ("PhoneCallHandler", "signalwire.rest.call_handler"),
    # HttpClient (rest._base) — top-level
    ("com.signalwire.sdk.rest", "HttpClient"):
        ("HttpClient", "signalwire.rest._base"),
    # Lambda runtime — Java nests handlers under runtime.lambda
    ("com.signalwire.sdk.runtime.lambda", "LambdaAgentHandler"):
        ("LambdaAgentHandler", "signalwire.runtime.lambda.lambda_agent_handler"),
    ("com.signalwire.sdk.runtime.lambda", "LambdaResponse"):
        ("LambdaResponse", "signalwire.runtime.lambda.lambda_response"),
    # EnvProvider / ExecutionMode — runtime helpers (top-level interfaces/enums)
    ("com.signalwire.sdk.runtime", "EnvProvider"):
        ("EnvProvider", "signalwire.runtime.env_provider"),
    ("com.signalwire.sdk.runtime", "ExecutionMode"):
        ("ExecutionMode", "signalwire.runtime.execution_mode"),
    # REST namespace classes — Java exposes <Name>Namespace at top-level rest.namespaces;
    # Python doesn't have these classes (they're erased through indexed CrudResource).
    ("com.signalwire.sdk.rest.namespaces", "BillingNamespace"):
        ("BillingNamespace", "signalwire.rest.namespaces.billing"),
    ("com.signalwire.sdk.rest.namespaces", "CampaignNamespace"):
        ("CampaignNamespace", "signalwire.rest.namespaces.campaign"),
    ("com.signalwire.sdk.rest.namespaces", "ChatNamespace"):
        ("ChatNamespace", "signalwire.rest.namespaces.chat"),
    ("com.signalwire.sdk.rest.namespaces", "ComplianceNamespace"):
        ("ComplianceNamespace", "signalwire.rest.namespaces.compliance"),
    ("com.signalwire.sdk.rest.namespaces", "ConferenceNamespace"):
        ("ConferenceNamespace", "signalwire.rest.namespaces.conference"),
    ("com.signalwire.sdk.rest.namespaces", "FaxNamespace"):
        ("FaxNamespace", "signalwire.rest.namespaces.fax"),
    ("com.signalwire.sdk.rest.namespaces", "MessagingNamespace"):
        ("MessagingNamespace", "signalwire.rest.namespaces.messaging"),
    ("com.signalwire.sdk.rest.namespaces", "NumberLookupNamespace"):
        ("NumberLookupNamespace", "signalwire.rest.namespaces.number_lookup"),
    ("com.signalwire.sdk.rest.namespaces", "PubSubNamespace"):
        ("PubSubNamespace", "signalwire.rest.namespaces.pub_sub"),
    ("com.signalwire.sdk.rest.namespaces", "SipNamespace"):
        ("SipNamespace", "signalwire.rest.namespaces.sip"),
    ("com.signalwire.sdk.rest.namespaces", "StreamNamespace"):
        ("StreamNamespace", "signalwire.rest.namespaces.stream"),
    ("com.signalwire.sdk.rest.namespaces", "SwmlNamespace"):
        ("SwmlNamespace", "signalwire.rest.namespaces.swml"),
    ("com.signalwire.sdk.rest.namespaces", "TranscriptionNamespace"):
        ("TranscriptionNamespace", "signalwire.rest.namespaces.transcription"),
}

# Overloaded methods where the canonical projection must surface the
# FULL-arity overload, not the fewest-param one. The default overload-collapse
# rule (prefer fewest params) is right for action methods whose optional
# kwargs are intentionally moved onto a typed Builder/Config (record_call,
# tap, ...; those carry a PORT_SIGNATURE_OMISSIONS rationale). It is WRONG for
# methods that deliberately expose every Python optional positionally to reach
# full reference parity — there the convenience overload would hide the real
# capability from the diff. Keyed by (canonical_class_name, method_canonical).
PREFER_FULL_OVERLOAD: set[tuple[str, str]] = {
    # FunctionResult.joinConference exposes all 18 of Python join_conference's
    # optional params (muted/beep/record/trim/wait_url/callback-method/...) plus
    # the 7 validations. The 1-arg joinConference(name) is only an ergonomic
    # shortcut; the full overload is the parity surface, so emit it.
    ("FunctionResult", "join_conference"),
    # The remaining FunctionResult SWAIG helpers below each now expose EVERY
    # Python optional positionally (the behavioral-parity sweep restored the
    # dropped params + the skipped validations so each emitted action is
    # byte-identical to Python). The fewer-arg form is just an ergonomic
    # convenience delegating to the full one; the full overload is the parity
    # surface, so emit it (and the PORT_SIGNATURE_OMISSIONS entries that
    # excused the old collapsed shape are removed — these are now drift-0
    # parity, like join_conference).
    ("FunctionResult", "pay"),            # +16 params, caller-overridable ai_response
    ("FunctionResult", "tap"),            # +rtp_ptime/status_url, +direction/codec/rtp_ptime validation
    ("FunctionResult", "record_call"),    # +5 params, always-emit beep/input_sensitivity, +format/direction validation
    ("FunctionResult", "send_sms"),       # +region
    ("FunctionResult", "rpc_dial"),       # +device_type (was hard-coded "phone")
    ("FunctionResult", "rpc_ai_message"), # +role (was hard-coded "system")
    # RELAY action pause() exposes the reference's optional ``behavior`` kwarg via
    # a ``pause(String behavior)`` overload alongside the no-arg convenience
    # ``pause()``. The full overload is the parity surface (matches the oracle's
    # ``pause(behavior: str | None)``); the 0-arg form would hide the param.
    ("PlayAction", "pause"),
    ("RecordAction", "pause"),
    ("CollectAction", "pause"),
    # AgentBase.addPatternHint now exposes the full Python
    # add_pattern_hint(hint, pattern, replace, ignore_case) — a STRUCTURED hint,
    # not a bare string (contract #74). AgentBase.addLanguage now exposes the
    # full add_language(name, code, voice, speech_fillers, function_fillers,
    # engine, model, params) carrying engine/model/fillers. The convenience
    # overloads (bare string / (name,code,voice)) delegate; the full overload is
    # the parity surface. (The PORT_SIGNATURE_OMISSIONS entries that excused the
    # old collapsed shapes are removed — these are now drift-0 parity.)
    ("AgentBase", "add_pattern_hint"),
    ("AgentBase", "add_language"),
    # Step.setGatherInfo exposes Python set_gather_info's optional ``isolated``
    # positionally via a (outputKey, completionAction, prompt, isolated)
    # overload alongside the 3-arg convenience. The full overload is the parity
    # surface (matches the oracle's 4-param set_gather_info); the 3-arg form
    # would hide ``isolated``.
    ("Step", "set_gather_info"),
}

# Java skill class renames to match Python casing
JAVA_SKILL_RENAMES = {
    "DatasphereSkill": "DataSphereSkill",
    "DatasphereServerlessSkill": "DataSphereServerlessSkill",
    "ApiNinjaTriviaSkill": "ApiNinjasTriviaSkill",
    "DatetimeSkill": "DateTimeSkill",
    "SwmlTransferSkill": "SWMLTransferSkill",
}

# Java module path overrides for canonical Python paths
# Key: Java fully-qualified package.Class
JAVA_MODULE_OVERRIDES = {
    "com.signalwire.sdk.swml.Service": "signalwire.core.swml_service",
    # Java's SWML ``Document`` (the doc model) is port-only — the reference
    # ``swml_builder`` module records ``SWMLBuilder``, not ``Document``. Pin it
    # to the same port-only home the SURFACE enumerator uses
    # (signalwire.swml.document) so both gates agree and its methods are
    # PORT_ADDITIONS, not a spurious swml_builder.Document overlay.
    "com.signalwire.sdk.swml.Document": "signalwire.swml.document",
    # SchemaUtils.java is the canonical SchemaUtils port (Python parity
    # at signalwire.utils.schema_utils.SchemaUtils); class-name lookup
    # routes it automatically.  The pre-existing Schema.java is a
    # singleton sidecar — leave it under signalwire.swml.schema (a
    # port-only home) instead of layering its 3 methods onto the
    # SchemaUtils canonical surface.
    "com.signalwire.sdk.logging.Logger": "signalwire.core.logging_config",
    "com.signalwire.sdk.swaig.ToolDefinition": "signalwire.core.swaig_function",
    "com.signalwire.sdk.swaig.ToolHandler": "signalwire.core.swaig_function",
    "com.signalwire.sdk.swaig.FunctionResult": "signalwire.core.function_result",
    "com.signalwire.sdk.rest.RestError": "signalwire.rest._base",
    "com.signalwire.sdk.skills.SkillBase": "signalwire.core.skill_base",
    "com.signalwire.sdk.skills.SkillManager": "signalwire.core.skill_manager",
    "com.signalwire.sdk.contexts.Context": "signalwire.core.contexts",
    "com.signalwire.sdk.contexts.ContextBuilder": "signalwire.core.contexts",
    "com.signalwire.sdk.contexts.Step": "signalwire.core.contexts",
    "com.signalwire.sdk.contexts.GatherInfo": "signalwire.core.contexts",
    "com.signalwire.sdk.contexts.GatherQuestion": "signalwire.core.contexts",
    "com.signalwire.sdk.datamap.DataMap": "signalwire.core.data_map",
    # Skills go under signalwire.skills.<name>.skill
    "com.signalwire.sdk.skills.builtin.WebSearchSkill": "signalwire.skills.web_search.skill",
    "com.signalwire.sdk.skills.builtin.WikipediaSearchSkill": "signalwire.skills.wikipedia_search.skill",
    "com.signalwire.sdk.skills.builtin.DatasphereSkill": "signalwire.skills.datasphere.skill",
    "com.signalwire.sdk.skills.builtin.DatasphereServerlessSkill": "signalwire.skills.datasphere_serverless.skill",
    "com.signalwire.sdk.skills.builtin.SpiderSkill": "signalwire.skills.spider.skill",
    "com.signalwire.sdk.skills.builtin.JokeSkill": "signalwire.skills.joke.skill",
    "com.signalwire.sdk.skills.builtin.MathSkill": "signalwire.skills.math.skill",
    "com.signalwire.sdk.skills.builtin.WeatherApiSkill": "signalwire.skills.weather_api.skill",
    "com.signalwire.sdk.skills.builtin.ApiNinjasTriviaSkill": "signalwire.skills.api_ninjas_trivia.skill",
    "com.signalwire.sdk.skills.builtin.NativeVectorSearchSkill": "signalwire.skills.native_vector_search.skill",
    "com.signalwire.sdk.skills.builtin.DateTimeSkill": "signalwire.skills.datetime.skill",
    "com.signalwire.sdk.skills.builtin.GoogleMapsSkill": "signalwire.skills.google_maps.skill",
    "com.signalwire.sdk.skills.builtin.PlayBackgroundFileSkill": "signalwire.skills.play_background_file.skill",
    "com.signalwire.sdk.skills.builtin.InfoGathererSkill": "signalwire.skills.info_gatherer.skill",
    "com.signalwire.sdk.skills.builtin.McpGatewaySkill": "signalwire.skills.mcp_gateway.skill",
    "com.signalwire.sdk.skills.builtin.ClaudeSkillsSkill": "signalwire.skills.claude_skills.skill",
    "com.signalwire.sdk.skills.builtin.SwmlTransferSkill": "signalwire.skills.swml_transfer.skill",
    "com.signalwire.sdk.skills.builtin.CustomSkillsSkill": "signalwire.skills.registry",
}

# Free-function projections: lift a static method on an SDK class up to
# a Python module-level free function. Keyed by the Java fully-qualified
# class plus method name; value is the (py_module, py_function) target.
# Example: Python's signalwire.utils.url_validator.validate_url is a free
# function; Java exposes it as UrlValidator.validateUrl. Without this,
# the audit would be looking for "signalwire.utils.url_validator.validate_url"
# but the port emits "signalwire.utils.url_validator.UrlValidator.validate_url".
FREE_FUNCTION_PROJECTIONS = {
    ("com.signalwire.sdk.utils.UrlValidator", "validateUrl"):
        ("signalwire.utils.url_validator", "validate_url"),
    # RelayEvent.parseEvent → module-level signalwire.relay.event.parse_event
    # (Python free function; Java groups it as a static factory on RelayEvent).
    ("com.signalwire.sdk.relay.RelayEvent", "parseEvent"):
        ("signalwire.relay.event", "parse_event"),
    # ExecutionMode helpers — Python ships them as free functions in
    # two distinct modules; Java groups both static methods on the
    # ExecutionMode enum for cohesion.
    ("com.signalwire.sdk.runtime.ExecutionMode", "getExecutionMode"):
        ("signalwire.core.logging_config", "get_execution_mode"),
    # logging_config module-level free functions grouped on Logger's static
    # helpers (mirrors _FREE_FUNCTION_SURFACE_PROJECTIONS in enumerate_surface.py).
    ("com.signalwire.sdk.logging.Logger", "configureLogging"):
        ("signalwire.core.logging_config", "configure_logging"),
    ("com.signalwire.sdk.logging.Logger", "resetLoggingConfiguration"):
        ("signalwire.core.logging_config", "reset_logging_configuration"),
    ("com.signalwire.sdk.logging.Logger", "stripControlChars"):
        ("signalwire.core.logging_config", "strip_control_chars"),
    ("com.signalwire.sdk.runtime.ExecutionMode", "isServerlessMode"):
        ("signalwire.utils", "is_serverless_mode"),
    # Top-level Signalwire class projects each static helper onto the
    # canonical signalwire.<name> free function. The Java method is
    # PascalCase ``RestClient`` to mirror Python's same-cased function;
    # other helpers use camelCase that converts to snake_case via
    # camel_to_snake (registerSkill -> register_skill, etc.).
    ("com.signalwire.sdk.Signalwire", "RestClient"):
        ("signalwire", "RestClient"),
    ("com.signalwire.sdk.Signalwire", "registerSkill"):
        ("signalwire", "register_skill"),
    ("com.signalwire.sdk.Signalwire", "addSkillDirectory"):
        ("signalwire", "add_skill_directory"),
    ("com.signalwire.sdk.Signalwire", "listSkillsWithParams"):
        ("signalwire", "list_skills_with_params"),
    ("com.signalwire.sdk.Signalwire", "listSkills"):
        ("signalwire", "list_skills"),
    # WebhookValidator static methods → Python module-level free functions
    # in signalwire.core.security.webhook_validator. Java collapses both
    # entry points onto a static-only utility class for namespacing; the
    # projection lifts them back to the canonical Python locations so the
    # cross-port audit sees the same symbols.
    ("com.signalwire.sdk.security.WebhookValidator", "validateWebhookSignature"):
        ("signalwire.core.security.webhook_validator", "validate_webhook_signature"),
    ("com.signalwire.sdk.security.WebhookValidator", "validateRequest"):
        ("signalwire.core.security.webhook_validator", "validate_request"),
    # WebhookValidator.validate → the framework-free decomposed webhook-validation
    # core (signalwire.core.security.webhook_middleware.validate). Java's method
    # returns a WebhookRejection record (status, headers, body) or null; the oracle
    # records the language-neutral tuple<int,dict<string,string>,string>? shape, so
    # this projection's reflected return type is overridden via
    # FREE_FUNCTION_SIGNATURE_OVERRIDES below to the canonical tuple (same as .NET's
    # ValueTuple-based WebhookValidationMiddleware.Validate). The WebhookFilter
    # servlet wrapper on top of it stays a PORT_ADDITION idiom.
    ("com.signalwire.sdk.security.WebhookValidator", "validate"):
        ("signalwire.core.security.webhook_middleware", "validate"),
    # SecurityUtils static methods → Python module-level free functions in
    # signalwire.core.security.security_utils. The Python reference exports
    # these as bare module functions (filter_sensitive_headers, redact_url,
    # is_valid_hostname); Java groups them on a static-only utility class for
    # namespacing, so lift them back to the canonical free-function home.
    ("com.signalwire.sdk.security.SecurityUtils", "filterSensitiveHeaders"):
        ("signalwire.core.security.security_utils", "filter_sensitive_headers"),
    ("com.signalwire.sdk.security.SecurityUtils", "redactUrl"):
        ("signalwire.core.security.security_utils", "redact_url"),
    ("com.signalwire.sdk.security.SecurityUtils", "isValidHostname"):
        ("signalwire.core.security.security_utils", "is_valid_hostname"),
    # TypeInference static methods → Python module-level free functions in
    # signalwire.core.agent.tools.type_inference. Python reflects a handler's
    # signature; Java has no runtime lambda-parameter reflection, so the typed
    # params are supplied via the ParameterSchema typed-params builder and
    # inferSchema decomposes that built schema into the canonical tuple (the
    # static-port idiom — mirrors .NET/Ruby building from a types override).
    # Java groups both on a static-only utility class; lift them back to the
    # canonical free-function home. Native shapes are recorded canonically via
    # FREE_FUNCTION_SIGNATURE_OVERRIDES below.
    ("com.signalwire.sdk.core.agent.tools.TypeInference", "inferSchema"):
        ("signalwire.core.agent.tools.type_inference", "infer_schema"),
    ("com.signalwire.sdk.core.agent.tools.TypeInference", "createTypedHandlerWrapper"):
        ("signalwire.core.agent.tools.type_inference", "create_typed_handler_wrapper"),
}


# Signature overrides for free-function projections whose native Java shape does
# not translate to the canonical oracle shape via reflection alone. Keyed by the
# same ``(java_fqcn, java_method)`` as FREE_FUNCTION_PROJECTIONS; the value is the
# exact canonical signature to emit (params + returns), mirroring the Python
# reference. Java has no value-tuple type, so the decomposed webhook ``validate``
# core (which returns a ``WebhookRejection`` record standing in for the
# language-neutral ``(status, headers, body)`` triple) needs its return type
# recorded as the oracle's ``tuple<int,dict<string,string>,string>?`` directly —
# the same shape .NET emits from a ``System.ValueTuple``. The ``signing_key``
# param is keyword-only in the Python reference (``*, signing_key``); recording
# it as ``kind: keyword`` keeps the drift compare exact.
FREE_FUNCTION_SIGNATURE_OVERRIDES: dict[tuple[str, str], dict] = {
    ("com.signalwire.sdk.security.WebhookValidator", "validate"): {
        "params": [
            {"name": "method", "type": "string", "required": True},
            {"name": "url", "type": "string", "required": True},
            {"name": "headers", "type": "dict<string,string>", "required": True},
            {"name": "body", "type": "string", "required": True},
            {"name": "signing_key", "kind": "keyword", "type": "string", "required": True},
        ],
        "returns": "optional<tuple<int,dict<string,string>,string>>",
    },
    # infer_schema(func) -> (parameters, required, description, is_typed, has_raw_data).
    # Java's inferSchema takes the built ParameterSchema (a typed-params-builder
    # Map) + a description and returns an InferredSchema record; the oracle records
    # the language-neutral (func) -> 5-tuple shape (the callable param is the typed
    # handler the schema describes — mirrors .NET/Ruby recording `func`).
    ("com.signalwire.sdk.core.agent.tools.TypeInference", "inferSchema"): {
        "params": [
            {"name": "func", "type": "callable<list<any>,any>", "required": True},
        ],
        "returns":
            "tuple<dict<string,dict<string,any>>,list<string>,optional<string>,bool,bool>",
    },
    # create_typed_handler_wrapper(func, has_raw_data) -> callable. Java's method
    # takes a ToolHandler + boolean and returns a ToolHandler; recorded as the
    # canonical callable shape.
    ("com.signalwire.sdk.core.agent.tools.TypeInference", "createTypedHandlerWrapper"): {
        "params": [
            {"name": "func", "type": "callable<list<any>,any>", "required": True},
            {"name": "has_raw_data", "type": "bool", "required": True},
        ],
        "returns": "callable<list<any>,any>",
    },
}


# Method-level signature overrides for regular (non-free-function) methods whose
# native Java shape does not translate to the canonical oracle shape via
# reflection alone. Keyed by ``(canonical_class, method_canonical)`` — the
# canonical class name (post-rename, e.g. ``SWMLService``) and the canonical
# (snake_case) method name. The value is the exact canonical signature to emit
# (params + returns), mirroring the Python reference.
#
# ``handle_request`` is the framework-free dispatch core: Java has no value-tuple
# type, so it returns an ``HttpResult`` record standing in for the language-neutral
# ``(status, headers, body)`` triple. Reflection resolves that record to a bare
# ``class:`` ref; record the oracle's ``tuple<int,dict<string,string>,string>``
# return directly (the same shape .NET emits from its ``(int, Dictionary, string)``
# ValueTuple). The trailing ``body`` param is optional in the reference
# (``body=None``); mark it non-required so the drift compare is exact. It lives on
# both SWMLService (the base core) and AgentBase (the render-override), matching the
# reference which declares it on both.
METHOD_SIGNATURE_OVERRIDES: dict[tuple[str, str], dict] = {
    ("SWMLService", "handle_request"): {
        "params": [
            {"name": "self", "kind": "self"},
            {"name": "method", "type": "string", "required": True},
            {"name": "url", "type": "string", "required": True},
            {"name": "headers", "type": "dict<string,string>", "required": True},
            {"name": "body", "type": "optional<dict<string,any>>", "required": False,
             "default": None},
        ],
        "returns": "tuple<int,dict<string,string>,string>",
    },
    ("AgentBase", "handle_request"): {
        "params": [
            {"name": "self", "kind": "self"},
            {"name": "method", "type": "string", "required": True},
            {"name": "url", "type": "string", "required": True},
            {"name": "headers", "type": "dict<string,string>", "required": True},
            {"name": "body", "type": "optional<dict<string,any>>", "required": False,
             "default": None},
        ],
        "returns": "tuple<int,dict<string,string>,string>",
    },
}


MIXIN_PROJECTIONS = {
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
    # Python additionally extracted a ``PromptManager`` class that
    # PromptMixin delegates to. The user-facing surface is identical
    # (``agent.prompt_manager.X`` ≡ ``agent.X``). Project the same set of
    # AgentBase methods to PromptManager so the cross-language audit
    # treats both paths as covered.
    ("signalwire.core.agent.prompt.manager", "PromptManager"): [
        "define_contexts", "get_contexts", "get_post_prompt", "get_prompt",
        "get_raw_prompt",
        "prompt_add_section", "prompt_add_subsection", "prompt_add_to_section",
        "prompt_has_section", "set_post_prompt", "set_prompt_pom",
        "set_prompt_text",
    ],
    ("signalwire.core.mixins.skill_mixin", "SkillMixin"): [
        "add_skill", "has_skill", "list_skills", "remove_skill",
    ],
    ("signalwire.core.mixins.tool_mixin", "ToolMixin"): [
        "define_tool", "define_tools", "on_function_call",
        "register_swaig_function",
    ],
    ("signalwire.core.agent.tools.registry", "ToolRegistry"): [
        "define_tool", "register_swaig_function",
        "has_function", "get_function", "get_all_functions",
        "remove_function",
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


# Methods whose Python reference carries a trailing ``**kwargs`` / ``**llm_params``
# var-keyword tail that the port mirrors as a single trailing open ``Map`` param.
# Since porting-sdk #58 the oracle STRIPS every var-keyword tail from the extracted
# signature (the cross-port floor is the ``extras`` door, not a kwargs param — see
# enumerate_python_signatures.py). Java (static, no ``**kwargs``) still exposes the
# door as one trailing ``Map<String,Object>`` param; reflection defaults it to
# ``required: True`` (Java has no defaults), which now reads as a port-side EXTRA
# required param vs the stripped oracle → param-count-mismatch. The var-keyword door
# is optional by construction (a caller may pass zero extra keys), so mark that
# trailing param ``required: false``: the diff's ``extras_all_optional`` rule then
# excuses it as an optional port extra — the honest shape (an optional kwargs door),
# not an omission. Keyed by fully-qualified ``(module, class_or_None, method)``; a
# ``None`` class targets a module-level free function. The tail param is matched by
# position (last param) so the specific open-map value type doesn't matter.
KWARGS_TAIL_OPTIONAL: set[tuple[str, str | None, str]] = {
    ("signalwire", None, "RestClient"),
    ("signalwire.core.mixins.ai_config_mixin", "AIConfigMixin", "set_prompt_llm_params"),
    ("signalwire.core.mixins.ai_config_mixin", "AIConfigMixin", "set_post_prompt_llm_params"),
    ("signalwire.core.swml_handler", "SWMLVerbHandler", "build_config"),
}


def _mark_kwargs_tails_optional(out_modules: dict) -> None:
    """Flip the trailing var-keyword-door param of each KWARGS_TAIL_OPTIONAL
    method to ``required: false`` (see the set's docstring). Fail loud if a
    keyed symbol/param can't be found — a rename would otherwise silently stop
    excusing it and re-red the drift gate."""
    for module, cls, method in KWARGS_TAIL_OPTIONAL:
        mod_entry = out_modules.get(module)
        if not mod_entry:
            raise RuntimeError(f"KWARGS_TAIL_OPTIONAL: module {module!r} not found")
        if cls is None:
            sig = mod_entry.get("functions", {}).get(method)
        else:
            sig = mod_entry.get("classes", {}).get(cls, {}).get("methods", {}).get(method)
        if not sig:
            raise RuntimeError(
                f"KWARGS_TAIL_OPTIONAL: symbol {module}.{cls or ''}.{method} not found"
            )
        params = sig.get("params", [])
        # The tail is the last non-receiver param.
        tail = [p for p in params if p.get("kind") not in ("self", "cls")]
        if not tail:
            raise RuntimeError(
                f"KWARGS_TAIL_OPTIONAL: {module}.{cls or ''}.{method} has no value param to mark"
            )
        tail[-1]["required"] = False


# Idiom-scaffolding Java simple names to DROP (mirrors enumerate_surface.py's
# _SURFACE_EXCLUDED_CLASSES; here keyed by the bare simple name SignatureDump
# emits for the nested/helper type). All are port-only value/builder types with
# no reference counterpart.
_SIG_EXCLUDED_SIMPLE_NAMES: set[str] = {
    "RenderOptions",            # SwmlRenderer options-builder
    "SWAIGFunctionBuilder",     # SWAIGFunction options-builder
    "ValidationResult",         # (valid, errors) tuple record (swml/swaig/security)
    "AuthException",            # AuthHandler nested exception
    "AuthResult",               # AuthHandler nested result
    "BasicCredentials",         # AuthHandler nested credential type
    "BearerCredentials",        # AuthHandler nested credential type
    "RequestHandler",           # AuthHandler framework-neutral middleware wrapper
    "Response",                 # AuthHandler nested response value
    "LoggingLevel",             # logging enum helper (Logger.Level is the surface)
    "SkillParams",              # package-private skill base-schema helper
    "SWAIGFunctionHandler",     # @FunctionalInterface handler
    "ToolRegistryTool",         # ToolRegistry nested tool value
    "WebhookRejection",         # WebhookValidator.validate reject-triple record
    "HttpResult",               # Service.handleRequest (status, headers, body) triple record
    "InferredSchema",           # TypeInference.inferSchema 5-tuple stand-in record
}


def collect(raw: dict, aliases: dict, sidecar: dict[str, list[dict]] | None = None) -> tuple[dict, list]:
    out_modules: dict = {}
    failures: list = []
    sidecar = sidecar or {}

    for type_entry in raw.get("types", []):
        pkg = type_entry.get("package", "")
        java_name = type_entry.get("name", "")
        if not java_name:
            continue
        if type_entry.get("kind") in ("annotation",):
            continue

        # Idiom-scaffolding types (mirror enumerate_surface.py's
        # _SURFACE_EXCLUDED_CLASSES). These are the Java expression of a Python
        # kwargs bundle / return tuple / value object — a static-typing
        # NECESSITY, not reference surface: options-builders (the NAMED-param
        # idiom for many-optional-arg methods), validation-RESULT records
        # (Python returns a ``(bool, list)`` tuple), credential/exception/
        # response value types nested inside AuthHandler, the SWAIGFunction
        # builder, the LoggingLevel enum, and the private SkillParams helper.
        # The reference has no counterpart, so drop them on BOTH sides rather
        # than launder them as PORT_ADDITIONS (keeps the surface + signature
        # additions sets consistent). Guarded to hand packages only so a
        # generated DTO that happens to share a name is never dropped.
        if java_name in _SIG_EXCLUDED_SIMPLE_NAMES and not (
            pkg == _GENERATED_PKG or _gen_type_module(pkg) is not None
        ):
            continue

        # Generated-REST scaffolding drop (mirror enumerate_surface.py §8).
        # The generated package's typed-input BUILDER scaffolding — every
        # write/command/set method emits a nested ``<Method>Request`` + its
        # ``Builder`` (the Java NAMED idiom for keyword params, L13) — is
        # implementation detail of the typed input, not a route/resource, so
        # the oracle has no counterpart. Drop these here exactly as the
        # surface enumerator drops all nested classes in the package. Also
        # drop the ``ResourceTree`` plumbing base entirely (the oracle has no
        # ResourceTree; the RestClient's namespace accessors map to Python
        # instance attributes and are covered by PORT_ADDITIONS). The resource
        # classes (Mfa, Calling, …) and the namespace CONTAINERS
        # (RegistryNamespace, …) are kept.
        if pkg == _GENERATED_PKG:
            if java_name == "ResourceTree" or java_name.endswith("Request") or java_name == "Builder":
                continue

        # Generated wire-type / read-side-payload classes (item A/H + D). Route BY
        # PACKAGE to the oracle <ns>_types_generated / *_generated module (wins over
        # CLASS_TO_MODULE — a type name recurs / collides with an SDK class). Emit
        # each PUBLIC FIELD as a zero-arg accessor member carrying the EXACT wire-key
        # name (verbatim — the reference records the raw wire field name, NOT
        # snake-folded), returning the field's (loose) type. For the sig-oracle-ABSENT
        # modules (REST types / relay-proto / swaig_actions) these members are port
        # extras EXCUSED by the diff's port-state-accessor rule (loose return); for
        # the sig-oracle-PRESENT gen-payload modules (swml_verbs / post_prompt /
        # swaig_request) they satisfy the reference's class-typed field accessors (the
        # gen-payload fold + gen-type return equivalence tolerate loose vs class:).
        # An ENUM's own constants (static self-typed fields) are NOT data members —
        # skip them so a class-typed self-ref does not leak as un-excused drift.
        gen_type_mod = _gen_type_module(pkg)
        if gen_type_mod is not None:
            canonical_name = _gen_type_unrename(java_name)
            members: dict = {}
            for m in type_entry.get("methods", []):
                if not m.get("is_field"):
                    continue
                if m.get("is_static"):
                    continue  # enum constants / static tables are not data members
                wire = m.get("name", "")
                if not wire or wire.startswith("$"):
                    continue
                # Undo the generator's reserved-word field suffix (`default_` →
                # `default`, `enum_` → `enum`) so the recorded name is the bare wire
                # key the oracle records. The generator suffixed on a JAVA keyword.
                recorded = wire[:-1] if (wire.endswith("_") and wire[:-1] in _JAVA_FIELD_KEYWORDS) else wire
                # The wire-type DTO fields are LOOSELY typed (boxed scalar / Map /
                # List / Object — never a ref to another generated class, to keep the
                # emitter deterministic). The reference records the RICH field type
                # (``class:...AIParams`` / ``union<int,SWMLVar>``). Record the accessor
                # return as ``any`` — the wire-neutral loose form (mirrors php, whose
                # ?array/mixed properties translate to ``any``). ``any`` matches the
                # reference's rich type via the diff's any-rule (for the sig-oracle-
                # PRESENT gen-payload modules), and is port-state-accessor-excused for
                # the sig-oracle-ABSENT modules — so no un-excused return-mismatch and
                # no diff-tool change needed. (The RICH types live in the SURFACE, which
                # records only the class name; the field-level shape is not compared.)
                members[recorded] = {"params": [{"name": "self", "kind": "self"}],
                                     "returns": "any"}
            if members:
                out_modules.setdefault(gen_type_mod, {"classes": {}})
                out_modules[gen_type_mod]["classes"][canonical_name] = {
                    "methods": dict(sorted(members.items())),
                }
            continue

        # First check explicit Java module overrides
        full_pkg = f"{pkg}.{java_name}" if pkg else java_name
        # Nested-class rename (Java's bare ``Builder`` → canonical
        # ``AgentBaseBuilder`` etc.). Mirrors the surface enumerator's
        # ``effective_name = outer_name + renamed`` convention. Each entry
        # is ``(pkg, simple_name) → (canonical_class, canonical_module)``.
        nested_key = (pkg, java_name)
        nested_rename = JAVA_NESTED_CLASS_RENAMES.get(nested_key)
        if nested_rename is not None:
            canonical_name, mod = nested_rename
        else:
            canonical_name = (
                _CLASS_RENAMES.get(java_name)
                or JAVA_EXTRA_RENAMES.get(java_name)
                or JAVA_SKILL_RENAMES.get(java_name)
                or java_name
            )
            if full_pkg in JAVA_MODULE_OVERRIDES:
                mod = JAVA_MODULE_OVERRIDES[full_pkg]
            elif canonical_name in CLASS_TO_MODULE:
                mod = CLASS_TO_MODULE[canonical_name]
            else:
                # Unknown / port-only class: place at its own
                # ``signalwire.<pkg>.<snake_class>`` so multiple port-only
                # classes in the same Java package don't collide. Mirrors
                # surface enumerator's ``java_to_python_module`` placement.
                mod = _pkg_to_module_with_class(pkg, canonical_name)

        methods_out: dict = {}
        free_functions_out: list[tuple[str, str, dict]] = []  # (target_mod, target_fn, sig)
        for m in type_entry.get("methods", []):
            native = m.get("name", "")
            if native == "<init>":
                method_canonical = "__init__"
            else:
                if native.startswith("$"):
                    continue
                snake = camel_to_snake(native)
                method_canonical = _METHOD_RENAMES.get(snake, snake)
                if method_canonical in _PY_KEYWORDS:
                    continue
            # Free-function projection: a static method that should
            # surface as a module-level Python function, not a class
            # method.
            ff_key = (full_pkg, native)
            if ff_key in FREE_FUNCTION_PROJECTIONS:
                target_mod, target_fn = FREE_FUNCTION_PROJECTIONS[ff_key]
                override = FREE_FUNCTION_SIGNATURE_OVERRIDES.get(ff_key)
                if override is not None:
                    # Native Java shape doesn't translate to the canonical shape
                    # via reflection (no value-tuple type); emit the recorded
                    # oracle-exact signature. Deep-copy so later mutation (overload
                    # collapse) can't corrupt the shared table.
                    sig = json.loads(json.dumps(override))
                    free_functions_out.append((target_mod, target_fn, sig))
                    continue
                ctx = f"{target_mod}.{target_fn}"
                try:
                    sig = build_signature(m, aliases, ctx, target_mod, canonical_name)
                except TypeTranslationError as e:
                    failures.append(str(e))
                    continue
                # Strip implicit ``self`` — free functions have no receiver.
                # build_signature only adds self for non-static methods, so
                # any ``self`` prefix here means the source method wasn't
                # marked static; drop it for the projection regardless.
                params = sig.get("params", [])
                if params and params[0].get("kind") == "self":
                    sig["params"] = params[1:]
                free_functions_out.append((target_mod, target_fn, sig))
                continue
            ctx = f"{mod}.{canonical_name}.{method_canonical}"
            try:
                sig = build_signature(m, aliases, ctx, mod, canonical_name)
            except TypeTranslationError as e:
                failures.append(str(e))
                continue
            # Method-level signature override (regular methods whose native Java
            # shape — e.g. a value-tuple stand-in record return — doesn't
            # translate via reflection alone). Replace the reflected signature
            # with the canonical one keyed by (canonical_class, method).
            mo = METHOD_SIGNATURE_OVERRIDES.get((canonical_name, method_canonical))
            if mo is not None:
                sig = {"params": [dict(p) for p in mo["params"]], "returns": mo["returns"]}
                methods_out[method_canonical] = sig
                continue
            # Generated-REST typed-input unfold (L10). Java reflection sees a
            # write/command/set method as a single builder-``request`` param;
            # the generator's sidecar records the canonical flat keyword set the
            # oracle expects (body fields → keyword, ``extras`` → keyword, a
            # leading path-id → positional, a query bag → var_keyword). Replace
            # the reflected params with the sidecar's — keeping the implicit
            # ``self`` receiver — so the drift gate compares the oracle's shape.
            if pkg == _GENERATED_PKG and native != "<init>":
                unfold = sidecar.get(f"{java_name}::{native}")
                if unfold is not None:
                    new_params: list = []
                    if sig["params"] and sig["params"][0].get("kind") == "self":
                        new_params.append(sig["params"][0])
                    for p in unfold:
                        param: dict = {
                            "name": p["name"],
                            "type": p.get("type", "any"),
                            "required": bool(p.get("required", True)),
                        }
                        kind = p.get("kind", "keyword")
                        if kind != "positional":
                            param["kind"] = kind
                        if "default" in p:
                            param["default"] = p["default"]
                        new_params.append(param)
                    sig["params"] = new_params
            # Public fields project as zero-arg accessor methods only when
            # their type names an SDK class — primitive state fields drop
            # out (matches Python adapter's _is_sdk_class_type filter).
            if m.get("is_field"):
                ret = sig.get("returns", "")
                is_sdk = (
                    ret.startswith("class:")
                    or ret.startswith("optional<class:")
                    or ret.startswith("list<class:")
                    or (ret.startswith("union<") and "class:" in ret)
                )
                if not is_sdk:
                    continue
            # Java overloads collapse to one entry. Default: prefer the
            # fewer-param overload so the projection lines up with Python's
            # single signature. Exception (PREFER_FULL_OVERLOAD): a few methods
            # expose every Python optional positionally for full parity — keep
            # the MOST-param overload there instead.
            if method_canonical in methods_out:
                existing = methods_out[method_canonical]
                if (canonical_name, method_canonical) in PREFER_FULL_OVERLOAD:
                    n_new, n_old = len(sig["params"]), len(existing["params"])
                    if n_new < n_old:
                        continue
                    if n_new == n_old:
                        # Same arity (e.g. the all-String full-arity overload vs
                        # the SAME-arity fully-typed overload that swaps a couple
                        # closed-set params to enums — record_call format/direction,
                        # tap direction/codec). Both reach full reference parity;
                        # break the tie toward the overload that types MORE params
                        # (more ``class:`` refs) so the knowable closed sets surface
                        # as the wave-1 ``enum<...>`` contract instead of bare
                        # ``string``. The String full-arity overload is then just a
                        # forward-compat escape hatch the Java overload-collapse
                        # drops (no separate surface entry — same collapse model as
                        # every other Java overload).
                        if _typed_param_count(sig) <= _typed_param_count(existing):
                            continue
                elif len(sig["params"]) >= len(existing["params"]):
                    continue
            methods_out[method_canonical] = sig

        for target_mod, target_fn, sig in free_functions_out:
            out_modules.setdefault(target_mod, {"classes": {}})
            out_modules[target_mod].setdefault("functions", {})
            # Java overloads collapse — prefer the fewer-param overload so
            # the projection lines up with Python's single signature.
            existing = out_modules[target_mod]["functions"].get(target_fn)
            if existing is not None and len(sig.get("params", [])) >= len(existing.get("params", [])):
                continue
            out_modules[target_mod]["functions"][target_fn] = sig

        if not methods_out:
            continue

        out_modules.setdefault(mod, {"classes": {}})
        out_modules[mod]["classes"][canonical_name] = {
            "methods": dict(sorted(methods_out.items())),
        }

    # Mixin projection — methods may live on AgentBase OR on SWMLService
    # (its parent class). Tool/auth/state helpers are typically declared
    # on Service and inherited.
    ab_entry = out_modules.get("signalwire.core.agent_base", {}).get("classes", {}).get("AgentBase")
    svc_entry = out_modules.get("signalwire.core.swml_service", {}).get("classes", {}).get("SWMLService")
    if ab_entry or svc_entry:
        ab_methods = ab_entry["methods"] if ab_entry else {}
        svc_methods = svc_entry["methods"] if svc_entry else {}
        combined = {**svc_methods, **ab_methods}
        projected: set[str] = set()
        for (target_mod, target_cls), expected in MIXIN_PROJECTIONS.items():
            present = {m: combined[m] for m in expected if m in combined}
            if not present:
                continue
            out_modules.setdefault(target_mod, {"classes": {}})
            out_modules[target_mod]["classes"].setdefault(target_cls, {"methods": {}})
            out_modules[target_mod]["classes"][target_cls]["methods"].update(present)
            projected.update(present)
        # Drop projected methods from AgentBase only.
        for n in projected:
            ab_methods.pop(n, None)
        # ``handle_request`` is declared on both Java classes (SWMLService is the
        # framework-free dispatch core; AgentBase overrides it to render via its
        # own SWML pipeline). The SIGNATURE oracle records it only once — on the
        # base SWMLService — so drop AgentBase's identical override copy here to
        # avoid a spurious port-only signature (the SURFACE oracle DOES list it on
        # both, and the surface enumerator keeps both). Only drop when the base
        # already carries it, so this never hides a genuinely AgentBase-only method.
        if "handle_request" in svc_methods:
            ab_methods.pop("handle_request", None)
        if ab_entry and not ab_methods:
            out_modules["signalwire.core.agent_base"]["classes"].pop("AgentBase", None)
            if not out_modules["signalwire.core.agent_base"].get("classes"):
                out_modules.pop("signalwire.core.agent_base", None)

    # Mark the trailing var-keyword-door param optional on collapsed-kwargs
    # methods (post-#58 the oracle strips the tail; keep the port's optional
    # kwargs door excused as an optional extra, not an omission).
    _mark_kwargs_tails_optional(out_modules)

    sorted_modules = {}
    for k in sorted(out_modules):
        entry = out_modules[k]
        out_entry: dict = {}
        if entry.get("classes"):
            out_entry["classes"] = {
                cls: entry["classes"][cls] for cls in sorted(entry["classes"])
            }
        if entry.get("functions"):
            out_entry["functions"] = {
                fn: entry["functions"][fn] for fn in sorted(entry["functions"])
            }
        # Skip modules that ended up with neither classes nor functions
        # after the various projection passes.
        if out_entry:
            sorted_modules[k] = out_entry
    return {
        "version": "2",
        "generated_from": "signalwire-java JAR via SignatureDump (java.lang.reflect)",
        "modules": sorted_modules,
    }, failures


def _typed_param_count(sig: dict) -> int:
    """Count params whose canonical type names an SDK class/enum (``class:``
    head), used only as the PREFER_FULL_OVERLOAD same-arity tiebreak so the
    fully-typed overload (more closed-set enums) wins over the all-String one.
    Looks inside ``optional<…>`` / ``union<…>`` wrappers too."""
    n = 0
    for p in sig.get("params", []):
        t = p.get("type") or ""
        if "class:" in t:
            n += 1
    return n


def build_signature(method: dict, aliases: dict, context: str, mod: str, class_name: str) -> dict:
    params_out: list = []
    is_static = method.get("is_static", False)
    if not is_static:
        # Both instance methods and constructors get self
        params_out.append({"name": "self", "kind": "self"})

    for p in method.get("parameters", []):
        ctx = f"{context}[{p.get('name')}]"
        native = p.get("name", "")
        canonical = camel_to_snake(native) if native else native
        canonical_type = translate_java_type(p.get("type", ""), aliases, ctx)
        param: dict = {
            "name": canonical,
            "type": canonical_type,
            "required": True,  # Java has no defaults
        }
        if p.get("is_varargs"):
            param["kind"] = "var_positional"
        params_out.append(param)

    if method.get("name") == "<init>":
        return_canon = "void"
    else:
        return_canon = translate_java_type(method.get("return_type", "void"), aliases, context + "[->]")
    return {"params": params_out, "returns": return_canon}


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def run_dump() -> dict:
    """Build SDK JAR + run SignatureDump."""
    # Compile the helper if needed. Recompile when the .class is missing OR the
    # source is newer than the compiled class — otherwise an edited
    # SignatureDump.java is silently ignored (a stale .class kept serving the old
    # behavior, which once hid a sort-determinism fix).
    helper_src = HERE / "SignatureDump.java"
    helper_class = PORT_ROOT / "build" / "scripts" / "SignatureDump.class"
    if not helper_class.exists() or helper_src.stat().st_mtime > helper_class.stat().st_mtime:
        cp = subprocess.run(
            ["javac", "-parameters", str(HERE / "SignatureDump.java"), "-d", str(PORT_ROOT / "build" / "scripts")],
            capture_output=True, text=True, timeout=120,
        )
        if cp.returncode != 0:
            raise RuntimeError(f"javac failed:\n{cp.stderr}")

    _version_match = re.search(
        r"^version\s*=\s*['\"]([^'\"]+)['\"]",
        (PORT_ROOT / "build.gradle").read_text(),
        re.MULTILINE,
    )
    if not _version_match:
        raise RuntimeError("Could not parse version from build.gradle")
    jar = PORT_ROOT / "build" / "libs" / f"signalwire-sdk-{_version_match.group(1)}.jar"
    if not jar.exists():
        raise RuntimeError(f"SDK jar not found at {jar}; run ./gradlew build first")

    # Build classpath: helper + SDK + ~/.gradle dep cache (gson, websocket, slf4j)
    cp_parts = [str(PORT_ROOT / "build" / "scripts"), str(jar)]
    for dep_jar in (Path.home() / ".gradle" / "caches").rglob("*.jar"):
        cp_parts.append(str(dep_jar))
    classpath = ":".join(cp_parts)
    cp = subprocess.run(
        ["java", "-cp", classpath, "SignatureDump", str(jar)],
        capture_output=True, text=True, timeout=300,
    )
    if cp.returncode != 0:
        raise RuntimeError(f"SignatureDump failed:\n{cp.stderr}")
    return json.loads(cp.stdout)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--raw", type=Path, default=None,
                        help="Path to a pre-dumped SignatureDump JSON")
    parser.add_argument("--out", type=Path,
                        default=PORT_ROOT / "port_signatures.json")
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    global CLASS_TO_MODULE
    CLASS_TO_MODULE = build_class_to_module_map(PSDK / "python_surface.json")

    aliases = load_aliases()
    sidecar = load_rest_sidecar()

    if args.raw and args.raw.is_file():
        raw = json.loads(args.raw.read_text(encoding="utf-8"))
    else:
        raw = run_dump()

    canonical, failures = collect(raw, aliases, sidecar)
    if failures:
        print(f"enumerate_signatures: {len(failures)} translation failure(s)", file=sys.stderr)
        for f in failures[:30]:
            print(f"  - {f}", file=sys.stderr)
        if len(failures) > 30:
            print(f"  ... ({len(failures) - 30} more)", file=sys.stderr)
        if args.strict:
            return 1

    args.out.write_text(json.dumps(canonical, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    n_mods = len(canonical["modules"])
    n_methods = sum(sum(len(c["methods"]) for c in m.get("classes", {}).values()) for m in canonical["modules"].values())
    print(f"enumerate_signatures: wrote {args.out} ({n_mods} modules, {n_methods} methods)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
