/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Full success+error REST coverage for the {@code datasphere.*} canonical spec group (9 routes).
 *
 * <p>For every datasphere route this exercises BOTH a success (2xx) call — asserting the response
 * body, the journalled {@code method}/{@code path}, and {@code matched_route} == the canonical
 * endpoint id — AND an error path: an armed {@link MockTest.Harness#scenarioSet} override
 * (404/422/500) that must surface as a {@link RestError} carrying the right status code, with the
 * journal recording the same {@code matched_route} and {@code response_status}.
 *
 * <p>Mirrors the DRY-helper idiom of {@code FabricCoverageMockTest}. {@code update_document} is
 * PATCH (the SDK's {@code DatasphereDocuments} opts into {@code UpdateMethod.PATCH}); {@code
 * search_documents} is POST. No accepted gaps — all 9 routes are reachable.
 */
class DatasphereCoverageMockTest {

  private RestClient client;
  private MockTest.Harness mock;

  @BeforeEach
  void setUp() {
    MockTest.Bound bound = MockTest.newClient();
    this.client = bound.client;
    this.mock = bound.harness;
  }

  private static Map<String, Object> kw(Object... entries) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      m.put((String) entries[i], entries[i + 1]);
    }
    return m;
  }

  // ── DRY helpers (each RETURNS a value asserted by the @Test body) ────

  /** Assert a successful journalled call; returns the matched route for the caller to re-assert. */
  private String okJournal(String expectedMethod, String expectedPath, String expectedRoute) {
    MockTest.JournalEntry j = mock.last();
    assertEquals(expectedMethod, j.method, "method for " + expectedRoute);
    assertEquals(expectedPath, j.path, "path for " + expectedRoute);
    assertEquals(
        expectedRoute, j.getMatchedRoute(), "unexpected matched_route: " + j.getMatchedRoute());
    return j.getMatchedRoute();
  }

  /** Arm a one-shot error, run the call, assert RestError status; returns the status code seen. */
  private int errCall(String routeId, int status, Executable call) {
    mock.scenarioSet(routeId, status, Map.of("error", "x"));
    RestError ex = assertThrows(RestError.class, call);
    MockTest.JournalEntry j = mock.last();
    assertEquals(Integer.valueOf(status), j.getResponseStatus(), "response_status for " + routeId);
    assertEquals(routeId, j.getMatchedRoute(), "matched_route for " + routeId);
    return ex.getStatusCode();
  }

  // ════════════════════════════════════════════════════════════════════
  // documents collection: list / create / search
  // ════════════════════════════════════════════════════════════════════

  @Test
  void listSuccess() {
    Map<String, Object> body = client.datasphere().documents().list();
    assertNotNull(body);
    assertTrue(body.containsKey("data"));
    assertEquals(
        "datasphere.list_documents",
        okJournal("GET", "/api/datasphere/documents", "datasphere.list_documents"));
  }

  @Test
  void listError() {
    assertEquals(
        500,
        errCall("datasphere.list_documents", 500, () -> client.datasphere().documents().list()));
  }

  @Test
  void createSuccess() {
    Map<String, Object> body = client.datasphere().documents().create(kw("filename", "a.pdf"));
    assertNotNull(body);
    assertEquals(
        "datasphere.create_document",
        okJournal("POST", "/api/datasphere/documents", "datasphere.create_document"));
  }

  @Test
  void createError() {
    assertEquals(
        422,
        errCall(
            "datasphere.create_document",
            422,
            () -> client.datasphere().documents().create(kw("filename", "a.pdf"))));
  }

  @Test
  void searchSuccess() {
    Map<String, Object> body = client.datasphere().documents().search(kw("query", "hello"));
    assertNotNull(body);
    assertEquals(
        "datasphere.search_documents",
        okJournal("POST", "/api/datasphere/documents/search", "datasphere.search_documents"));
  }

  @Test
  void searchError() {
    assertEquals(
        422,
        errCall(
            "datasphere.search_documents",
            422,
            () -> client.datasphere().documents().search(kw("query", "hello"))));
  }

  // ════════════════════════════════════════════════════════════════════
  // per-document: get / update (PATCH) / delete
  // ════════════════════════════════════════════════════════════════════

  @Test
  void getSuccess() {
    Map<String, Object> body = client.datasphere().documents().get("doc-1");
    assertNotNull(body);
    assertEquals(
        "datasphere.get_document",
        okJournal("GET", "/api/datasphere/documents/doc-1", "datasphere.get_document"));
  }

  @Test
  void getError() {
    assertEquals(
        404,
        errCall(
            "datasphere.get_document", 404, () -> client.datasphere().documents().get("missing")));
  }

  @Test
  void updateSuccess() {
    Map<String, Object> body = client.datasphere().documents().update("doc-1", kw("tags", "x"));
    assertNotNull(body);
    assertEquals(
        "datasphere.update_document",
        okJournal("PATCH", "/api/datasphere/documents/doc-1", "datasphere.update_document"));
  }

  @Test
  void updateError() {
    assertEquals(
        404,
        errCall(
            "datasphere.update_document",
            404,
            () -> client.datasphere().documents().update("missing", kw("tags", "x"))));
  }

  @Test
  void deleteSuccess() {
    Map<String, Object> body = client.datasphere().documents().delete("doc-1");
    assertNotNull(body);
    assertEquals(
        "datasphere.delete_document",
        okJournal("DELETE", "/api/datasphere/documents/doc-1", "datasphere.delete_document"));
  }

  @Test
  void deleteError() {
    assertEquals(
        404,
        errCall(
            "datasphere.delete_document",
            404,
            () -> client.datasphere().documents().delete("missing")));
  }

  // ════════════════════════════════════════════════════════════════════
  // chunks: listChunks / getChunk / deleteChunk
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("datasphere document chunks")
  class Chunks {

    @Test
    void listChunksSuccess() {
      Map<String, Object> body = client.datasphere().documents().listChunks("doc-1");
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "datasphere.list_document_chunks",
          okJournal(
              "GET", "/api/datasphere/documents/doc-1/chunks", "datasphere.list_document_chunks"));
    }

    @Test
    void listChunksError() {
      assertEquals(
          500,
          errCall(
              "datasphere.list_document_chunks",
              500,
              () -> client.datasphere().documents().listChunks("doc-1")));
    }

    @Test
    void getChunkSuccess() {
      Map<String, Object> body = client.datasphere().documents().getChunk("doc-1", "chunk-1");
      assertNotNull(body);
      assertEquals(
          "datasphere.get_document_chunk",
          okJournal(
              "GET",
              "/api/datasphere/documents/doc-1/chunks/chunk-1",
              "datasphere.get_document_chunk"));
    }

    @Test
    void getChunkError() {
      assertEquals(
          404,
          errCall(
              "datasphere.get_document_chunk",
              404,
              () -> client.datasphere().documents().getChunk("doc-1", "missing")));
    }

    @Test
    void deleteChunkSuccess() {
      Map<String, Object> body = client.datasphere().documents().deleteChunk("doc-1", "chunk-1");
      assertNotNull(body);
      assertEquals(
          "datasphere.delete_document_chunk",
          okJournal(
              "DELETE",
              "/api/datasphere/documents/doc-1/chunks/chunk-1",
              "datasphere.delete_document_chunk"));
    }

    @Test
    void deleteChunkError() {
      assertEquals(
          404,
          errCall(
              "datasphere.delete_document_chunk",
              404,
              () -> client.datasphere().documents().deleteChunk("doc-1", "missing")));
    }
  }
}
