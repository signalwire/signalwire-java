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


# Additional Java class renames not in enumerate_surface.py:
# These are core SDK classes that Java names differently from Python.
JAVA_EXTRA_RENAMES = {
    "Service": "SWMLService",       # SignalWire.SWML.Service → core.swml_service.SWMLService
    "AgentBase": "AgentBase",       # already matches
}

# Java skill class renames to match Python casing
JAVA_SKILL_RENAMES = {
    "DatasphereSkill": "DataSphereSkill",
    "DatasphereServerlessSkill": "DataSphereServerlessSkill",
    "McpGatewaySkill": "MCPGatewaySkill",
    "SwmlTransferSkill": "SWMLTransferSkill",
}

# Java module path overrides for canonical Python paths
# Key: Java fully-qualified package.Class
JAVA_MODULE_OVERRIDES = {
    "com.signalwire.sdk.swml.Service": "signalwire.core.swml_service",
    "com.signalwire.sdk.swml.Document": "signalwire.core.swml_builder",
    "com.signalwire.sdk.swml.Schema": "signalwire.utils.schema_utils",
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

MIXIN_PROJECTIONS = {
    ("signalwire.core.mixins.ai_config_mixin", "AIConfigMixin"): [
        "add_function_include", "add_hint", "add_hints", "add_internal_filler",
        "add_language", "add_pattern_hint", "add_pronunciation",
        "enable_debug_events", "set_function_includes", "set_global_data",
        "set_internal_fillers", "set_languages", "set_native_functions",
        "set_param", "set_params", "set_post_prompt_llm_params",
        "set_prompt_llm_params", "set_pronunciations", "update_global_data",
    ],
    ("signalwire.core.mixins.prompt_mixin", "PromptMixin"): [
        "define_contexts", "get_post_prompt", "get_prompt",
        "prompt_add_section",
        "prompt_add_subsection", "prompt_add_to_section",
        "prompt_has_section", "reset_contexts", "set_post_prompt",
        "set_prompt_text",
    ],
    ("signalwire.core.mixins.skill_mixin", "SkillMixin"): [
        "add_skill", "has_skill", "list_skills", "remove_skill",
    ],
    ("signalwire.core.mixins.tool_mixin", "ToolMixin"): [
        "define_tool", "on_function_call", "register_swaig_function",
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
        "enable_debug_routes", "manual_set_proxy_url", "run", "serve",
        "set_dynamic_config_callback", "on_request", "on_swml_request",
    ],
    ("signalwire.core.mixins.mcp_server_mixin", "MCPServerMixin"): [
        "add_mcp_server",
    ],
    ("signalwire.core.mixins.state_mixin", "StateMixin"): [
        "validate_tool_token",
    ],
}


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

        # First check explicit Java module overrides
        full_pkg = f"{pkg}.{java_name}" if pkg else java_name
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
        if ab_entry and not ab_methods:
            out_modules["signalwire.core.agent_base"]["classes"].pop("AgentBase", None)
            if not out_modules["signalwire.core.agent_base"].get("classes"):
                out_modules.pop("signalwire.core.agent_base", None)

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
