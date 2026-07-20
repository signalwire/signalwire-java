/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * PaginationDump — the Java port's PAGINATION-CORPUS dump program for the cross-port pagination
 * behavioral differ (porting-sdk/scripts/diff_port_pagination.py).
 *
 * <p>A shape-only PAGINATION-WIRED check proves a paginator TYPE is referenced by some list method;
 * it cannot express the paginator's RUNTIME page-walk contract. This program drives the SAME corpus
 * the differ's Python oracle runs (porting-sdk/scripts/pagination_corpus.py) against the live
 * {@code mock_signalwire} harness ({@link MockTest}) by arming each fixture's page bodies FIFO on
 * the list endpoint (including {@code links.next}), walking the real {@link PaginatedIterator}, and
 * emitting the deterministic per-fixture classification:
 *
 * <pre>
 *   empty_page_with_next   -> {continued_past_empty: bool, items_seen: int}
 *   repeating_cursor_guard -> {loop_guarded: bool, hung: bool}
 *   exhaustion             -> {terminated: bool, total_items: int}
 * </pre>
 *
 * <p>A paginator that STOPS on the empty page reds {@code empty_page_with_next} ({@code
 * continued_past_empty:false, items_seen:0}); one with no cycle guard HANGS the repeating-cursor
 * walk, which the bounded watchdog reports as {@code hung:true} (never a silent green). Prints ONE
 * JSON object mapping fixture-id -&gt; classification to stdout.
 *
 * <p>Run via the {@code paginationDump} Gradle task.
 */
final class PaginationDump {

  private PaginationDump() {}

  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  /** The list endpoint the corpus arms (matches pagination_corpus.py ENDPOINT_ID / LIST_PATH). */
  private static final String LIST_PATH = "/fabric/addresses";

  private static final String ENDPOINT_ID = "fabric.list_fabric_addresses";

  /**
   * Bounded watchdog for the repeating-cursor walk — a hang is a hard fail, not an infinite loop.
   */
  private static final long WALK_DEADLINE_MS = 10_000;

  /** Stable next-cursor URL builder — byte-identical to pagination_corpus.py `_next`. */
  private static String next(String tok) {
    return "http://mock.test/api/fabric/addresses?page_token=" + tok;
  }

  private static Map<String, Object> page(List<Map<String, Object>> data, String nextUrl) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("data", data);
    Map<String, Object> links = new LinkedHashMap<>();
    if (nextUrl != null) {
      links.put("next", nextUrl);
    }
    body.put("links", links);
    return body;
  }

  private static Map<String, Object> item(String id) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id);
    return m;
  }

  public static void main(String[] args) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("empty_page_with_next", runEmptyPageWithNext());
    out.put("repeating_cursor_guard", runRepeatingCursor());
    out.put("exhaustion", runExhaustion());
    System.out.println(GSON.toJson(out));
  }

  /** Arm the fixture's pages FIFO, return an SDK HttpClient bound to the scoped mock client. */
  private static HttpClient armAndClient(List<Map<String, Object>> pages) {
    MockTest.Bound bound = MockTest.newClient();
    bound.harness.reset();
    for (Map<String, Object> body : pages) {
      bound.harness.scenarioSet(ENDPOINT_ID, 200, body);
    }
    return bound.client.getHttpClient();
  }

  private static List<String> collectIds(HttpClient http) {
    List<String> ids = new ArrayList<>();
    for (Map<String, Object> row : new PaginatedIterator(http, LIST_PATH)) {
      Object id = row.get("id");
      ids.add(id == null ? null : id.toString());
    }
    return ids;
  }

  private static Map<String, Object> runEmptyPageWithNext() {
    HttpClient http =
        armAndClient(
            List.of(
                page(List.of(), next("EP_page2")), page(List.of(item("found-after-empty")), null)));
    List<String> ids = collectIds(http);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("continued_past_empty", ids.equals(List.of("found-after-empty")));
    m.put("items_seen", ids.size());
    return m;
  }

  private static Map<String, Object> runRepeatingCursor() {
    HttpClient http =
        armAndClient(
            List.of(
                page(List.of(item("loop-1")), next("LOOP")),
                page(List.of(item("loop-2")), next("LOOP"))));
    // Walk under a bounded watchdog: a paginator with no cycle guard re-fetches the LOOP cursor
    // forever, so the walk must be interruptible and reported as hung rather than blocking the JVM.
    ExecutorService exec =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "pagination-walk");
              t.setDaemon(true);
              return t;
            });
    Future<List<String>> f = exec.submit(() -> collectIds(http));
    Map<String, Object> m = new LinkedHashMap<>();
    try {
      List<String> ids = f.get(WALK_DEADLINE_MS, TimeUnit.MILLISECONDS);
      m.put("loop_guarded", ids.equals(List.of("loop-1", "loop-2")));
      m.put("hung", false);
    } catch (java.util.concurrent.TimeoutException te) {
      f.cancel(true);
      m.put("loop_guarded", false);
      m.put("hung", true);
    } catch (Exception e) {
      m.put("loop_guarded", false);
      m.put("hung", false);
    } finally {
      exec.shutdownNow();
    }
    return m;
  }

  private static Map<String, Object> runExhaustion() {
    HttpClient http =
        armAndClient(
            List.of(
                page(List.of(item("x-1"), item("x-2")), next("EX_page2")),
                page(List.of(item("x-3"), item("x-4")), next("EX_page3")),
                page(List.of(item("x-5")), null)));
    List<String> ids = collectIds(http);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("terminated", ids.equals(List.of("x-1", "x-2", "x-3", "x-4", "x-5")));
    m.put("total_items", ids.size());
    return m;
  }
}
