#!/usr/bin/env python3
"""Generate the SignalWire REST namespace resource layer for signalwire-java.

This is the JAVA realization of porting-sdk/REST_GENERATOR_RULES.md — the
language-neutral contract of the REST resource generator (bases,
x-sdk-resource markup, path composition, command-dispatch, set_methods,
cross-spec client-tree placement, fail-loud invariants). It mirrors the proven
PHP generator (scripts/generate_rest.py in signalwire-php) and the Go/TS
generators; only the emitter is per-language.

Convention: like php/ts, this is a Python program emitting `.java` files (java's
enumerator scripts/enumerate_signatures.py is already Python; a Python generator
emitting .java is the port's tooling convention). The hand-written BASES stay
hand-written (src/main/java/com/signalwire/sdk/rest/{CrudResource,FabricResource,
FabricResourcePUT,HttpClient,RestClient}.java); the generator emits ONLY the
per-resource classes that EXTEND those bases, their declared/command/set methods,
the namespace containers, and the resource tree the hand RestClient composes.

Classes are named by x-sdk-resource.name VERBATIM (the Python oracle canonical
names — AiAgents, CxmlApplications, SipEndpoints, VideoRooms, Calling, …), so
the java adapter can project each generated class onto the same
signalwire.rest.namespaces.<ns>_resources_generated.<Name> oracle module (L1/L2).

Idiom (PORT_PHILOSOPHY_JAVA.md + changeset L13): the typed create/update/
operation/command inputs are expressed with the BUILDER pattern — a closed
`<Method>Request.builder()....build()` request object + an `extras(Map)` door —
NOT flat positionals. This turn (item A) emits the resource surface to a SCRATCH
dir and proves fidelity; adoption/adapter/commit are later items.

Path composition (§4): java's HttpClient.baseUrl is `https://<space>/api`, so a
REST path is relative to `/api`. The spec's servers[0].url path carries the full
`/api/<ns>` prefix; the generator STRIPS the leading `/api` so the composed base
path matches the port's known-correct hand paths (which pass REST-COVERAGE).

Usage:
    python3 scripts/generate_rest.py --out DIR     # scratch: emit into DIR
    python3 scripts/generate_rest.py               # write into the repo tree
    python3 scripts/generate_rest.py --check       # GEN-FRESH: fail if stale
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover
    sys.stderr.write("generate_rest.py requires PyYAML (pip install pyyaml)\n")
    raise


# ---------------------------------------------------------------------------
# google-java-format pass (§ formatting).
#
# The port's FMT gate runs spotless google-java-format 1.22.0 (build.gradle),
# and its LINT gate runs Checkstyle (OneStatementPerLine etc.). The generated
# .java files are DO-NOT-EDIT and are byte-compared by GEN-FRESH, so the
# generator's raw string output must ALREADY equal what `spotlessApply` would
# produce — otherwise spotless would rewrite the files and GEN-FRESH would then
# report them stale (and Checkstyle would flag the emitter's compact
# one-liners). Rather than reimplement gjf's line-wrapping in the emitters, we
# run each emitted .java string through the SAME google-java-format 1.22.0 jar
# spotless uses (resolved from the gradle cache, like enumerate_signatures.py
# resolves the SDK dep jars). Verified byte-identical to `spotlessApply`.
# ---------------------------------------------------------------------------

# --add-exports needed on JDK 16+ for gjf to reach jdk.compiler internals
# (the same flags spotless passes when it runs gjf out-of-process).
_GJF_ADD_EXPORTS = [
    "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
]
_GJF_VERSION = "1.22.0"  # must match the spotless googleJavaFormat() version in build.gradle


def _resolve_gjf_classpath() -> str:
    cache = Path.home() / ".gradle" / "caches" / "modules-2"
    gjf = sorted(cache.rglob(f"google-java-format-{_GJF_VERSION}.jar"))
    if not gjf:
        raise SystemExit(
            f"generate_rest.py: google-java-format-{_GJF_VERSION}.jar not found under "
            f"{cache}; run ./gradlew build once so spotless resolves it into the cache."
        )
    guavas = [
        p for p in cache.rglob("guava-*.jar")
        if "sources" not in p.name and "android" not in p.name
    ]
    if not guavas:
        raise SystemExit("generate_rest.py: guava jar not found in the gradle cache")
    # Highest guava version (gjf 1.22 needs a recent guava; any -jre works).
    guava = sorted(guavas, key=lambda p: p.name)[-1]
    return ":".join([str(gjf[0]), str(guava)])


_GJF_CP: str | None = None


def gjf_format(source: str) -> str:
    """Return ``source`` formatted by google-java-format 1.22.0 (byte-identical
    to `spotlessApply`)."""
    global _GJF_CP
    if _GJF_CP is None:
        _GJF_CP = _resolve_gjf_classpath()
    proc = subprocess.run(
        ["java", *_GJF_ADD_EXPORTS, "-cp", _GJF_CP,
         "com.google.googlejavaformat.java.Main", "-"],
        input=source, capture_output=True, text=True,
    )
    if proc.returncode != 0:
        raise SystemExit(f"generate_rest.py: google-java-format failed:\n{proc.stderr}")
    return proc.stdout


def gjf_format_many(sources: dict[str, str]) -> dict[str, str]:
    """Format many .java sources in a SINGLE gjf JVM invocation (google-java-format
    ``-i`` over a batch of temp files), byte-identical to per-file ``gjf_format`` /
    ``spotlessApply``. The per-file path spawns one JVM per file — fine for the ~60
    REST resource files, prohibitively slow for the ~1000 generated wire-type files
    (item A/H + D). Keys are opaque logical names; values are raw java sources. The
    returned dict has the SAME keys mapped to the formatted source."""
    global _GJF_CP
    if not sources:
        return {}
    if _GJF_CP is None:
        _GJF_CP = _resolve_gjf_classpath()
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        # A flat temp dir keyed by index avoids any subdir/name clashes.
        idx_to_key: dict[str, str] = {}
        paths: list[str] = []
        for i, key in enumerate(sources):
            p = tmp / f"F{i}.java"
            p.write_text(sources[key])
            idx_to_key[str(p)] = key
            paths.append(str(p))
        # gjf caps its own arg list fine for ~1000 files; if it ever grows past
        # the OS argv limit, chunk here. Today (~1000) it is well under.
        proc = subprocess.run(
            ["java", *_GJF_ADD_EXPORTS, "-cp", _GJF_CP,
             "com.google.googlejavaformat.java.Main", "-i", *paths],
            capture_output=True, text=True,
        )
        if proc.returncode != 0:
            raise SystemExit(
                f"generate_rest.py: batch google-java-format failed:\n{proc.stderr}")
        return {idx_to_key[p]: Path(p).read_text() for p in paths}


# The 12 real REST spec directories (registry has no own dir — its resources
# live inside relay-rest via namespace: registry; swml-webhooks is types-only).
SPEC_DIRS = [
    "relay-rest", "fabric", "calling", "video", "datasphere",
    "logs", "message", "voice", "fax", "project", "chat", "pubsub",
]

# The generated package + the on-disk sub-path under src/main/java.
GEN_PACKAGE = "com.signalwire.sdk.rest.namespaces.generated"
GEN_DIR = "com/signalwire/sdk/rest/namespaces/generated"
# The hand-written bases live in this package.
REST_PACKAGE = "com.signalwire.sdk.rest"

JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new",
    "package", "private", "protected", "public", "return", "short", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while", "true", "false", "null",
    "var", "record", "yield",
}


# ---------------------------------------------------------------------------
# Resolution.
# ---------------------------------------------------------------------------

def resolve_porting_sdk() -> Path:
    env = os.environ.get("PORTING_SDK")
    if env and (Path(env) / "rest-apis").is_dir():
        return Path(env).resolve()
    here = Path(__file__).resolve()
    for parent in here.parents:
        cand = parent.parent / "porting-sdk"
        if (cand / "rest-apis").is_dir():
            return cand.resolve()
    raise SystemExit("generate_rest.py: porting-sdk not found (set $PORTING_SDK or clone adjacent)")


def repo_root() -> Path:
    return Path(__file__).resolve().parents[1]


# ---------------------------------------------------------------------------
# Base loading (x-sdk-bases; §2) — fail-loud on cyclic/undefined extends.
# ---------------------------------------------------------------------------

def load_bases(psdk: Path) -> dict[str, list[str]]:
    raw = yaml.safe_load((psdk / "rest-apis" / "x-sdk-bases.yaml").read_text())
    bases = dict(raw.get("x-sdk-bases") or {})
    fab = psdk / "rest-apis" / "fabric" / "x-sdk-bases.yaml"
    if fab.is_file():
        bases.update((yaml.safe_load(fab.read_text()).get("x-sdk-bases") or {}))

    def resolve(name: str, seen: set[str]) -> list[str]:
        if name in seen:
            raise SystemExit(f"x-sdk-bases: cyclic extends at {name}")
        if name not in bases:
            raise SystemExit(f"x-sdk-bases: undefined base {name!r}")
        seen = seen | {name}
        methods: list[str] = []
        ext = bases[name].get("extends")
        if ext:
            methods.extend(resolve(ext, seen))
        methods.extend(list((bases[name].get("methods") or {}).keys()))
        return methods

    return {name: resolve(name, set()) for name in bases}


# ---------------------------------------------------------------------------
# Spec model.
# ---------------------------------------------------------------------------

class Spec:
    def __init__(self, name: str, doc: dict):
        self.name = name
        self.doc = doc
        self.server_path = _strip_api(_url_path(doc["servers"][0]["url"]))
        raw_path = _url_path(doc["servers"][0]["url"])
        if raw_path != "/" and raw_path.endswith("/"):
            raise SystemExit(f"{name}: servers[0].url path {raw_path!r} has a trailing slash")
        self.namespace_attr = (doc.get("x-sdk-namespace") or {}).get("attr") or ""
        self.ops: dict[str, tuple[str, str, bool]] = {}
        self.op_body: dict[str, dict] = {}
        for path, item in (doc.get("paths") or {}).items():
            for verb in ("get", "post", "put", "patch", "delete"):
                o = item.get(verb)
                if o and o.get("operationId"):
                    self.ops[o["operationId"]] = (verb, path, bool(o.get("requestBody")))
                    body = o.get("requestBody") or {}
                    content = body.get("content") or {}
                    media = content.get("application/json") or (next(iter(content.values())) if content else {})
                    self.op_body[o["operationId"]] = (media or {}).get("schema") or {}
        self.schemas = ((doc.get("components") or {}).get("schemas")) or {}

    def resources(self) -> list[tuple[str, dict]]:
        out = []
        for path, item in (self.doc.get("paths") or {}).items():
            r = item.get("x-sdk-resource")
            if r and not r.get("exclude") and r.get("name"):
                out.append((path, r))
        return out


def _url_path(url: str) -> str:
    if "://" in url:
        url = url.split("://", 1)[1]
    i = url.find("/")
    return url[i:] if i >= 0 else "/"


def _strip_api(path: str) -> str:
    """java's HttpClient baseUrl already ends in `/api`; strip the leading /api
    segment from the spec server path so composed paths match the hand paths."""
    if path == "/api":
        return ""
    if path.startswith("/api/"):
        return path[len("/api"):]
    return path


def load_spec(psdk: Path, ns: str) -> Spec:
    return Spec(ns, yaml.safe_load((psdk / "rest-apis" / ns / "openapi.yaml").read_text()))


# ---------------------------------------------------------------------------
# Path composition (§4).
# ---------------------------------------------------------------------------

def join_path(a: str, b: str) -> str:
    if not b:
        return a
    return a.rstrip("/") + "/" + b.lstrip("/")


def collection_segment(anchor: str, markup: dict) -> str:
    if "collection" in markup:
        return markup["collection"]
    p = anchor
    i = p.find("/{")
    if i >= 0:
        p = p[:i]
    return p


def base_path(spec: Spec, anchor: str, markup: dict) -> str:
    return join_path(spec.server_path, collection_segment(anchor, markup))


def relative_tail(spec: Spec, anchor: str, markup: dict, op_path: str):
    coll = collection_segment(anchor, markup)
    full = join_path(spec.server_path, coll)
    absp = join_path(spec.server_path, op_path)
    if coll and absp.startswith(full + "/"):
        return ([s for s in absp[len(full) + 1:].split("/") if s], False)
    if coll and absp == full:
        return ([], False)
    return ([s for s in absp.lstrip("/").split("/") if s], True)


# ---------------------------------------------------------------------------
# Naming.
# ---------------------------------------------------------------------------

def snake_to_lower_camel(snake: str) -> str:
    parts = [p for p in snake.replace("-", "_").replace(".", "_").split("_") if p]
    if not parts:
        return snake
    return parts[0] + "".join(w[:1].upper() + w[1:] for w in parts[1:])


def snake_to_pascal(snake: str) -> str:
    parts = [p for p in snake.replace("-", "_").replace(".", "_").split("_") if p]
    return "".join(w[:1].upper() + w[1:] for w in parts)


def escape_ident(field: str) -> str:
    ident = snake_to_lower_camel(field)
    return ident + "_" if ident in JAVA_KEYWORDS else ident


PARAM_ARG_NAME = {
    "id": "id", "queue_id": "queueId", "NumberGroupId": "groupId",
    "documentId": "documentId", "chunkId": "chunkId", "mfa_request_id": "requestId",
    "e164_number": "e164", "fabric_subscriber_id": "subscriberId",
    "ai_agent_id": "id", "cxml_webhook_id": "id", "swml_webhook_id": "id",
    "token_id": "tokenId", "room_id": "roomId", "resource_id": "resourceId",
    "sip_endpoint_id": "sipEndpointId",
}


def arg_for(brace: str) -> str:
    return PARAM_ARG_NAME.get(brace, snake_to_lower_camel(brace) or "id")


def java_str(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


# ---------------------------------------------------------------------------
# Base mapping (§2).
# ---------------------------------------------------------------------------

BASE_PROVIDES = {
    "CrudResource": {"list", "create", "get", "update", "delete"},
    "FabricResource": {"list", "create", "get", "update", "delete", "list_addresses"},
    "ReadResource": {"list", "get"},
    "BaseResource": set(),
}

# The java parent class each markup base maps to. CrudResource takes an
# UpdateMethod arg; FabricResource=PATCH parent / FabricResourcePUT=PUT parent.
# ReadResource + BaseResource are realized as light hand-written bases too (see
# report); a BaseResource resource carries only its declared methods + a baked
# base path.
EXTENDS = {
    "CrudResource": "CrudResource",
    "FabricResource": "FabricResource",
    "ReadResource": "ReadResource",
    "BaseResource": "BaseResource",
}


# ---------------------------------------------------------------------------
# Command-dispatch (§6).
# ---------------------------------------------------------------------------

def command_method_name(cmd: str) -> str:
    s = cmd
    if "." in s:
        s = s.split(".", 1)[1] if s.startswith("calling.") else s.replace(".", "_")
    s = s.replace(".", "_")
    return snake_to_lower_camel(s)


def command_py_name(cmd: str) -> str:
    s = cmd[len("calling."):] if cmd.startswith("calling.") else cmd
    return s.replace(".", "_")


def discriminator_mapping(spec: Spec, schema_name: str) -> list[str]:
    sch = spec.schemas.get(schema_name)
    if sch is None:
        raise SystemExit(f"command-dispatch request {schema_name!r} not in components.schemas")
    mapping = (sch.get("discriminator") or {}).get("mapping")
    if not mapping:
        raise SystemExit(f"command-dispatch request {schema_name!r} has no discriminator.mapping")
    return list(mapping.keys())


# ---------------------------------------------------------------------------
# Typed inputs (§5) — schema → Java native type + canonical audit type.
# ---------------------------------------------------------------------------

def resolve_schema(spec: Spec, schema: dict | None, seen=None) -> dict:
    if not schema:
        return {}
    if seen is None:
        seen = set()
    ref = schema.get("$ref")
    if ref:
        leaf = ref.rsplit("/", 1)[-1]
        if leaf in seen:
            return {}
        seen.add(leaf)
        return resolve_schema(spec, spec.schemas.get(leaf), seen)
    allof = schema.get("allOf")
    if allof and len(allof) == 1 and not schema.get("properties") and not schema.get("type"):
        return resolve_schema(spec, allof[0], seen)
    return schema


def _is_named_ref(schema: dict) -> bool:
    if not schema:
        return False
    if schema.get("$ref"):
        return True
    allof = schema.get("allOf")
    if allof and len(allof) == 1 and not schema.get("properties") and not schema.get("type"):
        return _is_named_ref(allof[0])
    return False


def _json_type(schema: dict) -> str | None:
    t = schema.get("type")
    if isinstance(t, list):
        non_null = [x for x in t if x != "null"]
        return non_null[0] if non_null else None
    return t


# Java native scalar (boxed types — nullability + Map<String,Object> uniformity).
_SCALAR_JAVA = {"string": "String", "integer": "Long", "number": "Double", "boolean": "Boolean"}
# Canonical audit type (the oracle records int/float; java has distinct int/long
# /float/double so it is NOT numeric-monotype — integer→int, number→float).
_SCALAR_CANON = {"string": "string", "integer": "int", "number": "float", "boolean": "bool"}


def java_field_type(spec: Spec, schema: dict) -> str:
    """The Java builder-field type for a body field (boxed / collections)."""
    resolved = resolve_schema(spec, schema)
    jt = _json_type(resolved)
    if jt in _SCALAR_JAVA:
        return _SCALAR_JAVA[jt]
    if jt == "array":
        return "java.util.List<Object>"
    # object / $ref-to-object / oneOf / anyOf / unknown → open map.
    return "java.util.Map<String, Object>"


def canonical_type(spec: Spec, schema: dict, required: bool) -> str:
    """Canonical audit type recorded in the sidecar for a body field."""
    if not required:
        return "optional<any>"
    if _is_named_ref(schema):
        return "dict<string,any>"
    resolved = resolve_schema(spec, schema)
    jt = _json_type(resolved)
    if jt in _SCALAR_CANON:
        return _SCALAR_CANON[jt]
    if jt == "array":
        return "list<any>"
    return "dict<string,any>"


def object_body_fields(spec: Spec, body_schema: dict) -> list[tuple[str, dict, bool]]:
    resolved = resolve_schema(spec, body_schema)
    props: dict[str, dict] = {}
    required: set[str] = set(resolved.get("required") or [])
    for name, psc in (resolved.get("properties") or {}).items():
        props.setdefault(name, psc)
    for br in resolved.get("allOf") or []:
        rb = resolve_schema(spec, br)
        required |= set(rb.get("required") or [])
        for name, psc in (rb.get("properties") or {}).items():
            props.setdefault(name, psc)
    return [(name, psc, name in required) for name, psc in props.items()]


def command_param_fields(spec: Spec, command_schema: dict) -> tuple[list[tuple[str, dict, bool]], bool]:
    """§6 union-flatten: union of all variants' fields; required iff EVERY variant
    requires it. has_id = command schema declares an ``id`` property."""
    cs = resolve_schema(spec, command_schema)
    has_id = "id" in (cs.get("properties") or {})
    params_schema = (cs.get("properties") or {}).get("params")
    if params_schema is None:
        return [], has_id
    ps = resolve_schema(spec, params_schema)
    variants: list[dict] = []
    for comb in ("anyOf", "oneOf"):
        if comb in ps:
            variants = [resolve_schema(spec, v) for v in ps[comb]]
            break
    if not variants:
        variants = [ps]
    all_props: dict[str, dict] = {}
    req_sets: list[set[str]] = []
    for v in variants:
        req_sets.append(set(v.get("required") or []))
        for name, psc in (v.get("properties") or {}).items():
            all_props.setdefault(name, psc)
    req_all = set.intersection(*req_sets) if req_sets else set()
    return [(name, psc, name in req_all) for name, psc in all_props.items()], has_id


def is_object_body(spec: Spec, body_schema: dict) -> bool:
    if not body_schema:
        return False
    if "anyOf" in body_schema or "oneOf" in body_schema:
        return False
    resolved = resolve_schema(spec, body_schema)
    if "anyOf" in resolved or "oneOf" in resolved:
        return False
    if resolved.get("properties") or resolved.get("allOf"):
        return True
    return _json_type(resolved) == "object"


def ordered_fields(fields):
    req = [f for f in fields if f[2]]
    opt = [f for f in fields if not f[2]]
    return req + opt


# Sidecar accumulator: (ClassName, javaMethod) -> [param records].
_SIDECAR: dict[tuple[str, str], list[dict]] = {}


def _register_sidecar(cls: str, java_method: str, records: list[dict]) -> None:
    _SIDECAR[(cls, java_method)] = records


# ---------------------------------------------------------------------------
# Builder emission (§5 / L13) — a closed <Method>Request builder + extras door.
# ---------------------------------------------------------------------------

def emit_request_builder(cls: str, method: str,
                         fields: list[tuple[str, str, bool, str]]) -> str:
    """Emit a nested static <Method>Request builder class.

    fields: list of (wireName, javaFieldType, required, ident). Produces:
      - a private field per param + the extras Map,
      - a fluent builder setter per param + extras(Map),
      - build() → the immutable request,
      - toBody() → the Map<String,Object> wire body (non-null fields + extras).
    """
    req_cls = snake_to_pascal(method) + "Request"
    lines = []
    lines.append(f"  /** Closed typed request for {{@link #{snake_to_lower_camel(method)}}} (builder + extras door). */")
    lines.append(f"  public static final class {req_cls} {{")
    for wire, jtype, required, ident in fields:
        lines.append(f"    private final {jtype} {ident};")
    lines.append("    private final java.util.Map<String, Object> extras;")
    lines.append("")
    # private ctor from builder
    ctor_args = ", ".join(f"{jtype} {ident}" for wire, jtype, required, ident in fields)
    sep = ", " if fields else ""
    lines.append(f"    private {req_cls}({ctor_args}{sep}java.util.Map<String, Object> extras) {{")
    for wire, jtype, required, ident in fields:
        lines.append(f"      this.{ident} = {ident};")
    lines.append("      this.extras = extras;")
    lines.append("    }")
    lines.append("")
    lines.append(f"    public static Builder builder() {{ return new Builder(); }}")
    lines.append("")
    # toBody
    lines.append("    java.util.Map<String, Object> toBody() {")
    lines.append("      java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();")
    for wire, jtype, required, ident in fields:
        lines.append(f"      if (this.{ident} != null) {{ body.put({java_str(wire)}, this.{ident}); }}")
    lines.append("      if (this.extras != null) { body.putAll(this.extras); }")
    lines.append("      return body;")
    lines.append("    }")
    lines.append("")
    # Builder
    lines.append(f"    public static final class Builder {{")
    for wire, jtype, required, ident in fields:
        lines.append(f"      private {jtype} {ident};")
    lines.append("      private java.util.Map<String, Object> extras;")
    lines.append("")
    for wire, jtype, required, ident in fields:
        lines.append(f"      public Builder {ident}({jtype} {ident}) {{")
        lines.append(f"        this.{ident} = {ident};")
        lines.append("        return this;")
        lines.append("      }")
    lines.append("      public Builder extras(java.util.Map<String, Object> extras) {")
    lines.append("        this.extras = extras;")
    lines.append("        return this;")
    lines.append("      }")
    lines.append("")
    call = ", ".join(ident for wire, jtype, required, ident in fields)
    csep = ", " if fields else ""
    lines.append(f"      public {req_cls} build() {{ return new {req_cls}({call}{csep}extras); }}")
    lines.append("    }")
    lines.append("  }")
    return "\n".join(lines)


def build_field_tuples(spec, fields):
    """(wireName, javaFieldType, required, ident) required-first."""
    out = []
    for wire, schema, required in ordered_fields(fields):
        out.append((wire, java_field_type(spec, schema), required, escape_ident(wire)))
    return out


def sidecar_records_for_fields(spec, fields, leading, door_name="extras"):
    records: list[dict] = list(leading)
    for wire, schema, required in ordered_fields(fields):
        records.append({"name": wire, "kind": "keyword",
                        "type": canonical_type(spec, schema, required),
                        "required": required, **({"default": None} if not required else {})})
    records.append({"name": door_name, "kind": "keyword",
                    "type": "optional<dict<string,any>>", "required": False, "default": None})
    return records


# ---------------------------------------------------------------------------
# Method emitters.
# ---------------------------------------------------------------------------

def method_call_path(spec: Spec, anchor: str, markup: dict, op_path: str):
    """Return (id_args, java_path_expr) — a Java string expression for the path."""
    segs, sibling = relative_tail(spec, anchor, markup, op_path)
    id_args: list[str] = []
    pieces: list[str] = []
    for s in segs:
        if s.startswith("{") and s.endswith("}"):
            arg = arg_for(s[1:-1])
            while arg in id_args:
                arg += "2"
            id_args.append(arg)
            pieces.append(arg)
        else:
            pieces.append(java_str(s))
    if sibling:
        full = join_path(spec.server_path, op_path.lstrip("/"))
        expr = abs_java_path(full, id_args)
    elif not pieces:
        expr = "getBasePath()"
    else:
        # compose getBasePath() + "/" + seg + ...
        parts = ["getBasePath()"]
        for p in pieces:
            parts.append('"/"')
            parts.append(p)
        expr = " + ".join(parts)
    return id_args, expr


def abs_java_path(full: str, id_args: list[str]) -> str:
    out = []
    literal = []
    ai = 0
    i = 0
    while i < len(full):
        if full[i] == "{":
            j = full.find("}", i)
            if literal:
                out.append(java_str("".join(literal)))
                literal = []
            if ai < len(id_args):
                out.append(id_args[ai])
                ai += 1
            i = j + 1
            continue
        literal.append(full[i])
        i += 1
    if literal:
        out.append(java_str("".join(literal)))
    return " + ".join(out) if out else '""'


def emit_method(spec: Spec, anchor: str, markup: dict, base: str,
                method_snake: str, op_id: str, is_override: bool = False) -> tuple[str, str]:
    """Return (method_java, builder_java_or_empty)."""
    if op_id not in spec.ops:
        raise SystemExit(f"{markup['name']}.{method_snake}: op {op_id!r} not in spec")
    verb, op_path, has_body = spec.ops[op_id]
    id_args, path_expr = method_call_path(spec, anchor, markup, op_path)
    name = snake_to_lower_camel(method_snake)
    cls = markup["name"]

    id_records = [{"name": a, "kind": "positional", "type": "string", "required": True}
                  for a in id_args]
    id_params = ["String " + a for a in id_args]
    write_verb = verb in ("post", "put", "patch")
    builder_src = ""
    # Low-level verb RECEIVERS are named with a ``rest`` prefix so a generated
    # method that shares the semantic verb's name (a BaseResource resource's own
    # ``get(id, params)`` / ``delete(id)``, a CRUD subclass's inherited public
    # ``delete(id)``) does NOT recurse into itself or shadow the raw receiver.
    # The hand bases (BaseResource/…) expose exactly these protected receivers.
    verb_fn = {"post": "restPost", "put": "restPut", "patch": "restPatch"}.get(verb, "")

    if write_verb and has_body:
        body_schema = spec.op_body.get(op_id) or {}
        if is_object_body(spec, body_schema):
            fields = object_body_fields(spec, body_schema)
            ftuples = build_field_tuples(spec, fields)
            req_cls = snake_to_pascal(method_snake) + "Request"
            builder_src = emit_request_builder(cls, method_snake, ftuples)
            _register_sidecar(cls, name, sidecar_records_for_fields(spec, fields, id_records))
            params = id_params + [f"{req_cls} request"]
            call = f"    return {verb_fn}({path_expr}, request.toBody());"
        else:
            # §5.2 union body → a single Map body param.
            params = id_params + ["java.util.Map<String, Object> body"]
            _register_sidecar(cls, name, id_records + [
                {"name": "body", "kind": "positional", "type": "dict<string,any>", "required": True}])
            call = f"    return {verb_fn}({path_expr}, body);"
    elif write_verb:
        params = id_params
        _register_sidecar(cls, name, list(id_records))
        call = f"    return {verb_fn}({path_expr}, new java.util.LinkedHashMap<>());"
    elif verb == "get":
        # §5.3 GET query door → a trailing query-params map (var_keyword).
        params = id_params + ["java.util.Map<String, String> params"]
        _register_sidecar(cls, name, id_records + [
            {"name": "params", "kind": "var_keyword", "type": "any", "required": False, "default": {}}])
        call = f"    return restGet({path_expr}, params);"
    else:  # delete
        params = id_params
        _register_sidecar(cls, name, list(id_records))
        call = f"    return restDelete({path_expr});"

    sig = ", ".join(params)
    override = "  @Override\n" if is_override else ""
    method = (f"  /** {name} (generated from operation {op_id!r}). */\n"
              f"{override}  public java.util.Map<String, Object> {name}({sig}) {{\n{call}\n  }}")
    return method, builder_src


def emit_set_method(spec: Spec, markup: dict, sm_name: str, sm: dict,
                    update_schema_fields: set[str], field_schemas: dict[str, dict]) -> tuple[str, str]:
    handler = sm.get("handler")
    if not handler:
        raise SystemExit(f"{markup['name']}.{sm_name}: set_method missing handler")
    cls = markup["name"]
    name = snake_to_lower_camel(sm_name)
    args = sm.get("args") or {}
    params = ["String resourceId"]
    records: list[dict] = [{"name": "resource_id", "kind": "positional", "type": "string", "required": True}]
    build = ["    java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();",
             f"    body.put(\"call_handler\", {java_str(handler)});"]
    for arg_name, arg in args.items():
        field = arg.get("field")
        if not field:
            raise SystemExit(f"{markup['name']}.{sm_name}: arg {arg_name!r} missing field")
        if field not in update_schema_fields:
            raise SystemExit(
                f"{markup['name']}.{sm_name}: arg field {field!r} not in update request schema")
        ident = escape_ident(arg_name)
        required = bool(arg.get("required"))
        jtype = java_field_type(spec, field_schemas.get(field, {}))
        params.append(f"{jtype} {ident}")
        rec: dict = {"name": arg_name, "kind": "positional",
                     "type": canonical_type(spec, field_schemas.get(field, {}), required),
                     "required": required}
        if not required:
            rec["default"] = None
            build.append(f"    if ({ident} != null) {{ body.put({java_str(field)}, {ident}); }}")
        else:
            build.append(f"    body.put({java_str(field)}, {ident});")
        records.append(rec)
    params.append("java.util.Map<String, Object> extra")
    records.append({"name": "extra", "kind": "var_keyword", "type": "any", "required": False, "default": {}})
    _register_sidecar(cls, name, records)
    build.append("    if (extra != null) { body.putAll(extra); }")
    build.append("    return update(resourceId, body);")
    sig = ", ".join(params)
    method = (f"  /** {name} — sets call_handler={handler!r} + bound update fields (§7). */\n"
              f"  public java.util.Map<String, Object> {name}({sig}) {{\n"
              + "\n".join(build) + "\n  }")
    return method, ""


def schema_fields(spec: Spec, schema: dict, seen=None) -> set[str]:
    if schema is None:
        return set()
    if seen is None:
        seen = set()
    ref = schema.get("$ref")
    if ref:
        leaf = ref.rsplit("/", 1)[-1]
        if leaf in seen:
            return set()
        seen.add(leaf)
        return schema_fields(spec, spec.schemas.get(leaf), seen)
    out = set(((schema.get("properties")) or {}).keys())
    for comb in ("allOf", "anyOf", "oneOf"):
        for br in schema.get(comb) or []:
            out |= schema_fields(spec, br, seen)
    return out


def _find_update_op(spec: Spec, anchor: str, markup: dict):
    coll = collection_segment(anchor, markup)
    want_verb = "put" if markup.get("update_method") == "PUT" else "patch"
    for path, item in (spec.doc.get("paths") or {}).items():
        if not path.startswith(coll + "/{"):
            continue
        if path.count("/{") != 1 or not path.endswith("}"):
            continue
        op = item.get(want_verb) or item.get("put") or item.get("patch")
        if not op:
            continue
        content = (op.get("requestBody") or {}).get("content") or {}
        for media in content.values():
            sch = media.get("schema")
            if sch:
                return sch
    return None


def update_request_fields(spec: Spec, anchor: str, markup: dict) -> set[str]:
    sch = _find_update_op(spec, anchor, markup)
    return schema_fields(spec, sch) if sch else set()


def update_field_schemas(spec: Spec, anchor: str, markup: dict) -> dict[str, dict]:
    sch = _find_update_op(spec, anchor, markup)
    if not sch:
        return {}
    return {name: psc for name, psc, _ in object_body_fields(spec, sch)}


# ---------------------------------------------------------------------------
# File header.
# ---------------------------------------------------------------------------

def gen_header(desc: str) -> str:
    return (
        "// Code generated by scripts/generate_rest.py; DO NOT EDIT.\n"
        "//\n"
        "// AUTO-GENERATED from porting-sdk/rest-apis/ (x-sdk-* markup) — regenerate with:\n"
        "//   python3 scripts/generate_rest.py\n"
        "//\n"
        f"// {desc}\n"
        "\n"
        f"package {GEN_PACKAGE};\n\n"
        f"import {REST_PACKAGE}.HttpClient;\n\n"
    )


# ---------------------------------------------------------------------------
# Command-dispatch resource emitter (§6).
# ---------------------------------------------------------------------------

def emit_command_dispatch(spec: Spec, anchor: str, markup: dict) -> str:
    name = markup["name"]
    request = markup.get("request")
    if not request:
        raise SystemExit(f"{name}: command-dispatch requires request")
    commands = discriminator_mapping(spec, request)
    op = spec.ops.get("call-commands")
    if op:
        base = join_path(spec.server_path, op[1].lstrip("/"))
    else:
        base = join_path(spec.server_path, anchor.lstrip("/"))

    lines = [gen_header(f"Generated command-dispatch resource for the {spec.name!r} namespace.")]
    lines.append(f"/**\n * {name} — command-dispatch resource ({spec.name} spec). Each method POSTs\n"
                 f" * {{command, params, id?}} to {base}.\n */")
    lines.append(f"public final class {name} {{")
    lines.append("  private final HttpClient httpClient;")
    lines.append(f"  private static final String BASE_PATH = {java_str(base)};")
    lines.append("")
    lines.append(f"  public {name}(HttpClient httpClient) {{ this.httpClient = httpClient; }}")
    lines.append("")
    lines.append("  public String getBasePath() { return BASE_PATH; }")
    lines.append("")
    lines.append("  private java.util.Map<String, Object> execute("
                 "String command, String callId, java.util.Map<String, Object> params) {")
    lines.append("    java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();")
    lines.append("    body.put(\"command\", command);")
    lines.append("    body.put(\"params\", params);")
    lines.append("    if (callId != null) { body.put(\"id\", callId); }")
    lines.append("    return httpClient.post(BASE_PATH, body);")
    lines.append("  }")

    mapping = (spec.schemas.get(request).get("discriminator") or {}).get("mapping") or {}
    builders: list[str] = []
    for cmd in commands:
        mname = command_method_name(cmd)
        cmd_leaf = (mapping.get(cmd) or "").rsplit("/", 1)[-1]
        cmd_schema = spec.schemas.get(cmd_leaf, {})
        fields, with_id = command_param_fields(spec, cmd_schema)
        ftuples = build_field_tuples(spec, fields)
        req_cls = snake_to_pascal(command_py_name(cmd)) + "Request"
        builders.append(emit_request_builder(name, command_py_name(cmd), ftuples))

        records: list[dict] = []
        if with_id:
            records.append({"name": "call_id", "kind": "positional", "type": "string", "required": True})
        records = sidecar_records_for_fields(spec, fields, records)
        _register_sidecar(name, mname, records)

        id_param = ["String callId"] if with_id else []
        params = id_param + [f"{req_cls} request"]
        call_arg = "callId" if with_id else "null"
        lines.append("")
        lines.append(f"  /** {mname} — command {cmd!r}. */")
        lines.append(f"  public java.util.Map<String, Object> {mname}({', '.join(params)}) {{")
        lines.append(f"    return execute({java_str(cmd)}, {call_arg}, request.toBody());")
        lines.append("  }")

    for b in builders:
        lines.append("")
        lines.append(b)
    lines.append("}")
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------------------
# Standard resource emitter.
# ---------------------------------------------------------------------------

def emit_resource(spec: Spec, anchor: str, markup: dict) -> str:
    name = markup["name"]
    base = markup["base"]
    if markup.get("kind") == "command-dispatch":
        return emit_command_dispatch(spec, anchor, markup)
    if base not in EXTENDS:
        raise SystemExit(f"{name}: unknown base {base!r}")

    if base in ("CrudResource", "FabricResource"):
        upd = markup.get("update_method")
        if not upd:
            raise SystemExit(f"{name}: {base} requires update_method")
        # §9: declared update_method must match the spec's ACTUAL update op verb.
        # The update op is item-level (<collection>/{id}), not the anchor collection
        # (which carries only list/create) — locate it and compare. (Checking the
        # anchor item, as some earlier port generators do, silently never fires
        # because no CRUD anchor carries put/patch directly; L14 "give the check
        # teeth".) Fall back to the anchor verb if the item-level op is absent.
        spec_verb = None
        for path, item in (spec.doc.get("paths") or {}).items():
            coll = collection_segment(anchor, markup)
            if path.startswith(coll + "/{") and path.count("/{") == 1 and path.endswith("}"):
                if item.get("put"):
                    spec_verb = "PUT"
                elif item.get("patch"):
                    spec_verb = "PATCH"
                if spec_verb:
                    break
        if spec_verb is None:
            item = spec.doc["paths"][anchor]
            spec_verb = "PUT" if item.get("put") else ("PATCH" if item.get("patch") else None)
        if spec_verb and upd != spec_verb:
            raise SystemExit(f"{name}: update_method {upd} != spec update verb {spec_verb}")

    extends = EXTENDS[base]
    if base == "FabricResource":
        extends = "FabricResourcePUT" if markup.get("update_method") == "PUT" else "FabricResource"
    bp = base_path(spec, anchor, markup)

    header = gen_header(f"Generated REST resource for the {spec.name!r} namespace.")
    lines = [header]
    lines.append(f"import {REST_PACKAGE}.{extends};")
    lines.append("")
    lines.append(f"/**\n * {name} — generated from x-sdk-resource {name!r} ({spec.name} spec, base {base}).\n */")
    lines.append(f"public class {name} extends {extends} {{")

    # Constructor bakes the base path (§4).
    if base == "CrudResource":
        upd = markup.get("update_method", "PATCH")
        verb_enum = "UpdateMethod.PATCH" if upd == "PATCH" else "UpdateMethod.PUT"
        lines.append(f"  public {name}(HttpClient httpClient) {{")
        lines.append(f"    super(httpClient, {java_str(bp)}, {verb_enum});")
        lines.append("  }")
    else:
        lines.append(f"  public {name}(HttpClient httpClient) {{")
        lines.append(f"    super(httpClient, {java_str(bp)});")
        lines.append("  }")

    provided = BASE_PROVIDES[base]
    declared = markup.get("methods") or {}
    builders: list[str] = []

    for method_snake, spec_ref in declared.items():
        op_id = spec_ref.get("op")
        if not op_id:
            raise SystemExit(f"{name}.{method_snake}: method markup missing op")
        is_override = False
        if method_snake in provided:
            if method_snake == "list_addresses":
                verb, op_path, _ = spec.ops[op_id]
                _, sibling = relative_tail(spec, anchor, markup, op_path)
                if not sibling:
                    continue
                # A declared method that the base ALSO provides (a fabric
                # resource with a non-standard address sub-path overriding the
                # base ``listAddresses``) must carry ``@Override`` — errorprone
                # MissingOverride is an ERROR in the port's build.
                is_override = True
            else:
                continue
        method_src, builder_src = emit_method(spec, anchor, markup, base, method_snake, op_id,
                                              is_override=is_override)
        lines.append("")
        lines.append(method_src)
        if builder_src:
            builders.append(builder_src)

    set_methods = markup.get("set_methods") or {}
    if set_methods:
        if base not in ("CrudResource", "FabricResource"):
            raise SystemExit(f"{name}: set_methods require a CRUD base, got {base}")
        upd_fields = update_request_fields(spec, anchor, markup)
        upd_field_schemas = update_field_schemas(spec, anchor, markup)
        for sm_name, sm in set_methods.items():
            method_src, _ = emit_set_method(spec, markup, sm_name, sm, upd_fields, upd_field_schemas)
            lines.append("")
            lines.append(method_src)

    for b in builders:
        lines.append("")
        lines.append(b)
    lines.append("}")
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------------------
# Client tree (§8).
# ---------------------------------------------------------------------------

CONTAINERS = {
    "fabric": ("FabricNamespace", "fabric"),
    "video": ("VideoNamespace", "video"),
    "logs": ("LogsNamespace", "logs"),
    "registry": ("RegistryNamespace", "registry"),
    "project": ("ProjectNamespace", "project"),
    "datasphere": ("DatasphereNamespace", "datasphere"),
}

ATTR_OVERRIDE = {
    "GenericResources": "resources", "FabricAddresses": "addresses",
    "FabricTokens": "tokens", "DatasphereDocuments": "documents",
    "ProjectTokens": "tokens", "PubSub": "pubsub",
    "MessageLogs": "messages", "VoiceLogs": "voice", "FaxLogs": "fax",
    "ConferenceLogs": "conferences",
}


def container_accessor(markup: dict, name: str, container: str) -> str:
    if markup.get("attr"):
        return snake_to_lower_camel(markup["attr"])
    if name in ATTR_OVERRIDE:
        return snake_to_lower_camel(ATTR_OVERRIDE[name])
    lead = container[:1].upper() + container[1:]
    stem = name[len(lead):] if name.startswith(lead) else name
    return stem[:1].lower() + stem[1:] if stem else name[:1].lower() + name[1:]


def flat_accessor(name: str) -> str:
    if name in ATTR_OVERRIDE:
        return snake_to_lower_camel(ATTR_OVERRIDE[name])
    return name[:1].lower() + name[1:]


def resolve_placement(specs: list[Spec]):
    placed = []
    for spec in specs:
        for anchor, markup in spec.resources():
            container = markup.get("namespace") or spec.namespace_attr or ""
            placed.append((spec, anchor, markup, container))
    return placed


def emit_container(container: str, members: list[tuple[str, str]]) -> str:
    cls, _ = CONTAINERS[container]
    lines = [gen_header(f"Generated REST client container for the {container} namespace (§8).")]
    lines.append(f"/**\n * {cls} — generated container grouping the {container} namespace resources (§8).\n */")
    lines.append(f"public final class {cls} {{")
    lines.append("  private final HttpClient httpClient;")
    for accessor, class_name in members:
        lines.append(f"  private {class_name} {accessor};")
    lines.append("")
    lines.append(f"  public {cls}(HttpClient httpClient) {{ this.httpClient = httpClient; }}")
    for accessor, class_name in members:
        lines.append("")
        lines.append(f"  public {class_name} {accessor}() {{")
        lines.append(f"    if ({accessor} == null) {{ {accessor} = new {class_name}(httpClient); }}")
        lines.append(f"    return {accessor};")
        lines.append("  }")
    lines.append("}")
    return "\n".join(lines) + "\n"


def emit_resource_tree(placed) -> str:
    """A generated abstract class the hand RestClient extends: lazy accessor per
    FLAT resource + per container. (§8)."""
    flats = []
    containers_seen = []
    seen_c = set()
    for spec, anchor, markup, container in placed:
        name = markup["name"]
        if not container:
            flats.append((flat_accessor(name), name))
        else:
            if container not in seen_c:
                seen_c.add(container)
                containers_seen.append(container)

    lines = [gen_header("Generated REST resource tree the hand RestClient composes (§8).")]
    lines.append("/**\n * ResourceTree — generated lazy accessors for every flat REST resource plus\n"
                 " * the namespace containers (§8). The hand RestClient extends this and supplies\n"
                 " * the HttpClient via generatedHttpClient().\n */")
    lines.append("public abstract class ResourceTree {")
    for accessor, cls in flats:
        lines.append(f"  private {cls} {accessor};")
    for c in containers_seen:
        clsname, acc = CONTAINERS[c]
        lines.append(f"  private {clsname} {acc};")
    lines.append("")
    lines.append("  protected abstract HttpClient generatedHttpClient();")
    for accessor, cls in flats:
        lines.append("")
        lines.append(f"  public {cls} {accessor}() {{")
        lines.append(f"    if ({accessor} == null) {{ {accessor} = new {cls}(generatedHttpClient()); }}")
        lines.append(f"    return {accessor};")
        lines.append("  }")
    for c in containers_seen:
        clsname, acc = CONTAINERS[c]
        lines.append("")
        lines.append(f"  public {clsname} {acc}() {{")
        lines.append(f"    if ({acc} == null) {{ {acc} = new {clsname}(generatedHttpClient()); }}")
        lines.append(f"    return {acc};")
        lines.append("  }")
    lines.append("}")
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------------------
# Wire-type emitter (item A/H + D — REAL types, not Map<String,Object>).
#
# For each schema source (REST components/schemas, SWML $defs, RELAY params/result,
# SWAIG specs) emit one method-less Java data class per OBJECT schema (public typed
# nullable fields carrying the snake wire key, NO methods — the Python reference
# records these as method-less type definitions, so the enumerator surfaces the bare
# class name with no methods; the signature enumerator projects each public field as
# a zero-arg accessor member). A public x-sdk-enum (only PhoneCallHandler in
# relay-rest) becomes a Java enum. Every OTHER schema kind — a scalar/array/union
# alias, a plain inline enum — is NOT surfaced by the Python reference (its enumerator
# drops module-level scalar TypeAlias / inline Literal), so nothing is emitted for it
# (matching the reference surface exactly — verified per module, emit-set == oracle-set).
#
# These emit helpers are SHARED, imported by generate_swml_verbs.py /
# generate_relay_protocol.py / generate_swaig_payloads.py by path, so the four
# generators never diverge on the object-vs-alias split or the field rendering. The
# per-generator driver decides the package + on-disk subdir; the emit rule is here.
# ---------------------------------------------------------------------------

# Hard-reserved Java keywords forbidden as a CLASS/ENUM name. A schema whose
# sanitised leaf collides gets a ``_`` suffix in the Java class + filename; the
# surface/signature enumerators rename it back to the bare oracle leaf (the type-name
# analog of the reserved-word FIELD rename Python does with ``from`` -> ``from_``).
# Goto/Return/Switch/Unset are also SWML-verb schema names. Kept in sync with
# JAVA_KEYWORDS above (that set also covers primitives / literals a schema name can
# never legitimately be).
JAVA_TYPE_RESERVED = JAVA_KEYWORDS

# ``java.lang.*`` types auto-imported into every compilation unit: a generated class
# spelled identically (``Record`` — java.lang.Record since JDK 14) triggers Error
# Prone's JavaLangClash and shadows the built-in. Suffix these with ``_`` (the
# type-name analog of the TS ``BUILTIN_COLLISION_RENAME`` — ``Record_``/``Set_``);
# the surface/signature enumerators rename them back to the bare oracle leaf. Only
# java.lang types (always in scope) need this; java.util (List/Map/Set) is
# fully-qualified at every use so those names don't clash.
JAVA_BUILTIN_COLLISION = {
    "Record", "String", "Integer", "Long", "Double", "Boolean", "Object", "Void",
    "Number", "Character", "Byte", "Short", "Float", "System", "Thread", "Runnable",
    "Comparable", "Cloneable", "Iterable", "Error", "Exception", "Class", "Enum",
    "Math", "Process", "Runtime", "Package", "Module", "Thread", "Override",
    "Deprecated", "SuppressWarnings", "FunctionalInterface", "SafeVarargs",
}


def type_name(raw: str) -> str:
    """Sanitise a schema key to a valid Java class identifier, folding every
    non-identifier rune to ``_`` — matching the go/TS/php/python ref_name so the LEAF
    the surface diff compares is the identical token across ports
    (``Types.StatusCodes.StatusCode400`` -> ``Types_StatusCodes_StatusCode400``). A
    leaf colliding with a hard-reserved Java keyword OR a java.lang built-in type gets
    a ``_`` suffix (the enumerator renames it back to the bare oracle leaf)."""
    s = re.sub(r"[^A-Za-z0-9_]", "_", raw).lstrip("_")
    if not s:
        return "Schema"
    if s[0].isdigit():
        return "Schema_" + s
    if s in JAVA_TYPE_RESERVED or s in JAVA_BUILTIN_COLLISION:
        return s + "_"
    return s


def _schema_type(node: dict) -> str | None:
    t = node.get("type")
    if isinstance(t, list):
        return next((x for x in t if x != "null"), None)
    return t


def is_object_schema(node: dict) -> bool:
    """Mirror the reference is_object test (and php's is_object_schema): type:object
    (or no type but non-empty properties) AND not a oneOf/anyOf/allOf combinator AND
    properties non-empty. An empty-object schema (no properties) is an alias the
    reference drops, so it is NOT an object here."""
    if not isinstance(node, dict):
        return False
    if any(k in node for k in ("oneOf", "anyOf", "allOf")):
        return False
    props = node.get("properties")
    t = _schema_type(node)
    return (t == "object" or (t is None and props)) and isinstance(props, dict) and len(props) > 0


def _resolve_type_ref(schema: dict, schemas: dict, seen: set | None = None) -> dict:
    """Follow an in-spec ``$ref`` and a single-member ``allOf`` (the '$ref +
    description' idiom) to the concrete schema so a $ref-to-scalar newtype
    (uuid/jwt) / $ref-to-enum recovers its underlying scalar. An external ``.json``
    $ref or an unresolvable ref returns ``{}`` (→ structured/open)."""
    if not isinstance(schema, dict):
        return {}
    if seen is None:
        seen = set()
    ref = schema.get("$ref")
    if ref:
        if not ref.startswith("#/"):
            return {}
        leaf = ref.rsplit("/", 1)[-1]
        if leaf in seen:
            return {}
        seen.add(leaf)
        return _resolve_type_ref(schemas.get(leaf) or {}, schemas, seen)
    allof = schema.get("allOf")
    if allof and len(allof) == 1 and not schema.get("properties") and not schema.get("type"):
        return _resolve_type_ref(allof[0], schemas, seen)
    return schema


def type_field_type(schema: dict, schemas: dict | None = None) -> str:
    """The Java field type for a wire-type property (boxed scalars / collections).

    Every field is nullable and defaults to null, so the class is a pure data holder
    needing no constructor. The type NEVER references another generated class (avoids
    file-ordering / cross-package collision concerns and keeps the emitter
    deterministic) — the surface records only the class name, so field types are pure
    idiom:
        scalar (incl. $ref-to-scalar-newtype / $ref-to-enum) -> String/Long/Double/Boolean
        array   -> java.util.List<Object>
        object / $ref-to-object / union / unknown -> java.util.Map<String, Object>
    """
    schemas = schemas or {}
    if schema.get("$ref") or (
        schema.get("allOf") and len(schema["allOf"]) == 1
        and not schema.get("properties") and not schema.get("type")
    ):
        schema = _resolve_type_ref(schema, schemas)
    if schema.get("allOf") or schema.get("oneOf") or schema.get("anyOf") or schema.get("$ref"):
        return "java.util.Map<String, Object>"
    t = _schema_type(schema)
    if t == "string":
        return "String"
    if t == "integer":
        return "Long"
    if t == "number":
        return "Double"
    if t == "boolean":
        return "Boolean"
    if t == "array":
        return "java.util.List<Object>"
    if t == "object":
        return "java.util.Map<String, Object>"
    # No/unknown type → any JSON value.
    return "Object"


def type_field_name(wire_key: str) -> str:
    """Java field name for a wire key, carrying the wire key VERBATIM where legal
    (the reference/enumerator records the exact wire field name — SWAIG/allOf/oneOf —
    NOT snake-folded). Fold anything non-identifier to ``_`` (rare); a leading digit
    gets a ``_`` prefix; a hard Java keyword gets a ``_`` suffix (the field-name
    analog of the reserved-word rename — the enumerator strips it back)."""
    s = re.sub(r"[^A-Za-z0-9_]", "_", wire_key)
    if not s:
        s = "field"
    if s[0].isdigit():
        s = "_" + s
    if s in JAVA_KEYWORDS:
        s = s + "_"
    return s


def _enum_case_name(value: str) -> str:
    """UPPER_SNAKE case name for a backed-enum wire value (Java enum-constant idiom)."""
    parts = [p for p in re.split(r"[^A-Za-z0-9]+", value) if p]
    name = "_".join(p.upper() for p in parts)
    if not name:
        name = "VALUE"
    if name[0].isdigit():
        name = "V_" + name
    if name in JAVA_KEYWORDS:
        name = name + "_"
    return name


def type_gen_header(package: str, desc: str) -> str:
    return (
        "// Code generated by scripts/generate_rest.py; DO NOT EDIT.\n"
        "//\n"
        "// AUTO-GENERATED from porting-sdk/ (schemas) — regenerate with the generator.\n"
        "//\n"
        f"// {desc}\n"
        "\n"
        f"package {package};\n\n"
    )


def emit_type_class(package: str, raw_name: str, node: dict, source_desc: str,
                    schemas: dict | None = None, class_name: str | None = None) -> str:
    """Emit one method-less Java data class for an object schema. Returns RAW java
    (not gjf-formatted — the caller batch-formats). ``class_name`` overrides the
    derived name when a generator computes the class name itself (relay-protocol
    derives it from x-method + phase, not from a components/schemas key)."""
    java_name = class_name if class_name is not None else type_name(raw_name)
    lines: list[str] = []
    lines.append("/**")
    lines.append(f" * {java_name} — generated wire type ({source_desc}).")
    lines.append(" *")
    lines.append(" * Pure data DTO: public fields carrying the snake wire key; no methods (the")
    lines.append(" * reference records this as a method-less type definition).")
    lines.append(" */")
    lines.append(f"public final class {java_name} {{")
    props = node.get("properties") or {}
    used: set[str] = set()
    for wire_key, psc in props.items():
        field = type_field_name(wire_key)
        while field in used:
            field += "_"
        used.add(field)
        jtype = type_field_type(psc if isinstance(psc, dict) else {}, schemas)
        if field != wire_key:
            lines.append(f"  /** wire key: {wire_key} */")
        lines.append(f"  public {jtype} {field};")
    lines.append("}")
    return type_gen_header(package, source_desc) + "\n".join(lines) + "\n"


def emit_type_enum(package: str, enum_name: str, values: list[str], source_desc: str) -> str:
    """Emit a Java enum for an x-sdk-enum public enum (only PhoneCallHandler today).
    Each constant carries its exact wire string. Surfaced as a class by the
    reference. Returns RAW java (caller batch-formats)."""
    lines: list[str] = []
    lines.append("/**")
    lines.append(f" * {enum_name} — generated public enum ({source_desc}).")
    lines.append(" *")
    lines.append(" * Each constant's wire value is the exact wire string.")
    lines.append(" */")
    lines.append(f"public enum {enum_name} {{")
    used: set[str] = set()
    cases: list[str] = []
    for v in values:
        if v == "":
            continue
        cname = _enum_case_name(v)
        while cname in used:
            cname += "_"
        used.add(cname)
        cases.append(f'  {cname}("{v}")')
    lines.append(",\n".join(cases) + ";")
    lines.append("")
    lines.append("  public final String wire;")
    lines.append("")
    lines.append(f"  {enum_name}(String wire) {{")
    lines.append("    this.wire = wire;")
    lines.append("  }")
    lines.append("}")
    return type_gen_header(package, source_desc) + "\n".join(lines) + "\n"


# The 13 REST wire-type namespaces: (spec-dir, Java subpackage segment, oracle leaf).
# swml-webhooks is types-only (no resources / no servers block) and loaded specially.
# relay-rest folds registry.
TYPE_NS = [
    ("relay-rest", "relayrest", "relay_rest"),
    ("fabric", "fabric", "fabric"),
    ("calling", "calling", "calling"),
    ("video", "video", "video"),
    ("datasphere", "datasphere", "datasphere"),
    ("logs", "logs", "logs"),
    ("message", "message", "message"),
    ("voice", "voice", "voice"),
    ("fax", "fax", "fax"),
    ("project", "project", "project"),
    ("chat", "chat", "chat"),
    ("pubsub", "pubsub", "pubsub"),
    ("swml-webhooks", "swmlwebhooks", "swml_webhooks"),
]

# Generated wire-type classes live under this package + on-disk sub-path.
TYPES_PACKAGE = "com.signalwire.sdk.rest.namespaces.generated.types"
TYPES_DIR = "com/signalwire/sdk/rest/namespaces/generated/types"


def _load_types_schemas(psdk: Path, spec_dir: str) -> dict:
    """Load a spec's components/schemas WITHOUT the full Spec model (swml-webhooks
    has no servers block, so Spec() would fail). Ordered by yaml declaration."""
    doc = yaml.safe_load((psdk / "rest-apis" / spec_dir / "openapi.yaml").read_text())
    return ((doc.get("components") or {}).get("schemas")) or {}


def emit_types(psdk: Path, outs: dict[str, str]) -> None:
    """Emit every <ns>_types_generated Java data class / enum into
    ``types/<sub>/<TypeName>.java`` keys of ``outs`` (relative to the generated dir).
    Values are RAW java (build_outputs batch-formats)."""
    for spec_dir, sub, ns_key in TYPE_NS:
        schemas = _load_types_schemas(psdk, spec_dir)
        pkg = f"{TYPES_PACKAGE}.{sub}"
        for raw_name, node in schemas.items():
            if not isinstance(node, dict):
                continue
            xe = node.get("x-sdk-enum")
            if xe:
                enum_name = type_name(xe)
                fn = f"types/{sub}/{enum_name}.java"
                if fn not in outs:
                    outs[fn] = emit_type_enum(
                        pkg, enum_name, list(node.get("enum") or []),
                        f"x-sdk-enum on {ns_key!r} components/schemas {raw_name!r}")
            if is_object_schema(node):
                java_name = type_name(raw_name)
                fn = f"types/{sub}/{java_name}.java"
                if fn not in outs:
                    outs[fn] = emit_type_class(
                        pkg, raw_name, node,
                        f"{ns_key!r} spec, components/schemas {raw_name!r}", schemas)


# ---------------------------------------------------------------------------
# Driver.
# ---------------------------------------------------------------------------

def build_outputs(psdk: Path) -> dict[str, str]:
    load_bases(psdk)  # validate x-sdk-bases (fail loud)
    _SIDECAR.clear()
    specs = [load_spec(psdk, ns) for ns in SPEC_DIRS]
    outs: dict[str, str] = {}
    for spec in specs:
        for anchor, markup in spec.resources():
            outs[markup["name"] + ".java"] = emit_resource(spec, anchor, markup)

    placed = resolve_placement(specs)
    by_container: dict[str, list[tuple[str, str]]] = {}
    order: list[str] = []
    for spec, anchor, markup, container in placed:
        if not container:
            continue
        if container not in by_container:
            by_container[container] = []
            order.append(container)
        acc = container_accessor(markup, markup["name"], container)
        by_container[container].append((acc, markup["name"]))
    for container in order:
        if container not in CONTAINERS:
            raise SystemExit(f"container attr {container!r} has no java container class (add to CONTAINERS)")
        cls, _ = CONTAINERS[container]
        outs[cls + ".java"] = emit_container(container, by_container[container])
    outs["ResourceTree.java"] = emit_resource_tree(placed)

    # Wire-type surface (item A/H): one method-less data class / enum per
    # components/schemas OBJECT across the 13 REST namespaces, under types/<sub>/.
    emit_types(psdk, outs)

    import json as _json
    sidecar: dict[str, list[dict]] = {}
    for (cls, java_method) in sorted(_SIDECAR.keys()):
        sidecar[f"{cls}::{java_method}"] = _SIDECAR[(cls, java_method)]
    outs["rest_signatures.json"] = _json.dumps(
        {
            "_comment": "Code generated by scripts/generate_rest.py; DO NOT EDIT. "
                        "Canonical typed-param records for generated REST operation/"
                        "command/set methods; consumed by scripts/enumerate_signatures.py "
                        "to unfold the reflected Java builder params onto the oracle shape.",
            "methods": sidecar,
        },
        indent=2, sort_keys=False,
    ) + "\n"

    # Format every emitted .java through google-java-format so the on-disk files
    # are byte-identical to what the FMT gate (spotless) would produce and clean
    # under the LINT gate (Checkstyle). The JSON sidecar is left as-is. One BATCH
    # gjf invocation (not per-file) — the ~700 generated wire-type files make
    # per-file JVM spawns prohibitively slow.
    java_srcs = {fn: outs[fn] for fn in outs if fn.endswith(".java")}
    for fn, formatted in gjf_format_many(java_srcs).items():
        outs[fn] = formatted
    return outs


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true", help="GEN-FRESH: exit non-zero if stale")
    ap.add_argument("--out", default="", help="scratch: emit flat into this dir")
    args = ap.parse_args(argv)

    psdk = resolve_porting_sdk()
    outs = build_outputs(psdk)

    if args.out:
        out_dir = Path(args.out)
    else:
        out_dir = repo_root() / "src" / "main" / "java" / GEN_DIR

    if args.check:
        stale = []
        for fn, src in outs.items():
            p = out_dir / fn
            if not p.is_file() or p.read_text() != src:
                stale.append(str(p))
        expected = set(outs.keys())
        for p in sorted(out_dir.rglob("*.java")):
            rel = p.relative_to(out_dir).as_posix()
            if rel not in expected:
                stale.append(f"{p} (leftover — not in generator output)")
        if stale:
            sys.stderr.write("GEN-FRESH FAIL: %d generated REST file(s) stale:\n" % len(stale))
            for s in stale:
                sys.stderr.write("  - %s\n" % s)
            return 1
        print("GEN-FRESH: generated REST files match the canonical specs.")
        return 0

    out_dir.mkdir(parents=True, exist_ok=True)
    for fn, src in outs.items():
        p = out_dir / fn
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(src)
    print(f"generated {len(outs)} REST file(s) into {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
