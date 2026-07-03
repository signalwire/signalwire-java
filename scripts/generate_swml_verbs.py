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
    spec = importlib.util.spec_from_file_location("generate_rest", here / "generate_rest.py")
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


def build_outputs(psdk: Path) -> dict[str, str]:
    defs = _load_defs(psdk)
    outs: dict[str, str] = {}
    emitted_names: set[str] = set()

    # 1. One data class per OBJECT $defs schema (drop scalar/array/union aliases —
    #    same rule as the REST wire-type emitter).
    for raw_name, node in defs.items():
        if not isinstance(node, dict):
            continue
        if not GR.is_object_schema(node):
            continue
        java_name = GR.type_name(raw_name)
        if java_name in emitted_names:
            continue
        emitted_names.add(java_name)
        outs[f"{java_name}.java"] = GR.emit_type_class(
            GEN_PACKAGE, raw_name, node,
            f"schema.json $defs schema {raw_name!r}", defs,
        )

    # 2. One <Verb>Config data class per SWMLMethod.anyOf verb whose inner schema
    #    is an inline object / oneOf union (flattened union of variant props).
    sm = defs.get("SWMLMethod")
    if sm:
        for ref in sm.get("anyOf") or []:
            wrapper = _ref_leaf(ref.get("$ref", ""))
            wdef = defs.get(wrapper)
            if not wdef or not (wdef.get("properties") or {}):
                continue
            verb = next(iter(wdef["properties"].keys()))
            if verb in HAND_WRITTEN_VERBS:
                continue
            inner = wdef["properties"][verb]
            if _type_str(inner) == "string" or inner.get("$ref"):
                continue
            has_inline = _type_str(inner) == "object" and bool(inner.get("properties"))
            if not inner.get("oneOf") and not has_inline:
                continue
            props = _flatten_union(defs, inner)
            if not props:
                continue
            cfg_name = _pascal(verb) + "Config"
            java_name = GR.type_name(cfg_name)
            if java_name in emitted_names:
                continue
            emitted_names.add(java_name)
            outs[f"{java_name}.java"] = GR.emit_type_class(
                GEN_PACKAGE, cfg_name, {"type": "object", "properties": props},
                f"flattened SWMLMethod verb {verb!r} config", defs,
            )

    # Batch-format through google-java-format (one JVM) so the on-disk files are
    # byte-identical to spotlessApply / clean under Checkstyle.
    for fn, formatted in GR.gjf_format_many(outs).items():
        outs[fn] = formatted
    return outs


# ---------------------------------------------------------------------------
# Driver.
# ---------------------------------------------------------------------------

def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true", help="GEN-FRESH: exit non-zero if stale")
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
            sys.stderr.write("GEN-FRESH FAIL: %d generated SWML-verb file(s) stale:\n" % len(stale))
            for s in stale:
                sys.stderr.write("  - %s\n" % s)
            return 1
        print("GEN-FRESH: generated SWML-verb files match porting-sdk/schema.json ($defs).")
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
