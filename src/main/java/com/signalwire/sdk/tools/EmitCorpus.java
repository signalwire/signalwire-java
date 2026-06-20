/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.signalwire.sdk.swaig.FunctionResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * EmitCorpus — the Java port's EMISSION-DUMP program for the cross-port emission differ
 * (porting-sdk/scripts/diff_port_emission.py).
 *
 * <p>It builds the shared {@code FunctionResult} corpus (porting-sdk/scripts/emission_corpus.py —
 * the single source of truth) using the Java SDK's native {@link FunctionResult} API, serialises
 * each entry the same way the SDK serialises on the wire ({@link FunctionResult#toMap()}), and
 * prints ONE JSON object mapping
 *
 * <pre>
 *   corpus-id -&gt; emission
 * </pre>
 *
 * to stdout. The differ runs this program, parses that object, and byte-compares each entry against
 * Python's {@code to_dict()}. See the "per-port dump contract" in the differ's {@code --help} and
 * porting-sdk/IDIOM_PASS_JOURNAL.md §4 Tier-0. This mirrors Go's {@code cmd/emit-corpus/main.go}.
 *
 * <p>CONTRACT (why this file looks the way it does):
 *
 * <ul>
 *   <li>Every corpus id in {@code emission_corpus.corpus_ids()} MUST appear here exactly once (the
 *       differ rejects an id-set mismatch as a setup error — a skewed set would mask real diffs).
 *       When the shared corpus grows, add the new id here.
 *   <li>The argument VALUES are the WIRE values (plain strings/numbers/bools/maps). Where the Java
 *       API types a closed set ({@code RecordFormat}, {@code RecordDirection}, {@code
 *       TapDirection}, {@code Codec}) the bare-string overload is exercised so the emitted SWML is
 *       the wire value.
 *   <li>Only stdout carries the JSON object; nothing else is printed there (logs/errors go to
 *       stderr).
 * </ul>
 *
 * <p>Run from the built JAR (the {@code emitCorpus} Gradle task wires the classpath):
 *
 * <pre>
 *   ./gradlew --no-daemon -q emitCorpus
 * </pre>
 */
// Package-private: this is the emission-dump tool the EMISSION gate runs via
// the `emitCorpus` Gradle task (a main() entry point — no `public` needed to
// launch it), NOT a public SDK API. Keeping it non-public excludes it from the
// enumerated public surface, matching how every other port keeps its
// emit-corpus tool out of the surface (separate tools/ / cmd/ / examples/ dir).
final class EmitCorpus {

  private EmitCorpus() {}

  /**
   * The Python default {@code ai_response} for {@code pay()} — pinned so the full-arity pay
   * emission is deterministic (mirrors the corpus).
   */
  private static final String PAY_AI_RESPONSE =
      "The payment status is ${pay_result}, do not mention anything else about "
          + "collecting payment if successful.";

  /** entry pairs a stable corpus id with the FunctionResult it produces. */
  private record Entry(String id, Supplier<FunctionResult> build) {}

  /** fr is a tiny constructor helper: new FunctionResult(response). */
  private static FunctionResult fr(String response) {
    return new FunctionResult(response);
  }

  /** Ordered list literal helper (preserves order; allows nulls unlike List.of). */
  @SafeVarargs
  private static <T> List<T> list(T... items) {
    return new ArrayList<>(Arrays.asList(items));
  }

  /** Ordered map helper that preserves insertion order (Python dict order). */
  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  /**
   * The Java-native mirror of porting-sdk/scripts/emission_corpus.py. The ids and the resulting
   * emission must match the Python oracle exactly (modulo the whole-float artifact the differ
   * normalises: Python {@code 44.0} == Java {@code 44.0} both fold to {@code 44}).
   */
  private static List<Entry> corpus() {
    List<Entry> c = new ArrayList<>();

    // ---- envelope edge cases (toMap shape) ----------------------------------
    c.add(new Entry("envelope.empty", () -> fr("")));
    c.add(new Entry("envelope.response_only", () -> fr("Hello, world!")));
    c.add(new Entry("envelope.post_process_no_action", () -> fr("hi").setPostProcess(true)));
    c.add(new Entry("envelope.action_only", () -> fr("").hangup()));
    c.add(
        new Entry(
            "envelope.post_process_with_action",
            () -> fr("Transferring").setPostProcess(true).hangup()));
    c.add(new Entry("envelope.response_and_action", () -> fr("Goodbye").hangup()));

    // ---- connect ------------------------------------------------------------
    c.add(new Entry("connect.final_true", () -> fr("").connect("+15551234567", true, null)));
    c.add(new Entry("connect.final_false", () -> fr("").connect("+15551234567", false, null)));
    c.add(
        new Entry(
            "connect.from_addr",
            () -> fr("").connect("support@example.com", false, "+15559876543")));

    // ---- swml_transfer ------------------------------------------------------
    c.add(
        new Entry(
            "swml_transfer.default",
            () -> fr("").swmlTransfer("https://dest.example.com/swml", "Goodbye!", true)));
    c.add(
        new Entry(
            "swml_transfer.final_false",
            () ->
                fr("")
                    .swmlTransfer(
                        "https://dest.example.com/swml",
                        "Welcome back. How else can I help?",
                        false)));

    // ---- simple call-control actions ----------------------------------------
    c.add(new Entry("hangup", () -> fr("").hangup()));
    c.add(new Entry("hold.default", () -> fr("").hold(300)));
    c.add(new Entry("hold.value", () -> fr("").hold(120)));
    c.add(new Entry("hold.clamp_high", () -> fr("").hold(5000)));
    c.add(new Entry("hold.clamp_low", () -> fr("").hold(-5)));
    c.add(new Entry("stop", () -> fr("").stop()));
    c.add(new Entry("say", () -> fr("").say("Please hold while I connect you.")));

    // ---- wait_for_user (each branch) ---------------------------------------
    c.add(new Entry("wait_for_user.default", () -> fr("").waitForUser(null, null, false)));
    c.add(new Entry("wait_for_user.answer_first", () -> fr("").waitForUser(null, null, true)));
    c.add(new Entry("wait_for_user.timeout", () -> fr("").waitForUser(null, 30, false)));
    c.add(new Entry("wait_for_user.enabled_true", () -> fr("").waitForUser(true, null, false)));
    c.add(new Entry("wait_for_user.enabled_false", () -> fr("").waitForUser(false, null, false)));

    // ---- global data / metadata --------------------------------------------
    c.add(
        new Entry(
            "set_global_data",
            () -> fr("").updateGlobalData(map("plan", "premium", "chips", 1000))));
    c.add(
        new Entry("unset_global_data.list", () -> fr("").removeGlobalData(list("plan", "chips"))));
    c.add(new Entry("unset_global_data.str", () -> fr("").removeGlobalData("plan")));
    c.add(new Entry("set_metadata", () -> fr("").setMetadata(map("token", "abc", "count", 3))));
    c.add(new Entry("unset_metadata.list", () -> fr("").removeMetadata(list("token", "count"))));
    c.add(new Entry("unset_metadata.str", () -> fr("").removeMetadata("token")));

    // ---- swml_user_event ----------------------------------------------------
    c.add(
        new Entry(
            "swml_user_event",
            () ->
                fr("")
                    .swmlUserEvent(
                        map(
                            "type",
                            "cards_dealt",
                            "player_hand",
                            list("AS", "KH"),
                            "player_score",
                            21))));

    // ---- step / context changes --------------------------------------------
    c.add(new Entry("change_step", () -> fr("").swmlChangeStep("collect_payment")));
    c.add(new Entry("change_context", () -> fr("").swmlChangeContext("billing")));

    // ---- switch_context (simple-string vs object branches) -----------------
    c.add(
        new Entry(
            "switch_context.simple",
            () -> fr("").switchContext("You are now a billing agent.", null, false, false)));
    c.add(
        new Entry(
            "switch_context.object",
            () -> fr("").switchContext("New system prompt", "User said something", true, false)));
    c.add(
        new Entry(
            "switch_context.full_reset",
            () -> fr("").switchContext("Reset prompt", null, false, true)));

    // ---- background file play/stop -----------------------------------------
    c.add(new Entry("playback_bg.simple", () -> fr("").playBackgroundFile("music.mp3", false)));
    c.add(new Entry("playback_bg.wait", () -> fr("").playBackgroundFile("music.mp3", true)));
    c.add(new Entry("stop_playback_bg", () -> fr("").stopBackgroundFile()));

    // ---- join_room / sip_refer ---------------------------------------------
    c.add(new Entry("join_room", () -> fr("").joinRoom("team-standup")));
    c.add(new Entry("sip_refer", () -> fr("").sipRefer("sip:agent@example.com")));

    // ---- send_sms -----------------------------------------------------------
    c.add(
        new Entry(
            "send_sms.body",
            () ->
                fr("")
                    .sendSms(
                        "+15551112222",
                        "+15553334444",
                        "Your appointment is confirmed.",
                        null,
                        null,
                        null)));
    c.add(
        new Entry(
            "send_sms.full",
            () ->
                fr("")
                    .sendSms(
                        "+15551112222",
                        "+15553334444",
                        "See attached.",
                        list("https://ex.com/a.jpg"),
                        list("receipt", "vip"),
                        "us")));

    // ---- pay (full + helper-shaped prompts/parameters) ---------------------
    c.add(
        new Entry(
            "pay.minimal",
            () -> fr("").pay("https://pay.example.com/connector", "dtmf", null, 5, 1)));
    c.add(
        new Entry(
            "pay.full",
            () -> {
              List<Map<String, String>> parameters = list(map2("name", "order_id", "value", "42"));
              List<Map<String, Object>> prompts =
                  list(
                      map(
                          "for", "payment-card-number",
                          "actions", list(map("type", "Say", "phrase", "Enter your card number")),
                          "card_type", "visa amex"));
              return fr("")
                  .pay(
                      "https://pay.example.com/connector",
                      "dtmf",
                      "https://ex.com/status",
                      "credit-card",
                      7,
                      2,
                      false,
                      "90210",
                      5,
                      "one-time",
                      "9.99",
                      "usd",
                      "en-US",
                      "woman",
                      "Order 42",
                      "visa amex",
                      parameters,
                      prompts,
                      PAY_AI_RESPONSE);
            }));
    c.add(
        new Entry(
            "pay.postal_bool",
            () ->
                fr("")
                    .pay(
                        "https://pay.example.com/connector",
                        "dtmf",
                        null,
                        "credit-card",
                        5,
                        1,
                        true,
                        Boolean.TRUE,
                        0,
                        "reusable",
                        null,
                        "usd",
                        "en-US",
                        "woman",
                        null,
                        "visa mastercard amex",
                        null,
                        null,
                        PAY_AI_RESPONSE)));

    // ---- record_call (incl. mp4 + each direction) --------------------------
    c.add(new Entry("record_call.defaults", () -> fr("").recordCall()));
    c.add(new Entry("record_call.wav_speak", () -> fr("").recordCall(null, false, "wav", "speak")));
    c.add(
        new Entry("record_call.mp3_listen", () -> fr("").recordCall(null, false, "mp3", "listen")));
    c.add(new Entry("record_call.mp4_both", () -> fr("").recordCall(null, false, "mp4", "both")));
    c.add(
        new Entry(
            "record_call.full",
            () ->
                fr("")
                    .recordCall(
                        "rec1",
                        true,
                        "mp3",
                        "both",
                        "#",
                        true,
                        30.0,
                        5.0,
                        3.0,
                        120.0,
                        "https://ex.com/rec")));
    c.add(new Entry("stop_record_call.bare", () -> fr("").stopRecordCall()));
    c.add(new Entry("stop_record_call.id", () -> fr("").stopRecordCall("rec1")));

    // ---- tap (each direction / codec) --------------------------------------
    c.add(
        new Entry(
            "tap.defaults",
            () -> fr("").tap("rtp://10.0.0.1:5004", null, "both", "PCMU", 20, null)));
    c.add(
        new Entry(
            "tap.speak_pcma",
            () -> fr("").tap("ws://ex.com/tap", null, "speak", "PCMA", 20, null)));
    c.add(
        new Entry(
            "tap.hear_pcmu", () -> fr("").tap("wss://ex.com/tap", null, "hear", "PCMU", 20, null)));
    c.add(
        new Entry(
            "tap.both_full",
            () ->
                fr("")
                    .tap(
                        "rtp://10.0.0.1:5004",
                        "tap1",
                        "both",
                        "PCMA",
                        40,
                        "https://ex.com/tapstatus")));
    c.add(new Entry("stop_tap.bare", () -> fr("").stopTap()));
    c.add(new Entry("stop_tap.id", () -> fr("").stopTap("tap1")));

    // ---- join_conference (simple + full) -----------------------------------
    c.add(new Entry("join_conference.simple", () -> fr("").joinConference("sales-floor")));
    c.add(
        new Entry(
            "join_conference.full",
            () ->
                fr("")
                    .joinConference(
                        "sales-floor",
                        true,
                        "onEnter",
                        false,
                        true,
                        "https://ex.com/hold",
                        50,
                        "record-from-start",
                        "us-east",
                        "do-not-trim",
                        "call-123",
                        "start end join leave",
                        "https://ex.com/cb",
                        "GET",
                        "https://ex.com/rcb",
                        "GET",
                        "in-progress completed",
                        null)));

    // ---- execute_rpc + the three rpc helpers -------------------------------
    c.add(new Entry("execute_rpc.minimal", () -> fr("").executeRpc("ai_unhold", null, null, null)));
    c.add(
        new Entry(
            "execute_rpc.full",
            () ->
                fr("")
                    .executeRpc(
                        "ai_message",
                        map("role", "system", "message_text", "Hello"),
                        "call-abc",
                        "node-1")));
    c.add(
        new Entry(
            "rpc_dial",
            () -> fr("").rpcDial("+15551234567", "+15559876543", "https://ex.com/call-agent")));
    c.add(
        new Entry(
            "rpc_ai_message", () -> fr("").rpcAiMessage("call-abc", "Please take a message.")));
    c.add(new Entry("rpc_ai_unhold", () -> fr("").rpcAiUnhold("call-abc")));

    // ---- simulate_user_input -----------------------------------------------
    c.add(
        new Entry(
            "simulate_user_input", () -> fr("").simulateUserInput("I'd like to pay my bill.")));

    // ---- dynamic hints ------------------------------------------------------
    c.add(
        new Entry(
            "add_dynamic_hints",
            () ->
                fr("")
                    .addDynamicHints(
                        list(
                            "Cabby",
                            map("pattern", "cab bee", "replace", "Cabby", "ignore_case", true)))));
    c.add(new Entry("clear_dynamic_hints", () -> fr("").clearDynamicHints()));

    // ---- toggle_functions / functions-on-timeout ---------------------------
    c.add(
        new Entry(
            "toggle_functions",
            () ->
                fr("")
                    .toggleFunctions(
                        list(
                            map("function", "transfer", "active", false),
                            map("function", "lookup", "active", true)))));
    c.add(
        new Entry(
            "functions_on_speaker_timeout.true", () -> fr("").enableFunctionsOnTimeout(true)));
    c.add(
        new Entry(
            "functions_on_speaker_timeout.false", () -> fr("").enableFunctionsOnTimeout(false)));

    // ---- extensive_data -----------------------------------------------------
    c.add(new Entry("extensive_data.true", () -> fr("").enableExtensiveData(true)));
    c.add(new Entry("extensive_data.false", () -> fr("").enableExtensiveData(false)));

    // ---- replace_in_history (str + bool branches) --------------------------
    c.add(new Entry("replace_in_history.bool", () -> fr("").replaceInHistory(true)));
    c.add(
        new Entry(
            "replace_in_history.str", () -> fr("").replaceInHistory("Summarized the order.")));

    // ---- settings -----------------------------------------------------------
    c.add(
        new Entry(
            "settings",
            () -> fr("").updateSettings(map("temperature", 0.7, "max-tokens", 256, "top-p", 0.9))));

    // ---- speech timeouts ----------------------------------------------------
    c.add(new Entry("end_of_speech_timeout", () -> fr("").setEndOfSpeechTimeout(800)));
    c.add(new Entry("speech_event_timeout", () -> fr("").setSpeechEventTimeout(1200)));

    // ---- execute_swml (dict + JSON-string + transfer) ----------------------
    c.add(
        new Entry(
            "execute_swml.dict",
            () ->
                fr("")
                    .executeSwml(
                        map(
                            "version",
                            "1.0.0",
                            "sections",
                            map("main", list(map("answer", map())))),
                        false)));
    c.add(
        new Entry(
            "execute_swml.dict_transfer",
            () ->
                fr("")
                    .executeSwml(
                        map(
                            "version",
                            "1.0.0",
                            "sections",
                            map("main", list(map("answer", map())))),
                        true)));
    c.add(
        new Entry(
            "execute_swml.json_string",
            () ->
                fr("")
                    .executeSwml(
                        "{\"version\": \"1.0.0\", \"sections\": {\"main\": [{\"hangup\": {}}]}}",
                        false)));

    return c;
  }

  /** String/String ordered map helper (for pay parameters: name/value pairs). */
  private static Map<String, String> map2(String... kv) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put(kv[i], kv[i + 1]);
    }
    return m;
  }

  public static void main(String[] args) {
    // disableHtmlEscaping mirrors Go's enc.SetEscapeHTML(false): keep '+'/'&'/
    // '<' etc. literal so the JSON matches Python's json.dumps output.
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    Map<String, Object> out = new LinkedHashMap<>();
    for (Entry e : corpus()) {
      if (out.containsKey(e.id())) {
        System.err.println("emit-corpus: duplicate corpus id " + e.id());
        System.exit(1);
      }
      out.put(e.id(), e.build().get().toMap());
    }

    System.out.println(gson.toJson(out));
  }
}
