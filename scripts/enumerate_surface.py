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


# Generated read-side payload modules whose classes expose the reference-recorded
# typed FIELDS on the SURFACE. Mirrors ruby's ORACLE_FIELD_ACCESSOR_MODULES
# (enumerate_surface.rb): the generate_rest/generate_swml emitters produce one
# method-less DTO per schema OBJECT, but the reference records a per-class SET of
# typed composition fields (class-typed / list<class> / union members — e.g.
# AIObject: [SWAIG, hints, languages, params, post_prompt, prompt, pronounce]).
# Java exposes each as a public snake-wire-key FIELD; emit exactly the ORACLE'S
# recorded member subset on each such class (oracle-GATED so scalar wire fields
# the reference does NOT record — ai_volume/global_data/post_prompt_url — are
# never over-emitted). Classes the oracle records with zero members stay
# method-less. Retires the swml_verbs_generated / post_prompt_generated surface
# omissions by EMISSION (RULES.md §2 — the fields are real, present, and
# reference-recorded; leaving them method-less was a blind enumerator, not a gap).
_ORACLE_FIELD_ACCESSOR_MODULES = (
    "signalwire.core.swml_verbs_generated",
    "signalwire.core.post_prompt_generated",
    "signalwire.core.swaig_request_generated",
)


def load_oracle_class_members(
    reference_json: Path,
) -> dict[tuple[str, str], set[str]]:
    """Return {(ref_module, ClassName): {member, ...}} for EVERY reference class
    in python_surface.json.

    Used by the accessor→member fold: a Java ``getX``/``setX``/``isX``/``hasX``/
    ``withX`` whose stripped snake form names a member the reference records on
    the SAME (module, class) is idiom re-expressing that member's read/write
    capability, so it folds onto the reference member NAME (RULES.md §2) instead
    of sitting in the additions allow-list. Reference class members come from the
    SURFACE oracle (the surface gate compares this set); the signature enumerator
    loads the signature oracle for its own parallel fold.
    """
    data = json.loads(reference_json.read_text(encoding="utf-8"))
    out: dict[tuple[str, str], set[str]] = {}
    for mod, entry in data.get("modules", {}).items():
        for cls, members in entry.get("classes", {}).items():
            out[(mod, cls)] = set(members)
    return out


# Accessor prefixes that re-express a reference MEMBER's read/write capability.
# ``get``/``is``/``has`` are readers, ``set``/``with`` are writers — a public
# reference field/attribute is read+write, so both fold onto the field name.
_ACCESSOR_PREFIX_RE = re.compile(r"^(?:get|set|is|has|with)_(?P<field>.+)$")

# Ctor / dunder names — never a surface CAPABILITY difference. Excluded from
# emission when they would be a port-only ADDITION (idiom_reaudit_brief cat3);
# kept only where the reference records the same dunder on that class.
_CTOR_DUNDER_NAMES = frozenset({
    "__init__", "__repr__", "__str__", "__eq__", "__hash__",
    "__enter__", "__exit__",
})


def exclude_ctor_dunder(
    modules: dict[str, dict],
    oracle_class_members: dict[tuple[str, str], set[str]],
) -> None:
    """In-place: drop ctor/dunder members that would be port-only additions (the
    reference records no such dunder on that class). Lockstep with the signature
    enumerator's exclusion."""
    for mod, entry in modules.items():
        for cls, methods in entry.get("classes", {}).items():
            ref_members = oracle_class_members.get((mod, cls), set())
            entry["classes"][cls] = [
                m for m in methods
                if not (m in _CTOR_DUNDER_NAMES and m not in ref_members)
            ]


def fold_accessors_to_members(
    modules: dict[str, dict],
    oracle_class_members: dict[tuple[str, str], set[str]],
) -> None:
    """In-place: fold each class's ``getX``/``setX``/``isX``/``hasX``/``withX``
    accessor onto the reference member ``X`` when the reference records ``X`` on
    the SAME (module, class). Getter+setter collapse onto the one field name
    (deduped). Members with no matching reference field are left untouched (they
    remain port-only additions for the cat1 review). Idiom, not omission
    (RULES.md §2) — applied uniformly so it covers every current & future class.
    """
    family = _agentbase_family_members(oracle_class_members)
    for mod, entry in modules.items():
        for cls, methods in entry.get("classes", {}).items():
            ref_members = oracle_class_members.get((mod, cls))
            if not ref_members:
                continue
            # An AgentBase-family class is compared as ONE flattened set (the diff's
            # ``agentbase-family`` fold), so the "accessor is itself a reference
            # member" guard below must consult the whole family, not just this class:
            # the reference files ``native_functions`` on AgentBase but
            # ``set_native_functions`` on AIConfigMixin. Gating on AgentBase alone
            # folded the setter away and the mixin's copy went missing.
            twin_names = ref_members
            if (mod, cls) == ("signalwire.core.agent_base", "AgentBase"):
                twin_names = ref_members | family
            folded: set[str] = set()
            for name in methods:
                m = _ACCESSOR_PREFIX_RE.match(name)
                # Do NOT fold when the accessor NAME is itself a reference member:
                # the reference deliberately records both the attribute AND its
                # accessor (e.g. AgentServer records both ``agents`` and
                # ``get_agents``), so ``get_agents`` must stay to match its twin.
                if (m and m.group("field") in ref_members
                        and name not in twin_names):
                    folded.add(m.group("field"))
                else:
                    folded.add(name)
            entry["classes"][cls] = sorted(folded)


