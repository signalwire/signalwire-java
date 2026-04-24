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

Usage::

    python3 scripts/enumerate_surface.py                      # stdout
    python3 scripts/enumerate_surface.py --output port_surface.json
    python3 scripts/enumerate_surface.py --check --output port_surface.json
"""

from __future__ import annotations

import argparse
import json
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
}

_CAMEL_RE_1 = re.compile(r"(.)([A-Z][a-z]+)")
_CAMEL_RE_2 = re.compile(r"([a-z0-9])([A-Z])")
_PY_KEYWORDS = {"pass", "class", "def", "from", "import", "return", "yield",
                "global", "lambda", "raise", "try", "with", "async", "await"}

# Java method name → Python-reference method name. Applied after the usual
# camel→snake translation to bridge idiomatic naming gaps (Java's
# ``toMap`` vs Python's ``to_dict``).
_METHOD_RENAMES: dict[str, str] = {
    "to_map": "to_dict",
}


def camel_to_snake(name: str) -> str:
    """``setPromptText`` → ``set_prompt_text``; ``URL`` stays ``url``."""
    s1 = _CAMEL_RE_1.sub(r"\1_\2", name)
    s2 = _CAMEL_RE_2.sub(r"\1_\2", s1).lower()
    # Collapse accidental double-underscores from all-caps runs.
    while "__" in s2 and not (s2.startswith("__") and s2.endswith("__")):
        s2 = s2.replace("__", "_")
    return s2


def translate_method_name(java_name: str, class_name: str) -> str | None:
    """Java method → Python-reference method name, or None if skipped.

    - Constructors (``ClassName``) map to ``__init__``.
    - ``toString`` maps to ``__repr__``.
    - Everything else: camelCase → snake_case.
    - If the result collides with a Python keyword (``pass``), a trailing
      underscore is added (``pass_``) — this matches signalwire-python's
      convention for ``Call.pass_``.
    """
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
                java_outer_name=name,
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

            translated = translate_method_name(name, java_outer_name)
            if translated is not None:
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


def enumerate_file(path: Path, class_to_module: dict[str, str]
                   ) -> dict[str, dict]:
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
    outer_name = _CLASS_RENAMES.get(outer_name_raw, outer_name_raw)
    body_open = stripped.find("{", m_type.end())
    if body_open < 0:
        return {}
    body_close = find_matching_brace(stripped, body_open)
    body = stripped[body_open + 1 : body_close]

    known_python_classes = set(class_to_module.keys())
    classes = parse_type_body(
        body, outer_name, known_python_classes,
        java_outer_name=outer_name_raw,
    )

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
    return out


def enumerate_sdk(java_src_root: Path, class_to_module: dict[str, str]
                  ) -> dict[str, dict]:
    """Walk ``java_src_root/com/signalwire/sdk`` and collect all classes."""
    merged: dict[str, dict] = {}
    for path in sorted(java_src_root.rglob("*.java")):
        per_file = enumerate_file(path, class_to_module)
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
    return merged


def git_sha(repo: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "-C", str(repo), "rev-parse", "HEAD"],
            stderr=subprocess.DEVNULL,
        ).decode().strip()
    except Exception:
        return "N/A"


def build_snapshot(repo_root: Path, reference_json: Path) -> dict:
    class_to_module = build_class_to_module_map(reference_json)
    java_src = repo_root / "src" / "main" / "java"
    if not java_src.is_dir():
        raise SystemExit(f"error: java source not found at {java_src}")
    modules = enumerate_sdk(java_src, class_to_module)
    return {
        "version": "1",
        "generated_from": f"signalwire-java @ {git_sha(repo_root)}",
        "language": "java",
        "modules": modules,
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo", type=Path, default=Path(__file__).resolve().parent.parent,
        help="Path to the signalwire-java repo root (default: script's repo)",
    )
    parser.add_argument(
        "--reference", type=Path,
        default=Path.home() / "src" / "porting-sdk" / "python_surface.json",
        help="Path to porting-sdk/python_surface.json for class→module lookup",
    )
    parser.add_argument(
        "--output", type=Path, default=None,
        help="Write JSON to this path (default: stdout)",
    )
    parser.add_argument(
        "--check", action="store_true",
        help="Compare against file at --output; exit 1 on drift",
    )
    args = parser.parse_args(argv)

    if args.check and not args.output:
        parser.error("--check requires --output")
    if not args.reference.is_file():
        print(f"error: reference {args.reference} not found", file=sys.stderr)
        return 1

    snapshot = build_snapshot(args.repo, args.reference)
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
