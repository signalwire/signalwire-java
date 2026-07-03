#!/usr/bin/env python3
"""Generate the typed SWAIG read-side payload surface for signalwire-java.

This is the JAVA realization of SESSION_CHANGESET_FOR_PORTS.md item D1 — the three
``signalwire.core.*_generated`` SWAIG payload modules — mirroring python's
``generate_swaig_request`` / ``generate_post_prompt`` / ``generate_swaig_actions``,
php's ``generate_swaig_payloads.py``, go's ``pkg/swaig/*_generated.go`` and TS's
``SwaigContracts.generated.ts`` / ``SwaigActions.generated.ts``.

Source: the vendored porting-sdk ``swaig-specs/*.yaml`` (VENDORED from mod_openai —
the authoritative SWAIG wire spec):

  * ``swaig-request.yaml``  -> signalwire.core.swaig_request_generated  (2 classes)
        SwaigRequest (+ the inline ``argument`` object lifted to SwaigArgument).
  * ``post-prompt.yaml``    -> signalwire.core.post_prompt_generated    (14 classes)
        one class per components/schemas OBJECT schema; the ``PostPromptCallLogEntry``
        oneOf alias is NOT surfaced (the reference records it as a module-level
        TypeAlias its enumerator drops), so 15 schemas - 1 alias = 14.
  * ``swaig-response.yaml`` -> signalwire.core.swaig_actions_generated  (4 classes)
        one ``<Action>`` class per action key whose value is an object-with-properties
        (a bare object OR an object variant of a oneOf): context_switch ->
        ContextSwitchAction, hold -> HoldAction, playback_bg -> PlaybackBgAction,
        transfer -> TransferAction.

  2 + 14 + 4 = 20 classes == the surface oracle EXACTLY (0 missing / 0 extra).

Every emitted class is a method-less Java data DTO: public fields carrying the snake
wire key, no methods. The emit/drop rule + field rendering reuse the SHARED helpers
from generate_rest.py (is_object_schema, type_name, emit_type_class) exactly like
generate_swml_verbs.py / generate_relay_protocol.py.

These are READ-side payloads (open shapes; no ``extras`` door). Nothing is emitted
for scalar/array/union aliases.

Output layout: one class per file under a per-module subpackage
  src/main/java/com/signalwire/sdk/swaig/generated/postprompt/<ClassName>.java
  src/main/java/com/signalwire/sdk/swaig/generated/swaigrequest/<ClassName>.java
  src/main/java/com/signalwire/sdk/swaig/generated/swaigactions/<ClassName>.java
so the enumerators route each subpackage BY PATH to its
``signalwire.core.<post_prompt|swaig_request|swaig_actions>_generated`` oracle module.

Usage:
    python3 scripts/generate_swaig_payloads.py            # write into the repo tree
    python3 scripts/generate_swaig_payloads.py --check    # GEN-FRESH: fail if stale
    python3 scripts/generate_swaig_payloads.py --out DIR  # scratch: emit into DIR
"""
from __future__ import annotations

import argparse
import importlib.util
import re
import sys
from pathlib import Path