def _agentbase_family_members(
    oracle_class_members: dict[tuple[str, str], set[str]],
) -> set[str]:
    """Union of every member the reference records on the AgentBase family.

    The family is AgentBase plus the mixins the reference composes it from — the
    same set ``_MIXIN_SURFACE_PROJECTIONS`` projects onto, which is exactly what
    the surface diff folds together as ``agentbase-family``. Defined as a function
    so it reads the projection table at CALL time (the table is declared further
    down this module) and stays in sync if a mixin is added there.
    """
    keys = {("signalwire.core.agent_base", "AgentBase")} | set(
        _MIXIN_SURFACE_PROJECTIONS
    )
    out: set[str] = set()
    for key in keys:
        out |= oracle_class_members.get(key, set())
    return out


def load_oracle_generated_members(
    reference_json: Path,
) -> dict[tuple[str, str], set[str]]:
    """Return {(ref_module, ClassName): {member, ...}} for the generated
    read-side payload modules that expose oracle-recorded field accessors.

    Only classes with a NON-EMPTY recorded member set are included (empty-member
    classes surface method-less either way). Mirrors ruby's
    load_oracle_generated_members.
    """
    data = json.loads(reference_json.read_text(encoding="utf-8"))
    out: dict[tuple[str, str], set[str]] = {}
    for mod in _ORACLE_FIELD_ACCESSOR_MODULES:
        entry = data.get("modules", {}).get(mod)
        if not entry:
            continue
        for cls, members in entry.get("classes", {}).items():
            if members:
                out[(mod, cls)] = set(members)
    return out


# A public instance FIELD declaration in a generated DTO body:
#   ``public <Type> <name>;`` (optionally ``final``, generic type, dotted type),
# optionally preceded by a ``@SerializedName("<wire>")`` annotation. The generated
# DTOs carry public snake-wire-key fields and no methods. The reference member
# name is the WIRE key: when a field is a Java reserved word the generator escapes
# it (``default`` → ``default_``, ``case`` → ``case_``) and records the true wire
# key in ``@SerializedName`` — so prefer the annotation value when present and fall
# back to the declared field name otherwise (no camelCase translation needed —
# these are already snake wire keys).
_PUBLIC_FIELD_RE = re.compile(
    r"(?:@com\.google\.gson\.annotations\.SerializedName\(\s*\"(?P<wire>[^\"]*)\"\s*\)"
    r"|@SerializedName\(\s*\"(?P<wire2>[^\"]*)\"\s*\)\s*)?"  # optional wire-key annotation
    r"\s*\bpublic\s+(?:static\s+|final\s+)*"       # modifiers (not a method: no '(')
    r"[\w.$]+(?:\s*<[^;{}()]*>)?(?:\s*\[\s*\])*"    # type (generics / arrays)
    r"\s+(?P<name>[A-Za-z_$][\w$]*)\s*"            # field name
    r"(?:=[^;{}()]*)?;",                            # optional initializer, ';'
)


def oracle_gated_field_accessors(
    dto_body: str,
    ref_module: str,
    ref_class: str,
    oracle_generated_members: dict[tuple[str, str], set[str]],
) -> list[str]:
    """Return the sorted oracle-recorded member subset a generated DTO exposes.

    ``dto_body`` is the raw brace-body of the DTO class. Extract its declared
    public field names, then keep exactly the members the oracle records for
    ``(ref_module, ref_class)``. Abort loudly (like ruby) if the oracle wants a
    member the DTO does not actually declare — the model must be regenerated or
    the oracle fixed, never silently under-emitted.
    """
    wanted = oracle_generated_members.get((ref_module, ref_class))
    if not wanted:
        return []
    declared: set[str] = set()
    for m in _PUBLIC_FIELD_RE.finditer(dto_body):
        # Prefer the @SerializedName wire key (reserved-word escapes record the
        # true key there); fall back to the declared field name.
        wire = m.group("wire") or m.group("wire2")
        declared.add(wire if wire else m.group("name"))
    missing = sorted(wanted - declared)
    if missing:
        raise SystemExit(
            f"error: generated model {ref_module}.{ref_class} is missing "
            f"oracle-recorded field(s) {missing}; regenerate the model or "
            f"update the oracle (declared public fields: {sorted(declared)})"
        )
    return sorted(wanted)


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
    # Python ``AgentBase.skill_manager`` is a @property holding the SkillManager
    # (a B1 composition attribute — a class-typed instance attr the surface oracle
    # now records via its composition-attr enrichment); Java exposes it through the
    # ``getSkillManager()`` getter (present ONLY on AgentBase). Strip the ``get_``
    # prefix so it folds onto the reference's attribute name (rename-not-omission;
    # mirrors the get_pom → pom projection).
    "get_skill_manager": "skill_manager",
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

