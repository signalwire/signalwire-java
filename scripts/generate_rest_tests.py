#!/usr/bin/env python3
"""Generate the full-mock REST wire-test suite for signalwire-java.

This is the Java realisation of porting-sdk/REST_TEST_GENERATOR_RULES.md (the
portable REST *test* generator; reference:
generate_python_rest_types.py::generate_rest_tests, mirrors:
signalwire-ruby/scripts/generate_rest_tests.py + signalwire-php +
signalwire-go/cmd/generate-rest-tests + signalwire-typescript). For every REST
route the SDK actually implements it emits, into
src/test/java/com/signalwire/sdk/rest/generated/<Spec>GeneratedTest.java:

  - a SUCCESS test: call the real SDK method against the shared mock_signalwire
    harness (MockTest.newClient()), assert the mock journaled the expected
    (method, matched_route);
  - an ERROR test: arm a 500 for that route, assert the SDK raises RestError with
    getStatusCode() == 500 (and the journal recorded that status + route).

The assertion oracle is INDEPENDENT of the resource generator (RULES §1):
  - the (method, path) to call + the via method come from the route registry
    (RouteRegistry — captured from the REAL client) and the per-via call plan
    (RouteTestPlan — reflected from the real client), NOT re-walked here;
  - the matched_route to assert comes from the OpenAPI operationId
    (<spec_dir>.<operationId>) — the same value the mock derives its route table
    from. A generated test therefore catches SDK-vs-contract drift, not a
    generator self-snapshot.

Inputs joined by (METHOD, normalized-path) (RULES §2): the registry's deduped
routes (path params already {id}) × the spec operationIds (spec path normalized
the SAME way before the join). Routing collisions are resolved
longest-template-wins (RULES §7) so the asserted route is the one the mock
ACTUALLY journals (e.g. GET /rooms/{id} vs GET /rooms/{name}).

Call args are type-correct BY CONSTRUCTION (RULES §4/§6): RouteTestPlan reflects
each via method's parameter types off the live client and emits a Java literal of
the right kind (String→"x", int→1, Map→java.util.Map.of(), a closed
<Method>Request→<FQN>.builder().build()). The generated tests compile under the
same javac + spotless the FMT gate uses.

GEN-FRESH: `--check` reproduces the committed *GeneratedTest.java and exits
non-zero if any file differs. Resolves porting-sdk via $PORTING_SDK or sibling.

Usage:
    python3 scripts/generate_rest_tests.py           # (re)write the test files
    python3 scripts/generate_rest_tests.py --check   # GEN-FRESH: fail if stale
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover
    sys.stderr.write("generate_rest_tests.py requires PyYAML (pip install pyyaml)\n")
    raise


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
    raise SystemExit(
        "generate_rest_tests.py: porting-sdk not found (set $PORTING_SDK or clone adjacent)"
    )


def repo_root() -> Path:
    return Path(__file__).resolve().parents[1]


def gradlew() -> str:
    return str(repo_root() / "gradlew")


# ---------------------------------------------------------------------------
# google-java-format pass — byte-identical to the spotless FMT gate.
#
# The generated .java files are DO-NOT-EDIT and byte-compared by GEN-FRESH-TESTS,
# so the generator's output must ALREADY equal what `spotlessApply` produces —
# otherwise spotless would rewrite them and GEN-FRESH would report them stale.
# We run the emitted sources through the SAME google-java-format 1.22.0 jar
# spotless uses (resolved from the gradle cache), so both write and --check
# compare formatted-against-formatted. Mirrors scripts/generate_rest.py.
# ---------------------------------------------------------------------------

_GJF_ADD_EXPORTS = [
    "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
]
_GJF_VERSION = "1.22.0"  # must match the spotless googleJavaFormat() version in build.gradle
_GJF_CP: str | None = None


def _resolve_gjf_classpath() -> str:
    cache = Path.home() / ".gradle" / "caches" / "modules-2"
    gjf = sorted(cache.rglob(f"google-java-format-{_GJF_VERSION}.jar"))
    if not gjf:
        raise SystemExit(
            f"generate_rest_tests.py: google-java-format-{_GJF_VERSION}.jar not found under "
            f"{cache}; run ./gradlew build once so spotless resolves it into the cache."
        )
    guavas = [
        p for p in cache.rglob("guava-*.jar")
        if "sources" not in p.name and "android" not in p.name
    ]
    if not guavas:
        raise SystemExit("generate_rest_tests.py: guava jar not found in the gradle cache")
    guava = sorted(guavas, key=lambda p: p.name)[-1]
    return ":".join([str(gjf[0]), str(guava)])


def gjf_format_many(sources: dict[str, str]) -> dict[str, str]:
    """Format many .java sources in a SINGLE gjf JVM invocation, byte-identical to
    `spotlessApply`. Keys are opaque; values are raw java sources."""
    global _GJF_CP
    if not sources:
        return {}
    if _GJF_CP is None:
        _GJF_CP = _resolve_gjf_classpath()
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        idx_to_key: dict[str, str] = {}
        paths: list[str] = []
        for i, key in enumerate(sources):
            p = tmp / f"F{i}.java"
            p.write_text(sources[key])
            idx_to_key[str(p)] = key
            paths.append(str(p))
        proc = subprocess.run(
            ["java", *_GJF_ADD_EXPORTS, "-cp", _GJF_CP,
             "com.google.googlejavaformat.java.Main", "-i", *paths],
            capture_output=True, text=True,
        )
        if proc.returncode != 0:
            raise SystemExit(
                f"generate_rest_tests.py: batch google-java-format failed:\n{proc.stderr}")
        return {idx_to_key[p]: Path(p).read_text() for p in paths}


# ---------------------------------------------------------------------------
# 1. Capture from the real client (RULES §3) — run the committed Gradle tasks.
#    RouteRegistry: the SDK's deduped routes (via-merged, {id}-normalized).
#    RouteTestPlan: per-via call plan (chain, member, typed literal args).
# ---------------------------------------------------------------------------

def _run_gradle_json(task: str) -> dict:
    proc = subprocess.run(
        [gradlew(), "--no-daemon", "-q", task],
        cwd=str(repo_root()),
        env=dict(os.environ, SIGNALWIRE_LOG_MODE="off"),
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        sys.stderr.write(proc.stderr)
        raise SystemExit(f"gradle {task} exited {proc.returncode} — capture incomplete")
    out = proc.stdout
    # Any stray non-JSON line may precede the object; slice from the first '{'.
    i = out.find("{")
    if i > 0:
        out = out[i:]
    return json.loads(out)


def load_routes() -> list[dict]:
    reg = _run_gradle_json("routeRegistry")
    if reg.get("errors"):
        raise SystemExit(
            f"routeRegistry reported {len(reg['errors'])} capture error(s) — Set B incomplete"
        )
    return reg["routes"]


def load_plan() -> dict[str, list[dict]]:
    plan = _run_gradle_json("routeTestPlan")
    if plan.get("errors"):
        raise SystemExit(
            f"routeTestPlan reported {len(plan['errors'])} capture error(s) — plan incomplete"
        )
    # Index by via — a via may have several plan entries (list() vs list(Map)
    # overloads produce the same wire route); the join picks the entry whose
    # (method, path) matches the joined route, deterministically.
    idx: dict[str, list[dict]] = {}
    for e in plan["plan"]:
        idx.setdefault(e["via"], []).append(e)
    return idx


# ---------------------------------------------------------------------------
# 2. The join — registry routes × spec operationIds by (method, normalized-path).
# ---------------------------------------------------------------------------

_BRACE = re.compile(r"\{[^}]+\}")


def norm_params(p: str) -> str:
    """Every {param} → {id} (registry already does this; do it to the spec path
    so renamed params — {token_id}, {name} — line up)."""
    return _BRACE.sub("{id}", p)


def wire_key(p: str) -> str:
    """Every {param} → X: the wire-identical key used for collision ranking."""
    return _BRACE.sub("X", p)


def spec_prefix(doc: dict) -> str:
    url = ((doc.get("servers") or [{}])[0]).get("url", "")
    i = url.find("signalwire.com")
    return url[i + len("signalwire.com"):] if i >= 0 else ""


def spec_dirs_with_openapi(psdk: Path) -> list[str]:
    root = psdk / "rest-apis"
    out = [
        d.name
        for d in root.iterdir()
        if d.is_dir() and (d / "openapi.yaml").is_file()
    ]
    return sorted(out)


def build_join(routes: list[dict], psdk: Path, spec_dirs: list[str]) -> list[dict]:
    """Return one joined row per registry route that has a spec op AND a via.

    Row: {method, path, op_id (<spec>.<operationId>), via, spec}. The via is the
    registry's via[0] and the op_id is the longest-template collision winner the
    mock actually journals (RULES §7).
    """
    op_by: dict[str, str] = {}  # "METHOD normPath" -> <spec>.<operationId>
    wire_winner: dict[str, tuple[int, str]] = {}  # "METHOD wireKey" -> (len, route)
    verbs = ("get", "post", "put", "patch", "delete")

    for spec in spec_dirs:
        doc = yaml.safe_load((psdk / "rest-apis" / spec / "openapi.yaml").read_text())
        prefix = spec_prefix(doc)
        for path_key, body in (doc.get("paths") or {}).items():
            orig = prefix + path_key
            full = _BRACE.sub("{id}", orig)
            wk = _BRACE.sub("X", orig)
            for verb in verbs:
                op = body.get(verb)
                if not isinstance(op, dict):
                    continue
                op_id = op.get("operationId")
                if not op_id:
                    continue
                route = f"{spec}.{op_id}"
                op_by[f"{verb.upper()} {full}"] = route
                wkey = f"{verb.upper()} {wk}"
                cur = wire_winner.get(wkey)
                if cur is None or len(orig) > cur[0]:
                    wire_winner[wkey] = (len(orig), route)

    rows: list[dict] = []
    for r in routes:
        via_list = r.get("via") or []
        if not via_list:
            continue  # helper route with no via — skip
        method = r["method"]
        np = norm_params(r["path_template"])
        if f"{method} {np}" not in op_by:
            continue  # no spec op for this route — coverage finding, not a bug
        winner = wire_winner.get(f"{method} {wire_key(r['path_template'])}")
        if winner is None:
            continue
        op_id = winner[1]
        spec = op_id[: op_id.index(".")]
        rows.append({
            "method": method,
            "path": np,
            "op_id": op_id,
            "via": via_list[0],
            "spec": spec,
        })
    return rows


def pick_plan_entry(entries: list[dict], method: str, path: str) -> dict | None:
    """Choose the plan entry for a via that matches the joined route's (method,
    path); among matches pick deterministically (most args, then the lexically
    smallest joined-args string) so GEN-FRESH is stable."""
    matches = [e for e in entries if e["method"] == method and e["path"] == path]
    if not matches:
        # A via whose sole plan entry is a differently-shaped overload (rare);
        # fall back to any entry with the same method so the call still compiles.
        matches = [e for e in entries if e["method"] == method] or list(entries)
    if not matches:
        return None
    matches.sort(key=lambda e: (-len(e["args"]), " ".join(e["args"])))
    return matches[0]


# ---------------------------------------------------------------------------
# 3. Emit — one <Spec>GeneratedTest.java per spec namespace.
# ---------------------------------------------------------------------------

def pascal_spec(spec: str) -> str:
    """spec dir name → PascalCase class-name fragment (relay-rest → RelayRest)."""
    return "".join(part[:1].upper() + part[1:] for part in re.split(r"[-_]", spec) if part)


def slug(via: str) -> str:
    """The full via, slugified — stable for GEN-FRESH."""
    return re.sub(r"_+$", "", re.sub(r"[^A-Za-z0-9]+", "_", via))


def method_ident(slug_str: str) -> str:
    """A Java test-method identifier fragment from a slug (camelCase, stable)."""
    parts = [p for p in slug_str.split("_") if p]
    if not parts:
        return "route";
    head, *rest = parts
    head = head[:1].lower() + head[1:]
    return head + "".join(p[:1].upper() + p[1:] for p in rest)


def call_expr(entry: dict) -> str:
    """The literal Java call `client.ns().res().member(args)`."""
    chain = "".join(f"{seg}()." for seg in entry["chain"])
    args = ", ".join(entry["args"])
    return f"client.{chain}{entry['member']}({args})"


HEADER_TMPL = """/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */

