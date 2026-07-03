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
    "get_compat": "compat",
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
    # ExecutionMode helpers
    ("ExecutionMode", "getExecutionMode"): ("signalwire.core.logging_config", "get_execution_mode"),
    ("ExecutionMode", "isServerlessMode"): ("signalwire.utils", "is_serverless_mode"),
    # UrlValidator
    ("UrlValidator", "validateUrl"): ("signalwire.utils.url_validator", "validate_url"),
    # SecurityUtils static helpers → signalwire.core.security.security_utils
    # free functions (the Python reference exports them as bare module
    # functions; Java groups them on a static-only utility class).
    ("SecurityUtils", "filterSensitiveHeaders"):
        ("signalwire.core.security.security_utils", "filter_sensitive_headers"),
    ("SecurityUtils", "redactUrl"):
        ("signalwire.core.security.security_utils", "redact_url"),
    ("SecurityUtils", "isValidHostname"):
        ("signalwire.core.security.security_utils", "is_valid_hostname"),
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
        "get_language_params", "set_language_params",
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
    """Replace comments and string/char literals with placeholders.

    We care about structural tokens (``public``, ``class``, ``{``, ``}``).
    Comments and string contents can contain those tokens and would confuse
    a brace walker; blanking them is safer than regex-matching around them.
    """
    src = _BLOCK_COMMENT.sub(lambda m: " " * len(m.group(0)), src)
    src = _LINE_COMMENT.sub(lambda m: " " * len(m.group(0)), src)
    src = _STRING_LIT.sub(lambda m: '"' + " " * (len(m.group(0)) - 2) + '"', src)
    src = _CHAR_LIT.sub(lambda m: "'" + " " * (len(m.group(0)) - 2) + "'", src)
    return src


# Matches a ``public class/interface/enum/record Name`` header.
_TYPE_HEADER = re.compile(
    r"\bpublic\s+"
    r"(?:static\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+)*"
    r"(?P<kind>class|interface|enum|record|@interface)\s+"
    r"(?P<name>[A-Z][A-Za-z0-9_]*)"
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

    # Walk the body extracting public methods + public nested types.
    i = 0
    while i < len(src):
        m_type = _TYPE_HEADER.search(src, i)
        m_meth = _METHOD_HEADER.search(src, i)

        # Pick whichever comes first.
        next_type_pos = m_type.start() if m_type else len(src) + 1
        next_meth_pos = m_meth.start() if m_meth else len(src) + 1

        if m_type is None and m_meth is None:
            break

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
            inner_classes = parse_type_body(
                inner_body, effective_name, known_python_classes,
                java_outer_name=name, native=native,
            )
            for cls_name, cls_methods in inner_classes.items():
                if cls_name in classes:
                    classes[cls_name].extend(cls_methods)
                else:
                    classes[cls_name] = cls_methods
            i = body_close + 1
        else:
            name = m_meth.group("name")
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
      1. If the class name exists in ``python_surface.json``, use the
         reference module.
      2. Otherwise translate the Java package naturally (drop
         ``com.signalwire.sdk``, snake_case each segment, prepend
         ``signalwire.``), then append the snake_cased class name.
         Port-only classes end up at ``signalwire.<pkg>.<snake_class>``.
    """
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
    )

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
    #     namespace containers expose lazy accessor methods (``aiAgents()`` …)
    #     that mirror Python instance attributes set in ``__init__``. The oracle
    #     records the containers with ONLY ``__init__`` (attributes aren't
    #     methods there) and has no ResourceTree at all. So: drop ResourceTree,
    #     and restrict each container to ``__init__``. The RestClient's own
    #     accessors are inherited from ResourceTree (not re-declared on
    #     RestClient) and are likewise absent from the compared surface — the
    #     6 hand RestClient members that remain are covered by PORT_ADDITIONS.
    if java_package == "com.signalwire.sdk.rest.namespaces.generated":
        # 1. Drop every nested class (keep only the file's top-level type).
        classes = {outer_name: classes.get(outer_name, [])}
        # 2. Drop the ResourceTree plumbing base entirely.
        if outer_name == "ResourceTree":
            return {}
        # 3. Namespace containers: keep only __init__ (match the oracle's
        #    _client_tree_generated classes, which record init only).
        if outer_name in _GENERATED_CONTAINERS:
            meths = classes[outer_name]
            classes[outer_name] = ["__init__"] if "__init__" in meths else []
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
        # Deduplicate overloaded methods; stable ordering.
        unique_sorted = sorted(set(methods))
        mod = java_to_python_module(java_package, cls_name, class_to_module)
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
        for (target_mod, target_cls), expected in _MIXIN_SURFACE_PROJECTIONS.items():
            present = [m for m in expected if m in ab_methods]
            if not present:
                continue
            target = merged.setdefault(target_mod, {"classes": {}, "functions": []})
            existing = target["classes"].get(target_cls, [])
            target["classes"][target_cls] = sorted(set(existing) | set(present))
            ab_methods = [m for m in ab_methods if m not in present]
        if ab_entry:
            if ab_methods:
                ab_entry["classes"]["AgentBase"] = sorted(set(ab_methods))
            else:
                ab_entry.get("classes", {}).pop("AgentBase", None)
                if not ab_entry.get("classes") and not ab_entry.get("functions"):
                    merged.pop("signalwire.core.agent_base", None)

    return merged


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
