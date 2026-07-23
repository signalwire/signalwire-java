/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.signalwire.sdk.aichat.AIChatClient;
import com.signalwire.sdk.aichat.AIChatClientOptions;
import com.signalwire.sdk.aichat.AIChatError;
import com.signalwire.sdk.aichat.ChatLog;
import com.signalwire.sdk.aichat.ChatOptions;
import com.signalwire.sdk.aichat.ChatResponse;
import com.signalwire.sdk.aichat.ConversationInfo;
import com.signalwire.sdk.aichat.CreateConversationOptions;
import com.signalwire.sdk.aichat.SummaryError;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AIChatDump — the Java port's AI-CHAT dump program for the cross-port wire-behavioral gate ({@code
 * porting-sdk/scripts/diff_port_ai_chat.py}, on the {@code ai-chat-client} branch — a COORDINATED
 * pass).
 *
 * <p>The gate boots the in-process {@code mock_ai_chat} server, exports {@code MOCK_AI_CHAT_URL} +
 * {@code SIGNALWIRE_PROJECT_ID} / {@code SIGNALWIRE_API_TOKEN} into this program's env, runs it,
 * and asserts the JSON it prints (plus the wire requests the mock recorded) speak the AI Chat
 * protocol per the vendored spec ({@code ai-chat-specs/ai-chat.yaml}).
 *
 * <p>This mirrors {@code porting-sdk/scripts/ai_chat_dump_reference.py} EXACTLY: it drives the Java
 * {@link AIChatClient} through the shared {@code ai_chat_corpus} and emits ONE JSON object to
 * stdout (nothing else), keyed by corpus step:
 *
 * <ul>
 *   <li>success steps (create/chat/end/delete/log/summarize): {@code {wire_method, decoded:{…}}}
 *   <li>summarize_failed (the summarize {@code {error}} one_of branch — must SURFACE, not swallow):
 *       {@code {wire_method:"summarize", raised:true, error_type, message}}
 *   <li>error steps (err_notfound/err_ratelimit/err_inprogress/err_auth/err_unmapped): {@code
 *       {raised:true, error_code, error_type}}
 * </ul>
 *
 * <p>The corpus (steps + SUMMARIZE_ERROR_ID + ERROR_STEPS + force_error_id) is data, identical for
 * every language; it is mirrored inline here from {@code ai_chat_corpus.py}.
 *
 * <p>Run via the {@code aiChatDump} Gradle task against a running mock:
 *
 * <pre>
 *   MOCK_AI_CHAT_URL=http://127.0.0.1:PORT/api/ai/chat ./gradlew -q --console=plain aiChatDump
 * </pre>
 *
 * Nothing but the JSON object is written to stdout on success.
 */
public final class AIChatDump {

  private AIChatDump() {}

  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  /** The sentinel conversation id that makes summarize return its {@code {error}} branch. */
  private static final String SUMMARIZE_ERROR_ID = "__summarize_error";

  /**
   * error-step id → the JSON-RPC code the port's raised error MUST carry (call order preserved).
   */
  private static final Map<String, Integer> ERROR_STEPS = new LinkedHashMap<>();

  static {
    ERROR_STEPS.put("err_notfound", -32001); // ConversationNotFound
    ERROR_STEPS.put("err_ratelimit", -32005); // RateLimit
    ERROR_STEPS.put("err_inprogress", -32007); // ChatInProgress
    ERROR_STEPS.put("err_auth", -32009); // Authentication
    ERROR_STEPS.put("err_unmapped", -32602); // base AIChatError (unmapped code)
  }

  /** The sentinel conversation id that makes the mock return {@code code}. */
  private static String forceErrorId(int code) {
    return "__err_" + code;
  }