// Code generated by scripts/generate_rest_tests.py; DO NOT EDIT.
//
// AUTO-GENERATED full-mock REST wire tests for the '{spec}' namespace — regenerate:
//   python3 scripts/generate_rest_tests.py
//
// Each route the SDK implements (captured from the real client by RouteRegistry +
// RouteTestPlan, joined to the spec operationId) gets a SUCCESS test (call it,
// assert method + matched_route on the mock journal) and an ERROR test (arm a 500,
// assert RestError with getStatusCode() == 500). The assertion oracle is the spec
// operationId — independent of the resource generator — so these catch
// SDK-vs-contract drift, not a generator self-snapshot. Shares the mock harness
// (MockTest).

package com.signalwire.sdk.rest.generated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.signalwire.sdk.rest.MockTest;
import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** AUTO-GENERATED full-mock REST wire tests for the '{spec}' namespace. */
class {cls} {{

  private RestClient client;
  private MockTest.Harness mock;

  @BeforeEach
  void setUp() {{
    MockTest.Bound bound = MockTest.newClient();
    this.client = bound.client;
    this.mock = bound.harness;
  }}

  // Dispatch the SDK call for a SUCCESS wire-test. The assertion oracle is the
  // JOURNAL — that the request reached the mock with the right (method,
  // matched_route) — NOT the deserialized response. A few list routes' spec
  // responses are a top-level JSON array (list_sip_endpoints / list_swml_scripts
  // / list_subscriber_addresses), which the Java SDK's Map-only return type
  // cannot deserialize; that surfaces as a CLIENT-SIDE parse RestError with
  // status 0 AFTER the request has already reached the mock (the journal records
  // it 200). We tolerate exactly that status-0 client-side artifact so the wire
  // assertion still holds; any real HTTP error status propagates and fails.
  private void dispatch(Runnable call) {{
    try {{
      call.run();
    }} catch (RestError e) {{
      if (e.getStatusCode() != 0) {{
        throw e;
      }}
    }}
  }}
"""


def emit_spec_file(spec: str, rows: list[dict]) -> str:
    cls = pascal_spec(spec) + "GeneratedTest"
    body = HEADER_TMPL.format(spec=spec, cls=cls)
    for r in rows:
        ident = r["_ident"]
        call = r["_call"]
        method = r["method"]
        op_id = r["op_id"]
        body += f"""
  @Test
  void {ident}Success() {{
    dispatch(() -> {call});
    MockTest.JournalEntry j = mock.last();
    assertEquals("{method}", j.method, "method for {op_id}");
    assertEquals("{op_id}", j.getMatchedRoute(), "matched_route for {op_id}");
  }}