# CLASS-SCOPED getter→field renames for the relay Event @dataclass surface and the
# AI-Chat response DTOs. The reference now records the Event/DTO @dataclass FIELDS as
# (zero-arg) members (call_state/control_id/messages/…); Java exposes each field as a
# public getter (getCallState()→get_call_state / getMessages()→get_messages). These
# getters ARE the Java idiom for the reference's bare field reads, so we fold each onto
# the reference field NAME here (rename-not-omission, RULES.md §2). Scoped per Java
# SOURCE class name (the value passed to translate_method_name as class_name is the raw
# Java nested-type name — CallDialEvent, MessagingReceiveEvent, RelayEvent) so the fold
# only fires inside the event/DTO classes and a plain get_x elsewhere stays a getter.
# Imported by enumerate_signatures.py so the SURFACE and SIGNATURE gates agree.
# Getters with NO reference field (get_call_id on the subclasses, get_result_type,
# get_detect_event, get_dial_state_enum, get_call_info, get_refer_state, get_string_param)
# are deliberately absent here — they remain annotated PORT_ADDITIONS (extra Java
# accessor surface the reference lacks).
_EVENT_METHOD_RENAMES_BY_CLASS: dict[str, dict[str, str]] = {
    "CallCollectEvent": {"get_control_id": "control_id", "get_final": "final", "get_result": "result", "get_state": "state"},
    "CallConnectEvent": {"get_connect_state": "connect_state", "get_peer": "peer"},
    "CallDetectEvent": {"get_control_id": "control_id", "get_detect": "detect"},
    "CallDialEvent": {"get_call": "call", "get_dial_state": "dial_state", "get_tag": "tag"},
    "CallFaxEvent": {"get_control_id": "control_id", "get_fax": "fax"},
    "CallPayEvent": {"get_control_id": "control_id", "get_state": "state"},
    "CallPlayEvent": {"get_control_id": "control_id", "get_state": "state"},
    "CallReceiveEvent": {"get_call_state": "call_state", "get_context": "context", "get_device": "device", "get_direction": "direction", "get_node_id": "node_id", "get_project_id": "project_id", "get_segment_id": "segment_id", "get_tag": "tag"},
    "CallRecordEvent": {"get_control_id": "control_id", "get_duration": "duration", "get_record": "record", "get_size": "size", "get_state": "state", "get_url": "url"},
    "CallReferEvent": {"get_sip_notify_response_code": "sip_notify_response_code", "get_sip_refer_response_code": "sip_refer_response_code", "get_sip_refer_to": "sip_refer_to", "get_state": "state"},
    "CallSendDigitsEvent": {"get_control_id": "control_id", "get_state": "state"},
    "CallStateEvent": {"get_call_state": "call_state", "get_device": "device", "get_direction": "direction", "get_end_reason": "end_reason"},
    "CallStreamEvent": {"get_control_id": "control_id", "get_name": "name", "get_state": "state", "get_url": "url"},
    "CallTapEvent": {"get_control_id": "control_id", "get_device": "device", "get_state": "state", "get_tap": "tap"},
    "CallTranscribeEvent": {"get_control_id": "control_id", "get_duration": "duration", "get_recording_id": "recording_id", "get_size": "size", "get_state": "state", "get_url": "url"},
    "CallingErrorEvent": {"get_code": "code", "get_message": "message"},
    "ConferenceEvent": {"get_conference_id": "conference_id", "get_name": "name", "get_status": "status"},
    "DenoiseEvent": {"is_denoised": "denoised"},
    "EchoEvent": {"get_state": "state"},
    "HoldEvent": {"get_state": "state"},
    "MessagingReceiveEvent": {"get_body": "body", "get_context": "context", "get_direction": "direction", "get_from_number": "from_number", "get_media": "media", "get_message_id": "message_id", "get_message_state": "message_state", "get_segments": "segments", "get_tags": "tags", "get_to_number": "to_number"},
    "MessagingStateEvent": {"get_body": "body", "get_context": "context", "get_direction": "direction", "get_from_number": "from_number", "get_media": "media", "get_message_id": "message_id", "get_message_state": "message_state", "get_reason": "reason", "get_segments": "segments", "get_tags": "tags", "get_to_number": "to_number"},
    "QueueEvent": {"get_control_id": "control_id", "get_position": "position", "get_queue_id": "queue_id", "get_queue_name": "queue_name", "get_size": "size", "get_status": "status"},
    "RelayEvent": {"get_call_id": "call_id", "get_event_type": "event_type", "get_params": "params", "get_timestamp": "timestamp"},
    # AI-Chat response @dataclass DTOs (java source class names == reference names).
    "ChatLog": {"get_call_timeline": "call_timeline", "get_messages": "messages"},
    "ChatResponse": {"get_conversation_id": "conversation_id", "get_text": "text", "get_user_event": "user_event"},
    "ConversationInfo": {"get_id": "id", "get_initial_message": "initial_message", "get_status": "status"},
    # AI-Chat class-B2 attributes: the oracle records ``AIChatError.code``/``.message``
    # and ``AIChatClient.url`` as members (public __init__ attributes that are also
    # ctor params). Java's read side is getCode()/getServerMessage()/getUrl() — fold
    # each onto its reference member name. ``getServerMessage`` is the Java spelling
    # (``getMessage`` is final on java.lang.Throwable, so the port cannot use it); it
    # is the same ``message`` attribute the reference exposes.
    "AIChatError": {"get_code": "code", "get_server_message": "message"},
    "AIChatClient": {"get_url": "url"},
    # RelayError mirrors AIChatError: the reference keeps the RAW server message as
    # ``self.message`` while the exception TEXT is decorated ("RELAY error {code}:
    # {message}"). ``getMessage()`` on java.lang.Throwable returns the decorated form,
    # so the port declares ``getServerMessage()`` for the undecorated value — fold it
    # onto the reference's ``message`` attribute.
    "RelayError": {"get_code": "code", "get_server_message": "message"},
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


# Composition-delegate strip (§4c.1). Python keeps these methods on a HELPER OBJECT
# that AgentBase holds by composition (``render_swml``→SwmlRenderer,
# ``get_contexts``/``get_raw_prompt``→PromptManager, ``create_tool_token``→
# SessionManager, ``extract_sip_username``→SWMLService). Java flattens the delegate's
# method ALSO onto AgentBase as a pass-through convenience, so the method surfaces
# BOTH on AgentBase and on the canonical helper class. The helper-class copy already
# matches the reference (which files the method under the helper); the AgentBase copy
# is the flattened duplicate that — after the diff's ``agentbase-family`` fold — reads
# as a phantom addition (``agentbase-family.render_swml`` etc.). Strip it from
# AgentBase's OWN declared list, but ONLY when the SAME method is already emitted on
# its canonical helper class (guard: never drop a method that genuinely lives only on
# AgentBase). Mirrors how _MIXIN_SURFACE_PROJECTIONS strips a projected method from
# AgentBase after emitting it at the canonical path. Each entry:
#   method name → (canonical helper module, helper class) where the reference files it.
_COMPOSITION_DELEGATE_STRIP: dict[str, tuple[str, str]] = {
    "render_swml": ("signalwire.core.swml_renderer", "SwmlRenderer"),
    "get_contexts": ("signalwire.core.agent.prompt.manager", "PromptManager"),
    "get_raw_prompt": ("signalwire.core.agent.prompt.manager", "PromptManager"),
    "create_tool_token": ("signalwire.core.security.session_manager", "SessionManager"),
    "extract_sip_username": ("signalwire.core.swml_service", "SWMLService"),
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
    # B1 composition-attribute getters (class D catalog, B1). The reference oracle's
    # composition-attr enrichment now records these class-typed instance attributes as
    # members; Java exposes each through a ``getX()`` accessor. Strip the ``get_``
    # prefix so the Java getter folds onto the reference's attribute name
    # (rename-not-omission — the getter is the Java accessor idiom for a Python
    # instance attribute, wire-neutral). Scoped per-(module, class) so the rename never
    # over-matches a same-named getter on an unrelated class (``getResult`` also exists
    # on CollectEvent, which the reference does NOT surface — excluded here).
    # NOTE: AgentServer is intentionally NOT aliased — the reference has BOTH
    # ``agents`` (the dict attribute) AND ``get_agents`` (the accessor method) as
    # distinct members; Java ships a single ``getAgents()`` that matches the
    # reference's ``get_agents`` method, so the bare ``agents`` attribute is a
    # genuine B1 omission (no separate field member) rather than a rename target.
    ("signalwire.pom.pom", "PromptObjectModel"): {"get_sections": "sections"},
    ("signalwire.pom.pom", "Section"): {
        "get_subsections": "subsections",
        # The reference declares this attribute in camelCase VERBATIM
        # (``self.numberedBullets``, pom.py:50) because it is also the wire key
        # emitted into the POM dict (pom.py:125). So the generic camel→snake
        # translation of Java's ``isNumberedBullets()`` (→ ``numbered_bullets``)
        # under-shoots the reference member name; rename it to the camelCase
        # spelling the oracle actually records. Wire-neutral — same field.
        "is_numbered_bullets": "numberedBullets",
    },
    # RelayClient's ``getSpace()`` is the read side of the reference's ``host``
    # construction param: both hold the space hostname sourced from
    # ``SIGNALWIRE_SPACE`` (reference client.py:174 ``self.host = host or
    # os.environ.get("SIGNALWIRE_SPACE", ...)``; Java's ``space`` field is
    # populated from the same env var). A spelling difference for one value —
    # rename, not omission (RULES.md §2).
    ("signalwire.relay.client", "RelayClient"): {"get_space": "host"},
    # DataMap's ``getName()`` returns the ``functionName`` field — the read side of
    # the reference's ``function_name`` construction param (data_map.py:72). The
    # oracle records ``function_name`` and NO ``get_name``/``name`` member, so the
    # generic camel→snake fold (→ ``name``) under-shoots; rename it onto the field
    # the reference actually exposes.
    ("signalwire.core.data_map", "DataMap"): {"get_name": "function_name"},
    # SkillBase is an INTERFACE in this port, and a Java interface cannot declare a
    # constructor. The reference's construction contract — ``SkillBase(agent, params)``,
    # which stores ``self.agent``/``self.params`` (skill_base.py:33-40) — is expressed
    # here as ``bind(agent, params)``, called by SkillManager immediately before
    # ``setup()`` (the same construct-then-setup ordering). It carries the identical
    # param set, so it folds onto ``__init__`` rather than being filed as an addition:
    # same capability, different shape, reconciled at the emitter (RULES.md §2).
    ("signalwire.core.skill_base", "SkillBase"): {"bind": "__init__"},
    ("signalwire.relay.call", "Action"): {"get_result": "result"},
    ("signalwire.relay.message", "Message"): {"get_result": "result"},
    ("signalwire.web.web_service", "WebService"): {"get_security": "security"},
    # SWMLService now builds its unified SecurityConfig from the construction
    # ``config_file`` (as the reference does at swml_service.py:139) and exposes
    # it via ``getSecurity()`` — the Java accessor idiom for the reference's
    # ``security`` composition attribute, identical to the WebService row above.
    # This retires the old "Java's SWML Service exposes no SecurityConfig member"
    # PORT_OMISSIONS line: the capability is now present, so the fold applies.
    ("signalwire.core.swml_service", "SWMLService"): {"get_security": "security"},
}


# Construction-parameter READ accessors. Java expresses a wide many-optional-arg
# constructor as a builder + per-param getters; the param SET is already the
# compared contract (the ``construction`` node in port_signatures.json, bound via
# ``_BUILDER_CONSTRUCTS`` in enumerate_signatures.py). The getter is the read side
# of that same construction param — the Java idiom for reading a value the
# reference stores as plain scalar ``self.<name>`` state, which the surface oracle
# deliberately does NOT enumerate (it records only class-TYPED composition
# attributes; see enumerate_python.py::_enrich_composition_attributes). So there is
# no reference member to fold ONTO and the accessor is not independent surface
# either — it is construction idiom, and idiom is folded at the emitter (RULES.md
# §2 / ALLOWLIST_DISCIPLINE.md §0), never filed as an addition.
#
# Keyed by (reference module, class) → the accessor names to drop from the compared
# surface. Every name here MUST be a param in that class's construction contract,
# so the capability stays compared — just at the construction node rather than as a
# duplicate method member.
#
# ORACLE-GATED since class B2: the "oracle does not enumerate scalar state" premise
# above is no longer true for a public ``__init__`` attribute that is ALSO a ctor
# param — the oracle records those as real members. ``strip_construction_param_accessors``
# therefore skips any entry whose underlying field the oracle records on that class;
# the accessor folds onto the member instead (that is the READ-BACK capability
# CONSTRUCTION-READBACK enforces). Entries below stay listed because the gate is
# computed, not hand-maintained: if a later oracle revision starts recording one of
# these fields, the strip stops applying to it automatically.
_CONSTRUCTION_PARAM_ACCESSORS: dict[tuple[str, str], frozenset[str]] = {
    ("signalwire.core.agent_base", "AgentBase"): frozenset({
        "get_agent_id",                    # agent_id
        "get_default_webhook_url",         # default_webhook_url
        "get_native_functions",            # native_functions
        "get_token_expiry_secs",           # token_expiry_secs
        "is_check_for_input_override",     # check_for_input_override
        "is_enable_post_prompt_override",  # enable_post_prompt_override
        "is_suppress_logs",                # suppress_logs
        "is_use_pom",                      # use_pom
    }),
    ("signalwire.core.swml_service", "SWMLService"): frozenset({
        "get_config_file",                 # config_file
        "get_schema_path",                 # schema_path
        "is_schema_validation",            # schema_validation
    }),
    # SchemaUtils.__init__(schema_path, schema_validation) — ``getSchemaPath()`` is
    # the read side of its own ``schema_path`` construction param (the reference
    # reads it as ``self.schema_utils.schema_path`` at agent_base.py:210).
    ("signalwire.utils.schema_utils", "SchemaUtils"): frozenset({
        "get_schema_path",                 # schema_path
    }),
    # The WRITE side of the same contract. AgentBaseBuilder's setters ARE
    # AgentBase's construction parameter set — ``_BUILDER_CONSTRUCTS`` in
    # enumerate_signatures.py binds them to the ``construction`` node for
    # ``signalwire.core.agent_base.AgentBase``, where each is compared by NAME
    # against the reference's ``__init__`` param of the same name. Emitting them a
    # SECOND time as builder methods would double-count construction idiom as
    # independent surface. (The pre-existing builder setters are still carried in
    # PORT_ADDITIONS.md as self-declared idiom; that whole block is slated for the
    # fold-to-zero campaign — this table is where they land when it runs.)
    ("signalwire.agent.agent_base_builder", "AgentBaseBuilder"): frozenset({
        "agent_id",
        "check_for_input_override",
        "config_file",
        "default_webhook_url",
        "enable_post_prompt_override",
        "native_functions",
        "schema_path",
        "schema_validation",
        "suppress_logs",
        "token_expiry_secs",
        "use_pom",
    }),
}


def strip_construction_param_accessors(
    modules: dict[str, dict],
    oracle_class_members: dict[tuple[str, str], set[str]] | None = None,
) -> None:
    """In-place: drop the construction-param READ accessors listed in
    ``_CONSTRUCTION_PARAM_ACCESSORS``.

    ORACLE-GATED (class B2). The table's premise is that the surface oracle does
    NOT enumerate plain scalar ``self.<name>`` state, so a construction-param
    accessor has no reference member to fold ONTO. Oracle class B2 changed that
    for a subset: the reference oracle now records a public ``__init__``
    attribute that is also a ctor param as a real MEMBER of its class. For those,
    the accessor must FOLD onto the member (``fold_accessors_to_members`` ran
    just before this) — stripping it would delete a member the reference has,
    which is CONSTRUCTION-READBACK's exact failure mode: the caller can set the
    value but can no longer read it back.

    So an entry only strips when the oracle does NOT record the underlying field
    on that class. Entries whose field the oracle DOES record are inert here and
    keep the folded member. The table stays keyed by accessor name; the field is
    derived by removing the ``get_``/``is_``/``has_`` prefix (a bare name — the
    builder WRITE side — is its own field name).
    """
    oracle_class_members = oracle_class_members or {}
    for (mod, cls), names in _CONSTRUCTION_PARAM_ACCESSORS.items():
        entry = modules.get(mod)
        if not entry:
            continue
        methods = entry.get("classes", {}).get(cls)
        if methods is None:
            continue
        ref_members = oracle_class_members.get((mod, cls), set())
        drop = {
            name for name in names
            if _construction_accessor_field(name) not in ref_members
        }
        entry["classes"][cls] = [m for m in methods if m not in drop]


def _construction_accessor_field(accessor: str) -> str:
    """``get_agent_id`` → ``agent_id``; ``is_use_pom`` → ``use_pom``; a bare
    builder-setter name (``schema_path``) is already the field name."""
    for prefix in ("get_", "is_", "has_"):
        if accessor.startswith(prefix):
            return accessor[len(prefix):]
    return accessor


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
    # AI Chat options-builders — the Java NAMED-param idiom for the reference's
    # many-optional-kwargs methods (AIChatClient.__init__, chat, create_conversation,
    # summarize). The reference passes these as keyword args on the method itself; Java
    # models each optional-arg bundle as an immutable Options value type plus its fluent
    # Builder. No reference class counterpart (like SWAIGFunctionBuilder / the generated
    # <Method>Request+Builder scaffolding) — drop, not launder as PORT_ADDITIONS. The
    # Builder inner types surface outer-qualified (parse_type_body emits nested types as
    # <Outer><Inner>), hence both the Options and the <Options>Builder names.
    "AIChatClientOptions",
    "AIChatClientOptionsBuilder",
    "ChatOptions",
    "ChatOptionsBuilder",
    "CreateConversationOptions",
    "CreateConversationOptionsBuilder",
    "SummarizeOptions",
    "SummarizeOptionsBuilder",
}


