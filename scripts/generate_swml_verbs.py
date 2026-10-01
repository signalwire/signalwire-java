#!/usr/bin/env python3
"""Generate the typed SWML-verbs CONFIG surface for signalwire-java.

This is the JAVA realization of SESSION_CHANGESET_FOR_PORTS.md item D2 — the
``signalwire.core.swml_verbs_generated`` module — mirroring python's
``swml_verbs_generated.py``, php's ``generate_swml_verbs.py``, go's
``pkg/swml/swml_verbs_generated.go`` and TS's ``swml_verbs_generated.ts``.

Source: the CANONICAL porting-sdk ``schema.json`` ``$defs`` (167 defs). The java
repo also vendors a copy at ``src/main/resources/schema.json``; they carry the
IDENTICAL ``$defs`` set — this generator reads the porting-sdk canonical so the
port tracks the shared source, exactly like generate_rest.py.

What is emitted (matching the Python SURFACE oracle's 155 method-less types — the
reference's ``_SwmlVerbs`` verb-METHOD protocol is ``_``-prefixed and NOT part of
the cross-port surface oracle, so only the CONFIG type surface is emitted):

  1. One Java data class per ``$defs`` OBJECT schema (133) — public fields carrying
     the snake wire key, no methods. The emit/drop rule is the SAME as
     generate_rest.py's wire-type emitter: object schema -> data class;
     scalar / array / oneOf / anyOf / allOf union alias -> NOT surfaced.

  2. One ``<Verb>Config`` data class per SWMLMethod.anyOf verb whose inner schema
     is an inline object / oneOf union (22) — the flattened UNION of the verb's
     variant properties (mirrors go's flattenUnion / the reference _flatten_union).
     Hand-written verbs (answer/hangup/ai/play/say) are excluded from the Config
     flatten, matching go's handWrittenVerbs / the reference hand_written set.

  133 object classes + 22 Config classes = 155 == the oracle exactly (0/0).

Reserved-word class names (Goto/Return/Switch/Unset — hard Java keywords) get a
``_`` suffix in the Java class + filename (via generate_rest.type_name); the
enumerators map them back to the bare oracle leaf, exactly as the REST wire types.

Output layout: one class per file under
  src/main/java/com/signalwire/sdk/swml/generated/<ClassName>.java
in package ``com.signalwire.sdk.swml.generated``. The enumerators route every file
in that package BY PATH to the oracle module ``signalwire.core.swml_verbs_generated``
(not class name), so a type name that also exists as a REST wire type
(AIObject/Cond/Section/DataMap — 125 of the 155 recur in the <ns>_types_generated
modules) lands in the right module; the SURFACE-DIFF gen-type leaf fold then
collapses the cross-module duplicates on both the reference and the port.

Usage:
    python3 scripts/generate_swml_verbs.py            # write into the repo tree
    python3 scripts/generate_swml_verbs.py --check    # GEN-FRESH: fail if stale
    python3 scripts/generate_swml_verbs.py --out DIR  # scratch: emit into DIR
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import re
import sys
from pathlib import Path


# ---------------------------------------------------------------------------
# Reuse the shared emit helpers from generate_rest.py (is_object_schema,
# type_name, type_field_type/name, emit_type_class, gjf_format_many). Import by
# path so the two generators never diverge on the emit rule.
# ---------------------------------------------------------------------------


def _load_rest_generator():
    here = Path(__file__).resolve().parent
    spec = importlib.util.spec_from_file_location(
        "generate_rest", here / "generate_rest.py"
    )
    if spec is None or spec.loader is None:  # pragma: no cover
        raise SystemExit("generate_swml_verbs.py: cannot load generate_rest.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


GR = _load_rest_generator()

GEN_PACKAGE = "com.signalwire.sdk.swml.generated"
GEN_DIR = "com/signalwire/sdk/swml/generated"


def resolve_porting_sdk() -> Path:
    return GR.resolve_porting_sdk()


def repo_root() -> Path:
    return Path(__file__).resolve().parents[1]


# Verbs the reference hand-writes with richer ergonomics; excluded from the
# <Verb>Config flatten (matches go's handWrittenVerbs / the reference hand_written
# set). Only affects which Config classes are emitted — every $defs OBJECT schema
# is still emitted as a data class regardless.
HAND_WRITTEN_VERBS = {"answer", "hangup", "ai", "play", "say"}


def _load_defs(psdk: Path) -> dict:
    doc = json.loads((psdk / "schema.json").read_text())
    defs = doc.get("$defs")
    if not defs:
        raise SystemExit("generate_swml_verbs.py: schema.json has no $defs")
    return defs


def _ref_leaf(ref: str) -> str:
    return ref.rsplit("/", 1)[-1] if ref else ref


def _type_str(node: dict) -> str | None:
    t = node.get("type")
    if isinstance(t, list):
        return next((x for x in t if x != "null"), None)
    return t


def _pascal(s: str) -> str:
    parts = re.split(r"[_\-\s.]", s)
    return "".join(w[:1].upper() + w[1:] for w in parts if w)


def _flatten_union(defs: dict, node: dict | None) -> dict:
    """Return the UNION of properties across allOf/oneOf/anyOf, following $ref
    (mirrors go's flattenUnion / the reference _flatten_union). First-seen wins."""
    out: dict = {}

    def walk(n: dict | None) -> None:
        if not n:
            return
        ref = n.get("$ref")
        if ref:
            walk(defs.get(_ref_leaf(ref)))
            return
        for sub in n.get("allOf") or []:
            walk(sub)
        for name, psc in (n.get("properties") or {}).items():
            out.setdefault(name, psc)
        for sub in n.get("oneOf") or []:
            walk(sub)
        for sub in n.get("anyOf") or []:
            walk(sub)

    walk(node)
    return out


# ---------------------------------------------------------------------------
# schema.json transforms — the SAME ones the reference generator applies
# (porting-sdk/scripts/generate_python_rest_types.py render_swml_verbs), so the emitted
# class set and names match the python oracle's ``signalwire.core.swml_verbs_generated``.
# ---------------------------------------------------------------------------


def swml_verb_is_deprecated(wrapper: dict) -> bool:
    """A SWMLMethod wrapper marked ``deprecated: true`` (on itself or its verb property).
    Keyed on the schema annotation, never on a verb-name list."""
    if wrapper.get("deprecated") is True:
        return True
    props = wrapper.get("properties") or {}
    return any(
        isinstance(v, dict) and v.get("deprecated") is True for v in props.values()
    )


def drop_deprecated_swml_verbs(defs: dict) -> dict:
    """``defs`` without its deprecated verb wrappers (owner ruling 2026-09-24: dial/eval/if
    are deprecated and not SDK surface); they also leave the SWMLMethod union."""
    swml_method = defs.get("SWMLMethod") or {}
    kept, dropped = [], []
    for arm in swml_method.get("anyOf") or []:
        wrapper = _ref_leaf(str(arm.get("$ref") or ""))
        wdef = defs.get(wrapper)
        if isinstance(wdef, dict) and swml_verb_is_deprecated(wdef):
            dropped.append(wrapper)
            continue
        kept.append(arm)
    if not dropped:
        return defs
    out = {k: v for k, v in defs.items() if k not in dropped}
    out["SWMLMethod"] = {**swml_method, "anyOf": kept}
    return out


_PRESENCE_KEYS = frozenset({"required", "anyOf", "oneOf", "allOf"})


def _presence_only(arms) -> bool:
    """Every arm constrains only WHICH keys are present (``required`` combined by
    anyOf/oneOf/allOf) — the engine's one-of rules, which add no key and no type."""
    if not isinstance(arms, list) or not arms:
        return False
    for arm in arms:
        if not isinstance(arm, dict) or not arm or not set(arm) <= _PRESENCE_KEYS:
            return False
        req = arm.get("required")
        if req is not None and not (
            isinstance(req, list) and all(isinstance(r, str) for r in req)
        ):
            return False
        for key in ("anyOf", "oneOf", "allOf"):
            if key in arm and not _presence_only(arm[key]):
                return False
    return True


def _without_presence_allof(node: dict) -> dict:
    if _presence_only(node.get("allOf")):
        return {k: v for k, v in node.items() if k != "allOf"}
    return node


def _is_inline_object(node) -> bool:
    if isinstance(node, dict):
        node = _without_presence_allof(node)
    return (
        isinstance(node, dict)
        and "$ref" not in node
        and bool(node.get("properties"))
        and node.get("type") in ("object", None)
        and not (node.get("anyOf") or node.get("oneOf") or node.get("allOf"))
    )


def hoist_inline_objects(defs: dict, verb_roots: dict[str, str]) -> dict:
    """Lift every inline property-bearing object into its own named ``$def`` (a ``$ref``
    replaces it), so each emits as a typed class. Names derive from the schema path:
    a verb wrapper's verb object is ``<Verb>Config`` and its descendants are prefixed
    ``<Verb>``; any other object is ``<Parent><Key>``; an array element adds ``Item``;
    in a union with ONE object arm that arm takes the union's name, with several each
    takes ``<Name><ArmTitle>`` (or ``<Name>Variant<i>``). A taken name gets a numeric
    suffix. Originals first, hoisted after, in walk order."""
    taken: set[str] = set(defs)
    hoisted: dict[str, dict] = {}
    name_of: dict[int, str] = {}

    def claim(name: str) -> str:
        cand, n = name, 2
        while cand in taken:
            cand, n = f"{name}{n}", n + 1
        taken.add(cand)
        return cand

    def walk_children(node: dict, prefix: str) -> dict:
        out = dict(node)
        if isinstance(node.get("properties"), dict):
            out["properties"] = {
                k: visit(v, prefix + _pascal(k), prefix + _pascal(k))
                for k, v in node["properties"].items()
            }
        if isinstance(node.get("items"), dict):
            out["items"] = visit(node["items"], prefix + "Item", prefix + "Item")
        if isinstance(node.get("prefixItems"), list):
            out["prefixItems"] = [
                visit(p, f"{prefix}Item{i + 1}", f"{prefix}Item{i + 1}")
                for i, p in enumerate(node["prefixItems"])
            ]
        if isinstance(node.get("additionalProperties"), dict):
            out["additionalProperties"] = visit(
                node["additionalProperties"], prefix + "Value", prefix + "Value"
            )
        for key in ("anyOf", "oneOf", "allOf"):
            arms = node.get(key)
            if not isinstance(arms, list):
                continue
            n_obj = sum(1 for a in arms if _is_inline_object(a))
            new_arms = []
            for i, arm in enumerate(arms):
                if n_obj > 1 and _is_inline_object(arm):
                    title = re.sub(r"[^A-Za-z0-9]+", " ", str(arm.get("title") or ""))
                    suffix = _pascal(title)
                    arm_name = f"{prefix}{suffix or f'Variant{i + 1}'}"
                    new_arms.append(visit(arm, arm_name, arm_name))
                else:
                    new_arms.append(visit(arm, name_of[id(node)], prefix))
            out[key] = new_arms
        return out

    def visit(node, name: str, prefix: str):
        if not isinstance(node, dict):
            return node
        if _is_inline_object(node):
            final = claim(name)
            child_prefix = prefix if prefix != name else final
            hoisted[final] = {}
            hoisted[final] = walk_children(_without_presence_allof(node), child_prefix)
            ref = {"$ref": f"#/$defs/{final}"}
            for keep in ("description", "title", "deprecated", "x-api-state"):
                if keep in node:
                    ref[keep] = node[keep]
            return ref
        name_of[id(node)] = name
        return walk_children(node, prefix)

    out: dict = {}
    for def_name, sch in defs.items():
        if not isinstance(sch, dict):
            out[def_name] = sch
            continue
        verb = verb_roots.get(def_name)
        if verb is not None:
            props = dict(sch.get("properties") or {})
            base = _pascal(verb)
            props[verb] = visit(props[verb], base + "Config", base)
            out[def_name] = {**sch, "properties": props}
        else:
            name_of[id(sch)] = def_name
            out[def_name] = walk_children(sch, def_name)
    out.update(hoisted)
    return out


#: The SWAIG response ENVELOPE types are declared ONCE, by the SWAIG action payloads
#: (generate_swaig_payloads.py, from swaig-response.yaml). schema.json carries the same
#: shapes as $defs; the SWML tree does not re-declare them (nor the inline objects hoisted
#: out of them) — the reference makes the identical cut.
SWAIG_ENVELOPE_TYPES = ("SwaigAction", "SwaigResponse")


def _verb_config_ref(defs: dict, inner: dict) -> str | None:
    ref_t = inner.get("$ref")
    if not ref_t and inner.get("anyOf"):
        obj_refs = [
            a["$ref"]
            for a in inner["anyOf"]
            if isinstance(a, dict)
            and "$ref" in a
            and _is_inline_object(defs.get(_ref_leaf(str(a["$ref"]))))
        ]
        if len(obj_refs) == 1:
            ref_t = obj_refs[0]
    return ref_t


def _is_declared_object(schema: dict) -> bool:
    """The reference ``declaration()`` rule: an object with properties and no combinator
    is a class; everything else is an alias (not surfaced)."""
    is_object = (
        schema.get("type") == "object"
        or (not schema.get("type") and schema.get("properties"))
    ) and not (schema.get("oneOf") or schema.get("anyOf") or schema.get("allOf"))
    return bool(is_object and schema.get("properties"))


def build_outputs(psdk: Path) -> dict[str, str]:
    defs = drop_deprecated_swml_verbs(_load_defs(psdk))
    verb_roots: dict[str, str] = {}
    for arm in (defs.get("SWMLMethod") or {}).get("anyOf") or []:
        wrapper = _ref_leaf(str(arm.get("$ref") or ""))
        wprops = list(((defs.get(wrapper) or {}).get("properties") or {}).keys())
        if wprops:
            verb_roots[wrapper] = wprops[0]
    defs = hoist_inline_objects(defs, verb_roots)

    outs: dict[str, str] = {}
    emitted_names: set[str] = set()
    imported = [n for n in SWAIG_ENVELOPE_TYPES if n in defs]

    def emit(raw_name: str, node: dict, desc: str) -> None:
        java_name = GR.type_name(raw_name)
        if java_name in emitted_names:
            raise SystemExit(f"generate_swml_verbs.py: duplicate class {java_name}")
        emitted_names.add(java_name)
        outs[f"{java_name}.java"] = GR.emit_type_class(
            GEN_PACKAGE, raw_name, node, desc, defs
        )

    # 1. One data class per OBJECT $def (after hoisting), skipping the SWAIG envelope
    #    types and their hoisted interiors.
    for raw_name, node in defs.items():
        if not isinstance(node, dict):
            continue
        if raw_name in imported or any(
            raw_name.startswith(n) and raw_name[len(n) : len(n) + 1].isupper()
            for n in imported
        ):
            continue
        if not _is_declared_object(node):
            continue
        emit(raw_name, node, f"schema.json $defs schema {raw_name!r}")

    # 2. A flattened <Verb>Config per non-hand-written verb whose body is a oneOf (or a
    #    plain object) with no single hoisted config.
    for arm in (defs.get("SWMLMethod") or {}).get("anyOf") or []:
        wrapper = _ref_leaf(str(arm.get("$ref") or ""))
        wdef = defs.get(wrapper) or {}
        props = list((wdef.get("properties") or {}).keys())
        if not props:
            continue
        verb = props[0]
        if verb in HAND_WRITTEN_VERBS:
            continue
        inner = wdef["properties"][verb]
        if inner.get("type") == "string":
            continue
        if _verb_config_ref(defs, inner):
            continue
        if inner.get("oneOf") or (
            inner.get("type") == "object" and inner.get("properties")
        ):
            flat = _flatten_union(defs, inner)
            if flat:
                emit(
                    _pascal(verb) + "Config",
                    {"type": "object", "properties": flat},
                    f"flattened SWMLMethod verb {verb!r} config",
                )

    # Batch-format through google-java-format (one JVM) so the on-disk files are
    # byte-identical to spotlessApply / clean under Checkstyle.
    outs.update(GR.gjf_format_many(outs))
    return outs


# ---------------------------------------------------------------------------
# Driver.
# ---------------------------------------------------------------------------


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--check", action="store_true", help="GEN-FRESH: exit non-zero if stale"
    )
    ap.add_argument("--out", default="", help="scratch: emit into this dir")
    args = ap.parse_args(argv)

    psdk = resolve_porting_sdk()
    outs = build_outputs(psdk)

    if args.out:
        out_dir = Path(args.out)
    else:
        out_dir = repo_root() / "src" / "main" / "java" / GEN_DIR

    if args.check:
        stale: list[str] = []
        for fn, src in outs.items():
            p = out_dir / fn
            if not p.is_file() or p.read_text() != src:
                stale.append(str(p))
        expected = set(outs.keys())
        if out_dir.is_dir():
            for p in sorted(out_dir.rglob("*.java")):
                rel = p.relative_to(out_dir).as_posix()
                if rel not in expected:
                    stale.append(f"{p} (leftover — not in generator output)")
        if stale:
            sys.stderr.write(
                f"GEN-FRESH FAIL: {len(stale)} generated SWML-verb file(s) stale:\n"
            )
            for s in stale:
                sys.stderr.write(f"  - {s}\n")
            return 1
        print(
            "GEN-FRESH: generated SWML-verb files match porting-sdk/schema.json ($defs)."
        )
        return 0

    out_dir.mkdir(parents=True, exist_ok=True)
    for fn, src in outs.items():
        p = out_dir / fn
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(src)
    print(f"generated {len(outs)} SWML-verb file(s) into {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
