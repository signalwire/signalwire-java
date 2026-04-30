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
import re
import subprocess
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
PORT_ROOT = HERE.parent
PSDK = (PORT_ROOT.parent / "porting-sdk").resolve()
if not PSDK.is_dir():
    PSDK = Path("/usr/local/home/devuser/src/porting-sdk")

sys.path.insert(0, str(HERE))
from enumerate_surface import (  # type: ignore
    _CLASS_RENAMES, _METHOD_RENAMES, _PY_KEYWORDS, build_class_to_module_map,
    camel_to_snake, translate_method_name,
)


class TypeTranslationError(RuntimeError):
    pass


def load_aliases() -> dict[str, str]:
    data = yaml.safe_load((PSDK / "type_aliases.yaml").read_text(encoding="utf-8"))
    return {str(k): str(v) for k, v in data.get("aliases", {}).get("java", {}).items()}


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


# ---------------------------------------------------------------------------
# Building canonical inventory
# ---------------------------------------------------------------------------

# Loaded lazily via main()
CLASS_TO_MODULE: dict[str, str] = {}


def collect(raw: dict, aliases: dict) -> tuple[dict, list]:
    out_modules: dict = {}
    failures: list = []

    for type_entry in raw.get("types", []):
        pkg = type_entry.get("package", "")
        java_name = type_entry.get("name", "")
        if not java_name:
            continue
        if type_entry.get("kind") in ("annotation",):
            continue
        canonical_name = _CLASS_RENAMES.get(java_name, java_name)
        # Find Python module
        if canonical_name in CLASS_TO_MODULE:
            mod = CLASS_TO_MODULE[canonical_name]
        else:
            mod = _pkg_to_module(pkg)

        methods_out: dict = {}
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
            ctx = f"{mod}.{canonical_name}.{method_canonical}"
            try:
                sig = build_signature(m, aliases, ctx, mod, canonical_name)
            except TypeTranslationError as e:
                failures.append(str(e))
                continue
            # Java overloads collapse to one entry; prefer fewer-param overload
            if method_canonical in methods_out:
                existing = methods_out[method_canonical]
                if len(sig["params"]) >= len(existing["params"]):
                    continue
            methods_out[method_canonical] = sig

        if not methods_out:
            continue

        out_modules.setdefault(mod, {"classes": {}})
        out_modules[mod]["classes"][canonical_name] = {
            "methods": dict(sorted(methods_out.items())),
        }

    sorted_modules = {}
    for k in sorted(out_modules):
        entry = out_modules[k]
        sorted_modules[k] = {
            "classes": {
                cls: entry["classes"][cls] for cls in sorted(entry["classes"])
            }
        }
    return {
        "version": "2",
        "generated_from": "signalwire-java JAR via SignatureDump (java.lang.reflect)",
        "modules": sorted_modules,
    }, failures


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
    # Compile the helper if needed
    helper_class = PORT_ROOT / "build" / "scripts" / "SignatureDump.class"
    if not helper_class.exists():
        cp = subprocess.run(
            ["javac", "-parameters", str(HERE / "SignatureDump.java"), "-d", str(PORT_ROOT / "build" / "scripts")],
            capture_output=True, text=True, timeout=120,
        )
        if cp.returncode != 0:
            raise RuntimeError(f"javac failed:\n{cp.stderr}")

    jar = PORT_ROOT / "build" / "libs" / "signalwire-sdk-2.0.0.jar"
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

    if args.raw and args.raw.is_file():
        raw = json.loads(args.raw.read_text(encoding="utf-8"))
    else:
        raw = run_dump()

    canonical, failures = collect(raw, aliases)
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