# AI Chat value/error/client classes — fold the Java read-only getter idiom onto
# the reference's attribute surface. The Python reference models the response types
# (ConversationInfo/ChatResponse/ChatLog) as @dataclass and the errors
# (AIChatError + subclasses) as plain Exception subclasses; the surface oracle
# records their FIELDS as attributes, not methods, so it lists them method-less
# (the subclasses) / with only __init__ (AIChatError's explicit constructor). Java
# expresses the same fields as an immutable class with public getters
# (``getId()``/``getCode()``/…) + a constructor. Those getters are the Java idiom for
# the reference's bare attribute reads — they fold onto the (un-enumerated) attribute
# surface, i.e. they are DROPPED, exactly as the enumerator drops the generated-DTO
# accessors. AIChatClient additionally exposes ``getUrl()`` for its public ``url``
# attribute (the reference sets ``self.url`` — an attribute, not a method), so it is
# dropped too. Keyed by SURFACE class name → the exact member set to keep.
#
# NOT a laundered omission: this REMOVES port-only getter surface the reference never
# had; the members that remain (AIChatClient's API verbs, AIChatError.__init__) match
# the oracle exactly. The reference-only ``__aenter__``/``__aexit__`` on AIChatClient
# are the async-context-manager (``async with``) scoped-lifetime idiom; Java expresses
# the IDENTICAL capability through ``AutoCloseable`` + try-with-resources
# (``try (var c = new AIChatClient(...))``), whose ``close()`` is the analogue of the
# reference's ``close``/``__aexit__``. So the two dunders FOLD onto AutoCloseable and
# are INJECTED into the surface (see the injection at the override apply site below) —
# target ZERO ai_chat omissions, exactly as .NET folds IDisposable/``using`` (its
# SURFACE_METHOD_INJECTIONS injects the same names).
_AI_CHAT_MEMBER_OVERRIDES: dict[str, list[str]] = {
    # Response @dataclasses — the oracle now records their @dataclass FIELDS as members;
    # Java's getters fold onto those field names via _EVENT_METHOD_RENAMES_BY_CLASS, so
    # keep exactly the reference field set here.
    "ConversationInfo": ["id", "initial_message", "status"],
    "ChatResponse": ["conversation_id", "text", "user_event"],
    "ChatLog": ["call_timeline", "messages"],
    # Error hierarchy — AIChatError keeps its explicit __init__ (oracle records it)
    # PLUS the two class-B2 attributes the oracle now records (``code``/``message``
    # are public __init__ attributes that are also ctor params). Java's getCode() /
    # getServerMessage() are the read side; they fold onto those member names via
    # _EVENT_METHOD_RENAMES_BY_CLASS (get_server_message → message), so both must be
    # listed here or the fold's output is filtered straight back out and the caller
    # loses the read-back. Subclasses add no members of their own (oracle records
    # them empty; their Java ctor is the boilerplate super() delegator).
    "AIChatError": ["__init__", "code", "message"],
    "AuthenticationError": [],
    "ConversationNotFoundError": [],
    "RateLimitError": [],
    "ChatInProgressError": [],
    "SummaryError": [],
    # Client — keep the API verbs + __init__ + the AutoCloseable ``close``; INJECT the
    # ``__aenter__``/``__aexit__`` context-manager dunders (folded onto AutoCloseable /
    # try-with-resources, the Java analogue of Python's ``async with`` — see the inject
    # note at the apply site). ``url`` is a class-B2 attribute the oracle now records
    # (a public __init__ attribute that is also a ctor param); Java's getUrl() folds
    # onto it, so the MEMBER name is kept here — dropping it would take the read-back
    # away from Java callers that the reference gives Python callers.
    "AIChatClient": [
        "__aenter__", "__aexit__", "__init__", "chat", "close", "create_conversation",
        "delete", "end", "log", "summarize", "url",
    ],
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
    # Class-scoped getter→field fold for the relay Event / AI-Chat DTO @dataclass
    # surface (class_name is the raw Java source class name). Takes precedence over
    # the global _METHOD_RENAMES so a getter maps to its per-class reference field.
    scoped = _EVENT_METHOD_RENAMES_BY_CLASS.get(class_name)
    if scoped and snake in scoped:
        return scoped[snake]
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
                   native: bool = False,
                   oracle_generated_members:
                       dict[tuple[str, str], set[str]] | None = None,
                   ) -> dict[str, dict]:
    """Return {module: {"classes": {Name: [methods]}, "functions": []}}."""
    oracle_generated_members = oracle_generated_members or {}
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
        # Read-side payload DTOs in the field-accessor modules
        # (swml_verbs_generated / post_prompt_generated) expose the reference-
        # recorded typed composition FIELDS on the surface — emit exactly the
        # oracle-gated subset (RULES.md §2 fold-not-omit). All other generated
        # type modules record their classes method-less, so emit [].
        # Use the RAW body (not the comment/string-stripped one) so the
        # ``@SerializedName("<wire>")`` reserved-word escapes survive — string
        # literals are blanked in ``stripped``, indices are length-preserved.
        raw_body = raw[body_open + 1 : body_close]
        members = oracle_gated_field_accessors(
            raw_body, gen_type_mod, canonical, oracle_generated_members,
        )
        return {gen_type_mod: {"classes": {canonical: members}, "functions": []}}

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
        # AI Chat getter-idiom fold: pin the value/error/client classes to their
        # reference member set (drops the read-only getters that fold onto the
        # reference's bare attributes; keeps __init__ where the oracle records it).
        # Native mode keeps the Java surface verbatim for doc resolution.
        if not native and cls_name in _AI_CHAT_MEMBER_OVERRIDES:
            keep = set(_AI_CHAT_MEMBER_OVERRIDES[cls_name])
            methods = [m for m in methods if m in keep]
            # Inject the AutoCloseable-folded context-manager dunders that no Java
            # method name produces directly: AIChatClient ``implements AutoCloseable``,
            # so ``try (var c = new AIChatClient(...))`` expresses the reference's
            # ``async with`` scoped lifetime and its public ``close()`` is the
            # ``__aexit__`` analogue. The capability is real; inject the two dunder
            # names so the surface matches the oracle with ZERO omissions (mirrors
            # .NET's IDisposable/``using`` fold). Guard on the real ``close`` member so
            # the injection only fires when the AutoCloseable capability is present.
            for injected in ("__aenter__", "__aexit__"):
                if injected in keep and "close" in methods:
                    methods.append(injected)
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
                  native: bool = False,
                  oracle_generated_members:
                      dict[tuple[str, str], set[str]] | None = None,
                  oracle_class_members:
                      dict[tuple[str, str], set[str]] | None = None,
                  ) -> dict[str, dict]:
    """Walk ``java_src_root/com/signalwire/sdk`` and collect all classes."""
    merged: dict[str, dict] = {}
    for path in sorted(java_src_root.rglob("*.java")):
        per_file = enumerate_file(
            path, class_to_module, native=native,
            oracle_generated_members=oracle_generated_members,
        )
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

    # Accessor→member fold (RULES.md §2): collapse each class's getX/setX/isX/
    # hasX/withX onto the reference member X it re-expresses. Reference-keyed, so
    # python-reference mode only. Runs before the mixin projection so folded
    # names project correctly.
    if not native and oracle_class_members:
        fold_accessors_to_members(merged, oracle_class_members)
        exclude_ctor_dunder(merged, oracle_class_members)

    # Construction-param accessor strip (RULES.md §2 / ALLOWLIST_DISCIPLINE.md §0):
    # the read+write accessors for params already compared by the ``construction``
    # contract are construction idiom, not independent surface. Reference-keyed →
    # python-reference mode only.
    if not native:
        strip_construction_param_accessors(merged, oracle_class_members)

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
        # Composition-delegate strip (§4c.1): drop the flattened pass-through copy
        # of a helper-object method from AgentBase when the SAME method is already
        # emitted on its canonical helper class (so the reference's helper filing is
        # matched by the helper copy, and the AgentBase duplicate stops reading as a
        # phantom ``agentbase-family`` addition). Guarded on helper-class presence.
        for _dm, (_hmod, _hcls) in _COMPOSITION_DELEGATE_STRIP.items():
            if _dm not in ab_methods:
                continue
            helper_members = (
                merged.get(_hmod, {}).get("classes", {}).get(_hcls, [])
            )
            if _dm in helper_members:
                ab_methods = [m for m in ab_methods if m != _dm]
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