  private static Map<String, Object> run(String url) {
    Map<String, Object> out = new LinkedHashMap<>();
    AIChatClient client = new AIChatClient(AIChatClientOptions.builder().url(url).build());

    // ── success steps ──────────────────────────────────────────────────
    ConversationInfo info =
        client.createConversation(
            "conv-1",
            CreateConversationOptions.builder()
                .configUrl("http://cfg")
                .timeout(30)
                .reinit(true)
                .build());
    Map<String, Object> createDecoded = new LinkedHashMap<>();
    createDecoded.put("status", info.getStatus());
    createDecoded.put("id", info.getId());
    createDecoded.put("initial_message", info.getInitialMessage());
    out.put("create", step("create_conversation", createDecoded));

    ChatResponse reply =
        client.chat("conv-1", "hello", ChatOptions.builder().timeout(30).reinit(true).build());
    Map<String, Object> chatDecoded = new LinkedHashMap<>();
    chatDecoded.put("response", reply.getText());
    chatDecoded.put("user_event", reply.getUserEvent());
    out.put("chat", step("chat", chatDecoded));

    // end/delete return bool idiomatically; the wire result also carries the
    // conversation id (the caller's own input, echoed). Report both the derived
    // status and the id operated on — mirroring the reference dump.
    boolean ended = client.end("conv-1");
    Map<String, Object> endDecoded = new LinkedHashMap<>();
    endDecoded.put("status", ended ? "ended" : "?");
    endDecoded.put("id", "conv-1");
    out.put("end", step("end_conversation", endDecoded));

    boolean deleted = client.delete("conv-1");
    Map<String, Object> deleteDecoded = new LinkedHashMap<>();
    deleteDecoded.put("status", deleted ? "deleted" : "?");
    deleteDecoded.put("id", "conv-1");
    out.put("delete", step("delete", deleteDecoded));

    ChatLog log = client.log("conv-1");
    Map<String, Object> logDecoded = new LinkedHashMap<>();
    logDecoded.put("chat_log", log.getMessages());
    logDecoded.put("call_timeline", log.getCallTimeline());
    out.put("log", step("chat_log", logDecoded));

    String summary = client.summarize("conv-1");
    Map<String, Object> summarizeDecoded = new LinkedHashMap<>();
    summarizeDecoded.put("summary", summary);
    out.put("summarize", step("summarize", summarizeDecoded));

    // ── summarize one_of {error} branch: must SURFACE, not swallow ───────
    try {
      String swallowed = client.summarize(SUMMARIZE_ERROR_ID);
      Map<String, Object> failed = new LinkedHashMap<>();
      failed.put("wire_method", "summarize");
      failed.put("raised", false);
      Map<String, Object> d = new LinkedHashMap<>();
      d.put("summary", swallowed);
      failed.put("decoded", d);
      out.put("summarize_failed", failed);
    } catch (SummaryError e) {
      Map<String, Object> failed = new LinkedHashMap<>();
      failed.put("wire_method", "summarize");
      failed.put("raised", true);
      failed.put("error_type", e.getClass().getSimpleName());
      failed.put("message", e.getServerMessage());
      out.put("summarize_failed", failed);
    }

    // ── error-code steps (JSON-RPC error object) ─────────────────────────
    for (Map.Entry<String, Integer> entry : ERROR_STEPS.entrySet()) {
      String stepId = entry.getKey();
      int code = entry.getValue();
      try {
        client.chat(forceErrorId(code), "x");
        Map<String, Object> notRaised = new LinkedHashMap<>();
        notRaised.put("raised", false);
        out.put(stepId, notRaised);
      } catch (AIChatError e) {
        Map<String, Object> raised = new LinkedHashMap<>();
        raised.put("raised", true);
        raised.put("error_code", e.getCode());
        raised.put("error_type", e.getClass().getSimpleName());
        out.put(stepId, raised);
      }
    }

    return out;
  }

  private static Map<String, Object> step(String wireMethod, Map<String, Object> decoded) {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("wire_method", wireMethod);
    s.put("decoded", decoded);
    return s;
  }

  /**
   * Entry point.
   *
   * @param args unused; reads {@code MOCK_AI_CHAT_URL} from the environment.
   */
  public static void main(String[] args) {
    String url = System.getenv("MOCK_AI_CHAT_URL");
    if (url == null || url.isEmpty()) {
      System.err.println("MOCK_AI_CHAT_URL not set");
      System.exit(2);
      return;
    }
    try {
      Map<String, Object> out = run(url);
      System.out.println(GSON.toJson(out));
    } catch (RuntimeException e) {
      System.err.println("ai-chat-dump: " + e);
      System.exit(1);
    }
  }
}
