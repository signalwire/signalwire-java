/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.tools;

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.namespaces.generated.Calling;
import com.signalwire.sdk.rest.namespaces.generated.DatasphereDocuments;
import java.util.List;
import java.util.Map;

/**
 * DocWireRunner — the DOC-WIRE fixture runner for signalwire-java.
 *
 * <p>The DOC-WIRE gate (porting-sdk {@code scripts/doc_wire.py}) spawns {@code mock_signalwire} in
 * flag mode, exports {@code MOCK_SIGNALWIRE_PORT}, then runs THIS program; it then reads the mock
 * journal and fails on any {@code wire_violations}. This program's only job is to DRIVE the
 * documented REST calls against the mock so the mock journals what the documented fixtures actually
 * put on the wire — a doc lie like {@code area_code=} (spec {@code areacode}) shows up as a
 * journaled violation and fails the gate.
 *
 * <p>It replays the wire-bearing REST fixtures shown in {@code README.md}, {@code
 * examples/QuickstartRest.java}, {@code rest/docs/*}, and {@code rest/examples/*} — the exact
 * argument shapes the docs teach. The blocking agent/relay quickstarts are covered by EXAMPLES-RUN,
 * not here.
 *
 * <p>Run via the {@code docWireRun} Gradle task:
 *
 * <pre>
 *   ./gradlew --no-daemon -q docWireRun
 * </pre>
 */
final class DocWireRunner {

  private DocWireRunner() {}

  public static void main(String[] args) {
    String port = System.getenv("MOCK_SIGNALWIRE_PORT");
    if (port == null || port.isEmpty()) {
      System.err.println("DocWireRunner: MOCK_SIGNALWIRE_PORT not set");
      System.exit(2);
      return;
    }
    String baseUrl = "http://127.0.0.1:" + port;

    RestClient client = RestClient.withBaseUrl(baseUrl, "test_proj", "test_tok");

    String callId = "call-doc-wire";

    // --- README + examples/QuickstartRest.java (region: quickstart) ----------
    client
        .fabric()
        .aiAgents()
        .create(Map.of("name", "Support Bot", "prompt", Map.of("text", "You are helpful.")));
    client
        .calling()
        .play(
            callId,
            Calling.PlayRequest.builder()
                .play(List.of(Map.of("type", "tts", "params", Map.of("text", "Hello!"))))
                .build());
    client.phoneNumbers().search(Map.of("areacode", "512"));
    client
        .datasphere()
        .documents()
        .search(DatasphereDocuments.SearchRequest.builder().queryString("billing policy").build());

    // --- rest/docs/namespaces.md phone-number search -------------------------
    client.phoneNumbers().search(Map.of("areacode", "512", "number_type", "local"));

    // --- rest/docs/calling.md play (nested params:{text}) --------------------
    client
        .calling()
        .play(
            callId,
            Calling.PlayRequest.builder()
                .play(List.of(Map.of("type", "tts", "params", Map.of("text", "Hello!"))))
                .volume(5.0)
                .build());

    // --- rest/examples/RestCallingPlayAndRecord.java + RestCallingIvrAndAi.java
    client
        .calling()
        .play(
            callId,
            Calling.PlayRequest.builder()
                .play(
                    List.of(
                        Map.of(
                            "type",
                            "tts",
                            "params",
                            Map.of("text", "Please leave a message after the beep."))))
                .build());

    System.out.println("DocWireRunner: replayed documented REST fixtures against the mock");
  }
}