def _collect_crud_bases(repo_root: Path,
                        class_to_module: dict[str, str]) -> dict[str, dict]:
    """Emit the top-level ``crud_bases`` map (spec-driven REST parity, class D1).

    ``scripts/generate_rest.py`` already records each generated REST resource's
    structural CRUD contract (base + typed bind) in the generated
    ``rest_signatures.json`` sidecar, keyed by the bare resource class name
    (``ConferenceRooms`` → {base, bind}). The SURFACE oracle carries the same
    binding as a top-level ``crud_bases`` map keyed by the reference dotted
    ``<module>.<Class>`` path, so ``diff_port_surface._fold_crud_methods`` folds
    each resource's inherited CRUD ops (list/get/create/update/delete/paginate)
    structurally via the UNION of the reference's and this port's maps — no
    per-resource allow-list, no per-op class rename.

    We route each resource class to its reference module via the SAME
    class→module map the surface enumerator uses (the class names are the oracle
    canonical names, so they resolve to ``signalwire.rest.namespaces.<ns>_
    resources_generated.<Class>``, matching the reference's ``crud_bases`` keys).
    A resource absent from the class map (should not happen for a shipped
    resource) is skipped rather than emitted under a degraded path.
    """
    sidecar = (
        repo_root / "src" / "main" / "java" / "com" / "signalwire" / "sdk"
        / "rest" / "namespaces" / "generated" / "rest_signatures.json"
    )
    if not sidecar.is_file():
        return {}
    data = json.loads(sidecar.read_text(encoding="utf-8"))
    raw = data.get("crud_bases", {})
    out: dict[str, dict] = {}
    for cls_name, binding in raw.items():
        mod = class_to_module.get(cls_name)
        if mod is None:
            continue  # not a reference-known resource class; skip (don't degrade)
        out[f"{mod}.{cls_name}"] = {
            "base": binding.get("base"),
            "bind": list(binding.get("bind", [])),
        }
    return out