def _load_rest_generator():
    here = Path(__file__).resolve().parent
    spec = importlib.util.spec_from_file_location("generate_rest", here / "generate_rest.py")
    if spec is None or spec.loader is None:  # pragma: no cover
        raise SystemExit("generate_swaig_payloads.py: cannot load generate_rest.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


GR = _load_rest_generator()

GEN_PACKAGE = "com.signalwire.sdk.swaig.generated"
GEN_DIR = "com/signalwire/sdk/swaig/generated"
# (subdir/subpackage segment) per module.
SUB_POSTPROMPT = "postprompt"
SUB_REQUEST = "swaigrequest"
SUB_ACTIONS = "swaigactions"


def resolve_porting_sdk() -> Path:
    return GR.resolve_porting_sdk()


def repo_root() -> Path:
    return Path(__file__).resolve().parents[1]


def _load_yaml(path: Path) -> dict:
    import yaml  # type: ignore[import-untyped]

    return yaml.safe_load(path.read_text())


def _emit(sub: str, class_name: str, properties: dict, schemas: dict, source_desc: str) -> str:
    pkg = f"{GEN_PACKAGE}.{sub}"
    node = {"type": "object", "properties": properties}
    return GR.emit_type_class(pkg, class_name, node, source_desc, schemas, class_name=class_name)


def _build_swaig_request(psdk: Path) -> dict[str, str]:
    """swaig-request.yaml -> SwaigRequest (+ lifted SwaigArgument)."""
    spec_file = "swaig-request.yaml"
    spec = _load_yaml(psdk / "swaig-specs" / spec_file)
    schema = spec["components"]["schemas"]["SwaigRequest"]
    props = schema.get("properties", {})
    outs: dict[str, str] = {}

    arg = props.get("argument")
    if isinstance(arg, dict) and arg.get("properties"):
        outs[f"{SUB_REQUEST}/SwaigArgument.java"] = _emit(
            SUB_REQUEST, "SwaigArgument", arg["properties"], {},
            "inline swaig-request `argument` object")

    outs[f"{SUB_REQUEST}/SwaigRequest.java"] = _emit(
        SUB_REQUEST, "SwaigRequest", props, {},
        "swaig-request `SwaigRequest` schema")
    return outs


def _build_post_prompt(psdk: Path) -> dict[str, str]:
    """post-prompt.yaml -> one class per components/schemas OBJECT schema (15 - 1
    oneOf alias = 14)."""
    spec_file = "post-prompt.yaml"
    spec = _load_yaml(psdk / "swaig-specs" / spec_file)
    schemas = spec["components"]["schemas"]
    outs: dict[str, str] = {}
    emitted: set[str] = set()
    for raw_name, node in schemas.items():
        if not isinstance(node, dict):
            continue
        if not GR.is_object_schema(node):
            continue
        java_name = GR.type_name(raw_name)
        if java_name in emitted:
            continue
        emitted.add(java_name)
        outs[f"{SUB_POSTPROMPT}/{java_name}.java"] = _emit(
            SUB_POSTPROMPT, java_name, node.get("properties") or {}, schemas,
            f"post-prompt components/schemas {raw_name!r}")
    return outs


def _pascal_verb(verb: str) -> str:
    parts = [p for p in re.split(r"[._\-\s]", verb) if p]
    return "".join(w[:1].upper() + w[1:] for w in parts)


def _build_swaig_actions(psdk: Path) -> dict[str, str]:
    """swaig-response.yaml -> one ``<Action>`` class per action key whose value is an
    object-with-properties (bare object OR the object variant(s) of a oneOf). The
    FIRST object variant is ``<Verb>Action``; a second would be ``<Verb>Action2``."""
    spec_file = "swaig-response.yaml"
    spec = _load_yaml(psdk / "swaig-specs" / spec_file)
    actions = spec["components"]["schemas"]["SwaigAction"]["properties"]

    def _is_obj(s: object) -> bool:
        return isinstance(s, dict) and s.get("type") == "object" and bool(s.get("properties"))

    outs: dict[str, str] = {}
    emitted: set[str] = set()
    for verb in sorted(actions):
        schema = actions[verb]
        if not isinstance(schema, dict):
            continue
        branches = schema.get("oneOf") or ([schema] if _is_obj(schema) else [])
        obj_i = 0
        for b in branches:
            if not _is_obj(b):
                continue
            obj_i += 1
            action_name = _pascal_verb(verb) + "Action" + ("" if obj_i == 1 else str(obj_i))
            java_name = GR.type_name(action_name)
            if java_name in emitted:
                continue
            emitted.add(java_name)
            outs[f"{SUB_ACTIONS}/{java_name}.java"] = _emit(
                SUB_ACTIONS, java_name, b.get("properties") or {}, {},
                f"swaig-response action {verb!r} value object")
    return outs


def build_outputs(psdk: Path) -> dict[str, str]:
    specs_dir = psdk / "swaig-specs"
    if not specs_dir.is_dir():
        raise SystemExit(
            f"generate_swaig_payloads.py: {specs_dir} not found (need porting-sdk adjacency)"
        )
    outs: dict[str, str] = {}
    outs.update(_build_post_prompt(psdk))
    outs.update(_build_swaig_request(psdk))
    outs.update(_build_swaig_actions(psdk))
    for fn, formatted in GR.gjf_format_many(outs).items():
        outs[fn] = formatted
    return outs


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
            sys.stderr.write("GEN-FRESH FAIL: %d generated SWAIG-payload file(s) stale:\n" % len(stale))
            for s in stale:
                sys.stderr.write("  - %s\n" % s)
            return 1
        print("GEN-FRESH: generated SWAIG-payload files match porting-sdk/swaig-specs/*.yaml.")
        return 0

    out_dir.mkdir(parents=True, exist_ok=True)
    for fn, src in outs.items():
        p = out_dir / fn
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(src)
    print(f"generated {len(outs)} SWAIG-payload file(s) into {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