  @Test
  void {ident}Error() {{
    mock.scenarioSet("{op_id}", 500, Map.of("error", "x"));
    RestError ex = assertThrows(RestError.class, () -> {call});
    assertEquals(500, ex.getStatusCode(), "status for {op_id}");
    MockTest.JournalEntry j = mock.last();
    assertEquals(Integer.valueOf(500), j.getResponseStatus(), "response_status for {op_id}");
    assertEquals("{op_id}", j.getMatchedRoute(), "matched_route for {op_id}");
  }}
"""
    body += "}\n"
    return body


# ---------------------------------------------------------------------------
# Driver.
# ---------------------------------------------------------------------------

def build_outputs(psdk: Path) -> tuple[dict[str, str], list[str], int]:
    """Return ({filename: source}, uncovered_vias, n_routes_covered)."""
    routes = load_routes()
    plan = load_plan()
    spec_dirs = spec_dirs_with_openapi(psdk)
    rows = build_join(routes, psdk, spec_dirs)

    by_spec: dict[str, list[dict]] = {}
    uncovered: list[str] = []
    covered_vias: set[str] = set()

    for row in rows:
        via = row["via"]
        entries = plan.get(via)
        if not entries:
            uncovered.append(f"{via} ({row['method']} {row['path']})")
            continue
        entry = pick_plan_entry(entries, row["method"], row["path"])
        if entry is None:
            uncovered.append(f"{via} ({row['method']} {row['path']})")
            continue
        row = dict(row)
        row["_call"] = call_expr(entry)
        row["_slug"] = slug(via)
        by_spec.setdefault(row["spec"], []).append(row)
        covered_vias.add(via)

    outs: dict[str, str] = {}
    for spec in sorted(by_spec):
        srows = by_spec[spec]
        # Deterministic ordering: sort by via.
        srows.sort(key=lambda r: r["via"])
        # Ensure unique test-method identifiers within the file.
        used: set[str] = set()
        for r in srows:
            ident = method_ident(r["_slug"])
            base = ident
            k = 2
            while ident in used:
                ident = f"{base}{k}"
                k += 1
            used.add(ident)
            r["_ident"] = ident
        fn = f"{pascal_spec(spec)}GeneratedTest.java"
        outs[fn] = emit_spec_file(spec, srows)

    # Format through the same gjf 1.22.0 the spotless FMT gate uses, so both the
    # write path and --check compare formatted-against-formatted.
    outs = gjf_format_many(outs)
    return outs, uncovered, len(covered_vias)


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--check", action="store_true", help="GEN-FRESH: exit non-zero if stale")
    ap.add_argument("--out", default="", help="scratch: emit into this dir")
    args = ap.parse_args(argv)

    psdk = resolve_porting_sdk()
    outs, uncovered, n_covered = build_outputs(psdk)

    default_dir = (
        repo_root()
        / "src"
        / "test"
        / "java"
        / "com"
        / "signalwire"
        / "sdk"
        / "rest"
        / "generated"
    )
    out_dir = Path(args.out) if args.out else default_dir

    if uncovered:
        sys.stderr.write(
            f"\nUNCOVERED ({len(uncovered)} joined route(s) with no reflectable via plan):\n"
        )
        for u in uncovered:
            sys.stderr.write(f"  - {u}\n")

    if args.check:
        stale = []
        for fn, src in outs.items():
            p = out_dir / fn
            if not p.is_file() or p.read_text() != src:
                stale.append(str(p))
        expected = set(outs.keys())
        if out_dir.is_dir():
            for p in sorted(out_dir.glob("*.java")):
                if p.name not in expected:
                    stale.append(f"{p} (leftover — not in generator output)")
        if stale:
            sys.stderr.write("GEN-FRESH FAIL: %d generated REST test file(s) stale:\n" % len(stale))
            for s in stale:
                sys.stderr.write(f"  - {s}\n")
            return 1
        total = sum(src.count("@Test") for src in outs.values())
        print(
            f"GEN-FRESH: {len(outs)} generated REST test file(s) up to date "
            f"({total} tests, {n_covered} routes)."
        )
        return 0

    out_dir.mkdir(parents=True, exist_ok=True)
    # Remove any stale files no longer emitted.
    expected = set(outs.keys())
    for p in sorted(out_dir.glob("*.java")):
        if p.name not in expected:
            p.unlink()
    for fn, src in outs.items():
        (out_dir / fn).write_text(src)
    total = sum(src.count("@Test") for src in outs.values())
    print(
        f"generated {len(outs)} REST test file(s) into {out_dir} "
        f"({total} tests across {len(outs)} namespaces, {n_covered} routes covered)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