def build_snapshot(repo_root: Path, reference_json: Path,
                   native: bool = False) -> dict:
    class_to_module = build_class_to_module_map(reference_json)
    # In native mode the class names stay Java-spelled and don't line up with the
    # oracle's reference class keys, so the field-accessor emission (which is
    # keyed by reference class name) applies only to python-reference mode.
    oracle_generated_members = (
        {} if native else load_oracle_generated_members(reference_json)
    )
    oracle_class_members = (
        {} if native else load_oracle_class_members(reference_json)
    )
    java_src = repo_root / "src" / "main" / "java"
    if not java_src.is_dir():
        raise SystemExit(f"error: java source not found at {java_src}")
    modules = enumerate_sdk(
        java_src, class_to_module, native=native,
        oracle_generated_members=oracle_generated_members,
        oracle_class_members=oracle_class_members,
    )
    snapshot = {
        "version": "1",
        "generated_from": f"signalwire-java @ {git_sha(repo_root)}",
        "language": "java",
        "names": "java-native" if native else "python-reference",
        "modules": modules,
    }
    # Spec-driven REST parity (class D1): carry the crud_base bindings so the diff
    # folds each resource's inherited CRUD ops structurally. Only in
    # python-reference mode (the class→module map + fold are reference-keyed).
    if not native:
        crud_bases = _collect_crud_bases(repo_root, class_to_module)
        if crud_bases:
            snapshot["crud_bases"] = crud_bases
    return snapshot


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
