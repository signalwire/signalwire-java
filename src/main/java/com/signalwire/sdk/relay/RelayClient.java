/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.logging.Logger;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * RELAY WebSocket connection manager.
 *
 * <p>Manages WebSocket connections to the SignalWire RELAY service using JSON-RPC 2.0. Implements
 * the four correlation mechanisms:
 *
 * <ol>
 *   <li>JSON-RPC id -> CompletableFuture for RPC response matching
 *   <li>call_id -> Call for event routing
 *   <li>control_id -> Action per Call for action event routing
 *   <li>tag -> CompletableFuture&lt;Call&gt; for dial correlation
 * </ol>
 *
 * <p>Also handles:
 *
 * <ul>
 *   <li>Event ACK for every {@code signalwire.event}
 *   <li>Ping/pong for {@code signalwire.ping}
 *   <li>Exponential backoff reconnection
 *   <li>Authorization state for fast reconnection
 *   <li>Server-initiated disconnect with restart flag
 *   <li>Dynamic context subscription via receive/unreceive
 *   <li>Message tracking by message_id
 * </ul>
 *
 * <pre>{@code
 * var client = RelayClient.builder()
 *     .project("project-id")
 *     .token("api-token")
 *     .space("example.signalwire.com")
 *     .contexts(List.of("default"))
 *     .build();
 *
 * client.onCall(call -> {
 *     call.answer();
 *     var action = call.play(List.of(Map.of("type", "tts",
 *         "params", Map.of("text", "Hello!"))));
 *     action.waitForCompletion();
 *     call.hangup();
 * });
 *
 * client.run();
 * }</pre>
 *
 * <h2>Thread safety</h2>
 *
 * <p><b>A {@code RelayClient} is thread-safe.</b> After {@link #run()} (or {@link #connect()}), the
 * client owns an internal reader thread that receives RELAY frames and dispatches events; your
 * application interacts with the client from other threads. That is the intended model:
 *
 * <ul>
 *   <li>Register handlers ({@link #onCall}, {@link #onMessage}, {@link #onEvent}) <em>before</em>
 *       {@code run()}. The handler fields are read on the reader thread; set them during setup, not
 *       concurrently mid-run.
 *   <li>The four correlation maps (pending requests, calls, pending dials, messages) are {@link
 *       java.util.concurrent.ConcurrentHashMap}s, and connection state ({@code connected}, {@code
 *       running}, {@code webSocket}, {@code sessionId}, …) is held in {@code volatile} fields, so a
 *       dial launched from your thread correlates safely against a response landing on the reader
 *       thread.
 *   <li>Outbound RPCs (a {@link Call}'s verbs, {@link #dial}) may be issued from any thread; each
 *       is an independent request/response correlated by id.
 *   <li>{@link #close()} / {@link AutoCloseable} is safe to call from a different thread than the
 *       one that called {@code run()}; it tears the connection down and unblocks {@code run()}.
 * </ul>
 *
 * <p><b>Your {@code onCall}/{@code onMessage}/{@code onEvent} handler runs on the RELAY reader
 * thread.</b> Handler code (and the per-call listeners it registers) should be short and
 * non-blocking — the {@code onCall} handler is dispatched on a worker so a blocking answer/verb
 * sequence is tolerated, but blocking directly in an {@code onEvent}/{@code onMessage} handler
 * stalls event delivery for the whole client. Offload heavy work to your own executor.
 */
public class RelayClient implements AutoCloseable {

  private static final Logger log = Logger.getLogger(RelayClient.class);
  private static final Gson gson = new Gson();

  // ── Configuration ────────────────────────────────────────────────
  private final String project;
  private final String token;
  private final String jwtToken;
  private final String space;
  private final List<String> contexts;

  // ── Connection state ─────────────────────────────────────────────
  private volatile InternalWebSocket webSocket;
  private volatile String protocol;

  /**
   * Server-assigned session id captured from the {@code signalwire.connect} handshake result
   * ({@code result.sessionid}). Kept OFF the public surface — the Python reference does not expose
   * it either. Tests read it through the package-private {@link #sessionIdForTesting()} accessor to
   * scope a mock-relay harness view to this client's own frames (parallel-test isolation).
   * Production code never needs it.
   */
  private volatile String sessionId;

  private volatile String authorizationState;
  private volatile boolean connected;
  private volatile boolean running;
  private volatile boolean restartOnDisconnect;

  /** Future that completes when the initial signalwire.connect handshake finishes. */
  private volatile CompletableFuture<Void> connectFuture;

  // ── Correlation maps ─────────────────────────────────────────────
  /** JSON-RPC id -> CompletableFuture<Map> for RPC response matching */
  private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> pendingRequests =
      new ConcurrentHashMap<>();

  /**
   * JSON-RPC id -> request method, so the response dispatcher can skip result-code checking for the
   * {@code signalwire.connect} handshake (whose result carries no {@code code}) while raising
   * {@link RelayError} on a non-2xx code for every calling/messaging verb. Mirrors the Python
   * reference's {@code _pending_methods} (relay/client.py:749).
   */
  private final ConcurrentHashMap<String, String> pendingMethods = new ConcurrentHashMap<>();

  /** call_id -> Call for event routing */
  private final ConcurrentHashMap<String, Call> calls = new ConcurrentHashMap<>();

  /** tag -> CompletableFuture<Call> for dial correlation */
  private final ConcurrentHashMap<String, CompletableFuture<Call>> pendingDials =
      new ConcurrentHashMap<>();

  /** message_id -> Message for messaging state routing */
  private final ConcurrentHashMap<String, Message> messages = new ConcurrentHashMap<>();

  /**
   * Requests issued while disconnected, queued for delivery after reconnect. Flushed by {@link
   * #flushExecuteQueue()} once the connection is (re)established.
   */
  private final java.util.concurrent.ConcurrentLinkedQueue<QueuedRequest> executeQueue =
      new java.util.concurrent.ConcurrentLinkedQueue<>();

  /** A request buffered while disconnected: its raw JSON frame + the awaiting future. */
  private record QueuedRequest(String json, CompletableFuture<Map<String, Object>> future) {}

  /**
   * Per-request response deadline (ms). Mirrors the reference {@code _EXECUTE_TIMEOUT}. Not final
   * so the relay-liveness dump can tighten it to keep the bounded-window fixtures fast (mirroring
   * the python driver monkeypatching {@code _EXECUTE_TIMEOUT}); production callers never change it.
   */
  private volatile long executeTimeoutMs = 30_000;

  /** Max requests buffered while disconnected before {@link #execute} raises (reference cap). */
  private static final int EXECUTE_QUEUE_MAX = 100;

  // ── Client-side ping / half-open detection ───────────────────────
  /**
   * Client ping interval (ms) + max consecutive ping failures before the connection is declared
   * half-open and force-closed for reconnect. Mirrors the reference {@code _CLIENT_PING_INTERVAL} /
   * {@code _MAX_PING_FAILURES} (client.py:86-88). A half-open peer (socket still open, peer stopped
   * answering) is otherwise undetectable — the client would hang forever. Package-settable so the
   * relay-liveness dump can drive detection inside the bounded test window.
   */
  private volatile long clientPingIntervalMs = 30_000;

  private volatile int maxPingFailures = 3;

  /** The ping watchdog thread (one per connection); interrupted on disconnect/force-close. */
  private volatile Thread pingThread;

  // ── Event handlers ───────────────────────────────────────────────
  private Consumer<Call> onCallHandler;
  private Consumer<Message> onMessageHandler;
  private Consumer<RelayEvent> onEventHandler;

  // ── Reconnection state ───────────────────────────────────────────
  private long reconnectDelay = Constants.RECONNECT_INITIAL_DELAY_MS;

  /**
   * Max concurrent inbound calls this client will accept before dropping. Mirrors the reference
   * {@code Client(max_active_calls=...)} / {@code RELAY_MAX_ACTIVE_CALLS} env var (relay/client.py:
   * 90,150-160,914) — when the tracked call count reaches this cap, an inbound call is logged and
   * dropped rather than dispatched.
   */
  private final int maxActiveCalls;

  /** Default cap, matching Python's {@code _DEFAULT_MAX_ACTIVE_CALLS} (relay/client.py:90). */
  private static final int DEFAULT_MAX_ACTIVE_CALLS = 1000;

  // ── Thread pool ──────────────────────────────────────────────────
  private final ExecutorService executor =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r, "relay-worker");
            t.setDaemon(true);
            return t;
          });

  private final CountDownLatch runLatch = new CountDownLatch(1);

  private RelayClient(Builder builder) {
    this.project = builder.project;
    this.token = builder.token;
    this.jwtToken = builder.jwtToken;
    this.space = builder.space;
    this.contexts =
        builder.contexts != null ? new ArrayList<>(builder.contexts) : new ArrayList<>();
    this.maxActiveCalls = builder.maxActiveCalls;
  }

  // ── Builder ──────────────────────────────────────────────────────

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String project;
    private String token;
    private String jwtToken;
    private String space;
    private List<String> contexts;
    private Integer maxActiveCallsOverride;
    private int maxActiveCalls; // resolved in build(): override > env > default

    public Builder project(String project) {
      this.project = project;
      return this;
    }

    public Builder token(String token) {
      this.token = token;
      return this;
    }

    public Builder jwtToken(String jwtToken) {
      this.jwtToken = jwtToken;
      return this;
    }

    public Builder space(String space) {
      this.space = space;
      return this;
    }

    public Builder host(String host) {
      this.space = host;
      return this;
    }

    public Builder contexts(List<String> contexts) {
      this.contexts = contexts;
      return this;
    }

    /**
     * Cap on concurrent inbound calls before new inbound calls are dropped. Mirrors the reference
     * {@code Client(max_active_calls=...)} (relay/client.py:125). When unset here, the {@code
     * RELAY_MAX_ACTIVE_CALLS} env var is consulted, then the default of {@value
     * #DEFAULT_MAX_ACTIVE_CALLS}.
     *
     * @param maxActiveCalls the cap; clamped to at least 1
     */
    public Builder maxActiveCalls(int maxActiveCalls) {
      this.maxActiveCallsOverride = maxActiveCalls;
      return this;
    }

    /** Default RELAY host, matching Python's DEFAULT_RELAY_HOST (relay/constants.py). */
    private static final String DEFAULT_RELAY_HOST = "relay.signalwire.com";

    public RelayClient build() {
      // Env-var fallback for any credential not set explicitly — parity with
      // Python's relay Client() (relay/client.py), which reads
      // SIGNALWIRE_PROJECT_ID / SIGNALWIRE_API_TOKEN / SIGNALWIRE_JWT_TOKEN and
      // defaults the host to SIGNALWIRE_SPACE or DEFAULT_RELAY_HOST.
      if (project == null) {
        project = envOrNull("SIGNALWIRE_PROJECT_ID");
      }
      if (token == null) {
        token = envOrNull("SIGNALWIRE_API_TOKEN");
      }
      if (jwtToken == null) {
        jwtToken = envOrNull("SIGNALWIRE_JWT_TOKEN");
      }
      if (space == null) {
        String envSpace = envOrNull("SIGNALWIRE_SPACE");
        space = (envSpace != null) ? envSpace : DEFAULT_RELAY_HOST;
      }
      // A6 credential contract: missing OR EMPTY creds fail PRE-CONNECT with a per-variable,
      // actionable message naming the specific credential + its env var. JWT-only path: project/
      // token not required (project_id is inside the token). space always defaults, so it is never
      // itself an error. (Empty — not just null — counts as missing, matching the reference which
      // rejects an empty credential rather than attempting to connect with it.)
      if (jwtToken == null || jwtToken.isEmpty()) {
        if (project == null || project.isEmpty()) {
          throw new IllegalArgumentException(
              "project is required: pass it to the builder or set SIGNALWIRE_PROJECT_ID "
                  + "(or provide a JWT token via jwtToken() / SIGNALWIRE_JWT_TOKEN).");
        }
        if (token == null || token.isEmpty()) {
          throw new IllegalArgumentException(
              "token is required: pass it to the builder or set SIGNALWIRE_API_TOKEN "
                  + "(or provide a JWT token via jwtToken() / SIGNALWIRE_JWT_TOKEN).");
        }
      }
      // Max concurrent calls: constructor override > env var > default, matching
      // relay/client.py:150-160 (each clamped to >= 1; a non-numeric env value
      // falls back to the default, like Python's ValueError branch).
      if (maxActiveCallsOverride != null) {
        maxActiveCalls = Math.max(1, maxActiveCallsOverride);
      } else {
        String envVal = envOrNull("RELAY_MAX_ACTIVE_CALLS");
        int resolved = DEFAULT_MAX_ACTIVE_CALLS;
        if (envVal != null) {
          try {
            resolved = Math.max(1, Integer.parseInt(envVal.trim()));
          } catch (NumberFormatException e) {
            resolved = DEFAULT_MAX_ACTIVE_CALLS;
          }
        }
        maxActiveCalls = resolved;
      }
      return new RelayClient(this);
    }

    private static String envOrNull(String key) {
      String v = System.getenv(key);
      return (v != null && !v.isEmpty()) ? v : null;
    }
  }

  // ── Public API ───────────────────────────────────────────────────

  public String getProject() {
    return project;
  }

  public String getSpace() {
    return space;
  }

  public List<String> getContexts() {
    return Collections.unmodifiableList(contexts);
  }

  public boolean isConnected() {
    return connected;
  }

  /**
   * Returns the protocol identifier issued by the server during the signalwire.connect handshake.
   * Empty string before connect completes.
   */
  public String getRelayProtocol() {
    return protocol != null ? protocol : "";
  }

  /**
   * Returns the authorization state blob the server pushed via the {@code
   * signalwire.authorization.state} event, or null.
   */
  public String getAuthorizationState() {
    return authorizationState;
  }

  /**
   * Test-only setter for the protocol — used by reconnect-with-protocol tests that simulate "I
   * already have a session token from last time".
   */
  public void setRelayProtocol(String protocol) {
    this.protocol = protocol;
  }

  /**
   * Package-private, test-only accessor for the server-assigned session id captured at the {@code
   * signalwire.connect} handshake. NOT part of the public API surface (the Python reference keeps
   * {@code sessionid} internal too) — it exists so the in-package {@code RelayMockTest} harness can
   * scope its journal reads / resets / pushes to this client's own session, making mock-backed
   * tests parallel-safe. Returns {@code null} until the handshake completes.
   */
  String sessionIdForTesting() {
    return sessionId;
  }

  /** Register a handler for inbound calls. */
  public void onCall(Consumer<Call> handler) {
    this.onCallHandler = handler;
  }

  /** Register a handler for inbound messages. */
  public void onMessage(Consumer<Message> handler) {
    this.onMessageHandler = handler;
  }

  /** Register a handler for all raw events. */
  public void onEvent(Consumer<RelayEvent> handler) {
    this.onEventHandler = handler;
  }

  /** Connect and run the client. Blocks until {@link #disconnect()} is called. */
  public void run() {
    running = true;
    connectInternal();
    try {
      runLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      running = false;
    }
  }

  /**
   * Open the WebSocket connection and complete the signalwire.connect handshake without blocking
   * the caller.
   *
   * <p>Mirrors the Python {@code RelayClient.connect()} coroutine. Tests use this directly;
   * production code typically uses {@link #run()} instead.
   *
   * @param timeoutMs how long to wait for the handshake to complete
   * @throws RelayError if connect fails or times out (like {@link #dial}, which also throws {@code
   *     RelayError} on a connection failure — one connection-failure surface, one type)
   */
  public void connect(long timeoutMs) {
    running = true;
    connectFuture = new CompletableFuture<>();
    connectInternal();
    try {
      connectFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (java.util.concurrent.ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof RelayError re) {
        throw re;
      }
      throw new RelayError(
          RelayError.UNKNOWN_CODE, "RelayClient.connect failed: " + cause.getMessage(), cause);
    } catch (java.util.concurrent.TimeoutException e) {
      throw new RelayError(
          RelayError.UNKNOWN_CODE, "RelayClient.connect timed out after " + timeoutMs + "ms", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RelayError(RelayError.UNKNOWN_CODE, "RelayClient.connect interrupted", e);
    }
  }

  /** Connect with a 30-second timeout. */
  public void connect() {
    connect(30_000);
  }

  /** Disconnect the client. */
  public void disconnect() {
    running = false;
    stopPingWatchdog();
    if (webSocket != null) {
      webSocket.close();
    }
    runLatch.countDown();
    executor.shutdown();
  }

  /**
   * {@link AutoCloseable} entry point so the client can be used in a try-with-resources block:
   *
   * <pre>{@code
   * try (var client = RelayClient.builder()...build()) {
   *     client.connect();
   *     // ... use the client ...
   * } // close() runs here: WebSocket shut down, worker pool released
   * }</pre>
   *
   * Releases the same resources as {@link #disconnect()} — closes the RELAY WebSocket, releases the
   * {@link #run()} latch, and shuts the worker {@link java.util.concurrent.ExecutorService} down —
   * so the client is the rough Java parallel of Python's {@code async with RelayClient(...)}
   * context manager. Idempotent: a second call is a harmless no-op.
   */
  @Override
  public void close() {
    disconnect();
  }

  /** Subscribe to additional contexts dynamically. */
  public Map<String, Object> receive(List<String> newContexts) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("contexts", newContexts);
    return execute(Constants.METHOD_RECEIVE, params);
  }

  /** Unsubscribe from contexts. */
  public Map<String, Object> unreceive(List<String> removeContexts) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("contexts", removeContexts);
    return execute(Constants.METHOD_UNRECEIVE, params);
  }

  /**
   * Dial an outbound call.
   *
   * @param devices nested array: outer = sequential, inner = parallel
   * @param options optional parameters (region, max_price_per_minute)
   * @param timeout timeout in milliseconds
   * @return the answered Call
   */
  public Call dial(
      List<List<Map<String, Object>>> devices, Map<String, Object> options, long timeout) {
    String explicitTag = options != null ? Objects.toString(options.get("tag"), null) : null;
    String tag = explicitTag != null ? explicitTag : UUID.randomUUID().toString();

    // Register pending dial BEFORE sending RPC
    CompletableFuture<Call> future = new CompletableFuture<>();
    pendingDials.put(tag, future);

    // Build params
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("tag", tag);
    params.put("devices", devices);
    if (options != null) {
      for (Map.Entry<String, Object> entry : options.entrySet()) {
        if (!"tag".equals(entry.getKey())) {
          params.put(entry.getKey(), entry.getValue());
        }
      }
    }

    try {
      // Send RPC - response is just {"code":"200","message":"Dialing"}
      execute(Constants.METHOD_DIAL, params);

      // Wait for calling.call.dial event to resolve the future
      return future.get(timeout, TimeUnit.MILLISECONDS);
    } catch (java.util.concurrent.TimeoutException e) {
      throw new RelayError("Dial timed out after " + timeout + "ms", e);
    } catch (java.util.concurrent.ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      throw new RelayError("Dial failed: " + cause.getMessage(), cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RelayError("Dial interrupted", e);
    } finally {
      pendingDials.remove(tag);
    }
  }

  /** Dial with default timeout of 120 seconds. */
  public Call dial(List<List<Map<String, Object>>> devices) {
    return dial(devices, null, 120_000);
  }

  /**
   * Send an outbound SMS/MMS.
   *
   * @param context message context
   * @param fromNumber sender E.164 number
   * @param toNumber recipient E.164 number
   * @param body message body (required if no media)
   * @param mediaUrls media URLs (required if no body)
   * @return the Message object with message_id for tracking
   */
  public Message sendMessage(
      String context, String fromNumber, String toNumber, String body, List<String> mediaUrls) {
    return sendMessage(context, fromNumber, toNumber, body, mediaUrls, null);
  }

  /** Send a message with optional tags. */
  public Message sendMessage(
      String context,
      String fromNumber,
      String toNumber,
      String body,
      List<String> mediaUrls,
      List<String> tags) {
    // The default context is the protocol string (post-handshake) — matches
    // the Python SDK's behavior of context = context or self._relay_protocol or "default".
    String msgContext = context;
    if (msgContext == null || msgContext.isEmpty()) {
      msgContext = (protocol != null && !protocol.isEmpty()) ? protocol : "default";
    }

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("context", msgContext);
    params.put("from_number", fromNumber);
    params.put("to_number", toNumber);
    if (body != null) {
      params.put("body", body);
    }
    if (mediaUrls != null && !mediaUrls.isEmpty()) {
      params.put("media", mediaUrls);
    }
    if (tags != null && !tags.isEmpty()) {
      params.put("tags", tags);
    }

    // execute() now RAISES RelayError on any send failure (timeout / error frame /
    // non-2xx / dead connection) instead of returning an empty map — so we no longer
    // fabricate a random message_id and hand back a Message in state "queued" for a
    // send that never happened (NB-1(b)). We reach here only on a real 2xx result.
    Map<String, Object> result = execute(Constants.METHOD_MESSAGING_SEND, params);

    String messageId = result != null ? Objects.toString(result.get("message_id"), null) : null;
    if (messageId == null) {
      throw new RelayError(
          RelayError.UNKNOWN_CODE, "messaging.send succeeded but returned no message_id");
    }

    Message message = new Message(messageId);
    message.setContext(msgContext);
    message.setDirection("outbound");
    message.setFromNumber(fromNumber);
    message.setToNumber(toNumber);
    message.setBody(body);
    message.setMedia(mediaUrls);
    message.setState("queued");
    if (tags != null) {
      message.setTags(tags);
    }

    // Track by message_id for state routing
    messages.put(messageId, message);
    return message;
  }

  // ── RPC execution ────────────────────────────────────────────────

  /**
   * Execute an RPC method and wait for the response.
   *
   * <p>Mirrors the Python reference {@code RelayClient._send_request} (relay/client.py:730): a
   * request timeout, an error frame, a non-2xx result code, or a dead/half-open connection RAISES
   * {@link RelayError} — the failure is NEVER swallowed into an empty map (the old behavior that
   * made a dead-connection {@code play()}/{@code hangup()} "succeed" silently). On timeout the
   * connection is force-closed for reconnect (a timeout signals a half-open peer). A request issued
   * while disconnected is QUEUED for delivery after reconnect rather than dropped.
   *
   * @throws RelayError on request timeout, server error frame, non-2xx result code, dead
   *     connection, or a full disconnected-queue
   */
  public Map<String, Object> execute(String method, Map<String, Object> params) {
    String requestId = UUID.randomUUID().toString();

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("jsonrpc", Constants.JSONRPC_VERSION);
    request.put("id", requestId);
    request.put("method", method);

    // Send params verbatim. The Python reference's RelayClient.execute forwards
    // params unchanged to _send_request — project_id/protocol are carried ONLY
    // in the signalwire.connect handshake (see authenticate()), NOT injected
    // into every calling.*/messaging.* RPC. Injecting them here was a wire
    // divergence: the platform correlates a call by node_id/call_id, and the
    // handshake already scopes the session to the project + protocol.
    Map<String, Object> fullParams =
        params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
    request.put("params", fullParams);

    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    pendingRequests.put(requestId, future);
    pendingMethods.put(requestId, method);

    boolean sent;
    try {
      String json = gson.toJson(request);
      log.debug(">> %s id=%s", method, requestId);
      if (webSocket != null && webSocket.isOpen() && connected) {
        webSocket.send(json);
        sent = true;
      } else {
        // Not connected: queue for delivery after reconnect (parity with the
        // reference _execute_queue, client.py:752-759) rather than dropping the
        // request. A full queue raises rather than growing unbounded.
        sent = false;
        if (executeQueue.size() >= EXECUTE_QUEUE_MAX) {
          throw new RelayError(
              RelayError.UNKNOWN_CODE, "Execute queue full — too many requests while disconnected");
        }
        executeQueue.offer(new QueuedRequest(json, future));
        log.debug("Request queued (not connected): %s", method);
      }

      try {
        return future.get(executeTimeoutMs, TimeUnit.MILLISECONDS);
      } catch (java.util.concurrent.TimeoutException te) {
        log.error("Request timeout: %s %s", method, requestId);
        // A timeout on a supposedly-open socket signals a half-open connection —
        // force reconnect (never for the connect handshake itself).
        if (sent && !Constants.METHOD_CONNECT.equals(method)) {
          forceClose();
        }
        throw new RelayError(RelayError.UNKNOWN_CODE, "Request timeout for " + method, te);
      }
    } catch (java.util.concurrent.ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof RelayError re) {
        throw re;
      }
      throw new RelayError(RelayError.UNKNOWN_CODE, cause.getMessage(), cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RelayError(RelayError.UNKNOWN_CODE, "Interrupted during " + method, e);
    } finally {
      pendingRequests.remove(requestId);
      pendingMethods.remove(requestId);
    }
  }

  /**
   * Execute an RPC method on a call, swallowing ONLY the call-gone codes 404/410. Mirrors the
   * Python reference contract (A2): a 404/410 result means the call is already gone, so the verb is
   * a no-op; any OTHER non-2xx (e.g. 500) propagates as a {@link RelayError} from {@link #execute}.
   */
  Map<String, Object> executeOnCall(String method, Map<String, Object> params) {
    try {
      return execute(method, params);
    } catch (RelayError e) {
      if (Constants.isCallGoneCode(Integer.toString(e.getCode()))) {
        log.info("Call gone during %s (code %s)", method, e.getCode());
        return Collections.emptyMap();
      }
      throw e;
    }
  }

  // ── Connection management ────────────────────────────────────────

  private void connectInternal() {
    try {
      // Permit tests and audit fixtures to point the client at a
      // plain-ws loopback by passing space="ws://127.0.0.1:NNNN" (or
      // "ws://127.0.0.1:NNNN/path"). Production callers pass a bare
      // hostname like "example.signalwire.com", which is upgraded to
      // wss:// here.
      URI uri =
          space.startsWith("ws://") || space.startsWith("wss://")
              ? new URI(space)
              : new URI("wss://" + space);
      webSocket = new InternalWebSocket(uri);
      // A5 fleet CA-var contract (hard-cut, no aliases): a custom CA bundle for the RELAY WebSocket
      // transport is supplied via SIGNALWIRE_RELAY_CA_FILE. When set (and the connection is
      // wss://),
      // trust ONLY that bundle so a private/self-signed CA is honored (parity with the Python
      // reference relay/client.py:131 `_build_relay_ssl_context`). Unset → the JDK default trust.
      String relayCaFile = System.getenv("SIGNALWIRE_RELAY_CA_FILE");
      if (relayCaFile != null
          && !relayCaFile.isEmpty()
          && "wss".equalsIgnoreCase(uri.getScheme())) {
        webSocket.setSocketFactory(com.signalwire.sdk.rest.TlsContext.socketFactory(relayCaFile));
      }
      webSocket.connect();
    } catch (Exception e) {
      log.error("Connection failed", e);
      if (connectFuture != null && !connectFuture.isDone()) {
        connectFuture.completeExceptionally(e);
      }
      scheduleReconnect();
    }
  }

  private void authenticate() {
    Map<String, Object> params = new LinkedHashMap<>();

    Map<String, Object> version = new LinkedHashMap<>();
    version.put("major", Constants.PROTOCOL_MAJOR);
    version.put("minor", Constants.PROTOCOL_MINOR);
    version.put("revision", Constants.PROTOCOL_REVISION);
    params.put("version", version);

    params.put("agent", Constants.SDK_AGENT);
    params.put("event_acks", true);

    // Authentication: JWT-only path replaces project+token.
    Map<String, Object> auth = new LinkedHashMap<>();
    if (jwtToken != null && !jwtToken.isEmpty()) {
      auth.put("jwt_token", jwtToken);
    } else {
      auth.put("project", project);
      auth.put("token", token);
    }
    params.put("authentication", auth);

    // Contexts
    if (!contexts.isEmpty()) {
      params.put("contexts", contexts);
    }

    // Resume session if we have a previous protocol
    if (protocol != null && !restartOnDisconnect) {
      params.put("protocol", protocol);
    }

    // Fast re-auth with authorization state
    if (authorizationState != null && !restartOnDisconnect) {
      params.put("authorization_state", authorizationState);
    }

    restartOnDisconnect = false;

    // Send connect request
    String requestId = UUID.randomUUID().toString();
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("jsonrpc", Constants.JSONRPC_VERSION);
    request.put("id", requestId);
    request.put("method", Constants.METHOD_CONNECT);
    request.put("params", params);

    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    pendingRequests.put(requestId, future);

    String json = gson.toJson(request);
    // SECRET-SCRUB: never log the raw project id (a credential) — log only that we are
    // authenticating.
    log.debug("Authenticating (project id redacted)");
    webSocket.send(json);

    // Handle auth response asynchronously
    future
        .thenAccept(
            result -> {
              String proto = Objects.toString(result.get("protocol"), null);
              if (proto != null) {
                protocol = proto;
              }
              String sid = Objects.toString(result.get("sessionid"), null);
              if (sid != null) {
                sessionId = sid;
              }
              connected = true;
              reconnectDelay = Constants.RECONNECT_INITIAL_DELAY_MS;
              log.info("Connected to %s (protocol: %s)", space, protocol);
              // Deliver any requests buffered while disconnected.
              flushExecuteQueue();
              // Start the client-side ping watchdog to detect a half-open peer.
              startPingWatchdog();
              if (connectFuture != null && !connectFuture.isDone()) {
                connectFuture.complete(null);
              }
            })
        .exceptionally(
            e -> {
              log.error("Authentication failed", (Exception) e);
              if (connectFuture != null && !connectFuture.isDone()) {
                connectFuture.completeExceptionally(e);
              }
              return null;
            });
  }

  private void scheduleReconnect() {
    if (!running) return;

    log.info("Reconnecting in %d ms...", reconnectDelay);
    var unused =
        executor.submit(
            () -> {
              try {
                Thread.sleep(reconnectDelay);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }

              // Exponential backoff
              reconnectDelay =
                  Math.min(
                      (long) (reconnectDelay * Constants.RECONNECT_BACKOFF_MULTIPLIER),
                      Constants.RECONNECT_MAX_DELAY_MS);

              if (running) {
                connectInternal();
              }
            });
  }

  private void handleDisconnect() {
    connected = false;
    stopPingWatchdog();

    // Reject all pending request futures
    for (Map.Entry<String, CompletableFuture<Map<String, Object>>> entry :
        pendingRequests.entrySet()) {
      entry.getValue().completeExceptionally(new RuntimeException("Disconnected"));
    }
    pendingRequests.clear();

    // Reject all pending dial futures
    for (Map.Entry<String, CompletableFuture<Call>> entry : pendingDials.entrySet()) {
      entry
          .getValue()
          .completeExceptionally(
              new RelayError(RelayError.UNKNOWN_CODE, "Connection closed during dial"));
    }
    pendingDials.clear();

    // Fault any requests still buffered while disconnected — they cannot be
    // delivered on a torn-down connection; a caller must see the failure, not hang.
    QueuedRequest queued;
    while ((queued = executeQueue.poll()) != null) {
      queued
          .future()
          .completeExceptionally(new RelayError(RelayError.UNKNOWN_CODE, "Connection closed"));
    }

    if (running) {
      scheduleReconnect();
    }
  }

  /**
   * Force-close the WebSocket to trigger reconnect. Called when a request times out (a half-open
   * peer): the socket looks open but the peer is dead. Mirrors the reference {@code _force_close}
   * (client.py:1284).
   */
  private void forceClose() {
    connected = false;
    stopPingWatchdog();
    InternalWebSocket ws = webSocket;
    if (ws != null) {
      try {
        ws.close();
      } catch (RuntimeException ignored) {
        // best-effort; the reconnect path handles the torn-down socket
      }
    }
  }

  /**
   * Send any requests buffered while disconnected, once the connection is (re)established. Mirrors
   * the reference {@code _flush_execute_queue} (client.py:776).
   */
  private void flushExecuteQueue() {
    InternalWebSocket ws = webSocket;
    if (ws == null || !ws.isOpen()) {
      return;
    }
    QueuedRequest queued;
    while ((queued = executeQueue.poll()) != null) {
      try {
        ws.send(queued.json());
      } catch (RuntimeException e) {
        queued
            .future()
            .completeExceptionally(
                new RelayError(
                    RelayError.UNKNOWN_CODE,
                    "Failed to send queued request: " + e.getMessage(),
                    e));
      }
    }
  }

  /**
   * Start the client-side ping watchdog: every {@link #clientPingIntervalMs} send a {@code
   * signalwire.ping} and require a timely response; after {@link #maxPingFailures} consecutive
   * failures the peer is half-open and the connection is force-closed for reconnect. Mirrors the
   * reference {@code _ping_loop} (client.py:1217). Idempotent per connection.
   */
  private void startPingWatchdog() {
    stopPingWatchdog();
    Thread t =
        new Thread(
            () -> {
              int failures = 0;
              while (running && connected && !Thread.currentThread().isInterrupted()) {
                try {
                  Thread.sleep(clientPingIntervalMs);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
                if (!running || !connected) {
                  return;
                }
                Map<String, Object> res;
                try {
                  long deadline = Math.max(1_000, clientPingIntervalMs);
                  res = pingOnce(deadline);
                } catch (RuntimeException e) {
                  res = null;
                }
                if (res != null) {
                  failures = 0;
                } else if (++failures >= maxPingFailures) {
                  log.warn(
                      "Ping failed %d times; connection is half-open, forcing reconnect", failures);
                  forceClose();
                  return;
                }
              }
            },
            "relay-ping-watchdog");
    t.setDaemon(true);
    pingThread = t;
    t.start();
  }

  private void stopPingWatchdog() {
    Thread t = pingThread;
    if (t != null) {
      t.interrupt();
      pingThread = null;
    }
  }

  /** Send one client-initiated ping, returning the result or {@code null} on timeout/failure. */
  private Map<String, Object> pingOnce(long deadlineMs) {
    String requestId = UUID.randomUUID().toString();
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("jsonrpc", Constants.JSONRPC_VERSION);
    request.put("id", requestId);
    request.put("method", Constants.METHOD_PING);
    request.put("params", new LinkedHashMap<>());
    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    pendingRequests.put(requestId, future);
    pendingMethods.put(requestId, Constants.METHOD_PING);
    try {
      InternalWebSocket ws = webSocket;
      if (ws == null || !ws.isOpen()) {
        return null;
      }
      ws.send(gson.toJson(request));
      return future.get(deadlineMs, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      return null;
    } finally {
      pendingRequests.remove(requestId);
      pendingMethods.remove(requestId);
    }
  }

  /**
   * Timing knobs for the relay-liveness dump (mirror the reference monkeypatch of {@code
   * _EXECUTE_TIMEOUT}/{@code _CLIENT_PING_INTERVAL}/{@code _MAX_PING_FAILURES}). Package- private
   * so it stays off the public surface; the relay-liveness dump lives in this package.
   */
  void setLivenessTimingsForTesting(long execTimeoutMs, long pingIntervalMs, int maxPings) {
    this.executeTimeoutMs = execTimeoutMs;
    this.clientPingIntervalMs = pingIntervalMs;
    this.maxPingFailures = maxPings;
  }

  // ── Message handling ─────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private void handleMessage(String rawMessage) {
    // SECRET-SCRUB: scrub credential/authorization_state VALUES out of the raw inbound frame before
    // logging (an inbound authorization.state event carries the re-auth blob). Mirrors the Python
    // reference `<< {_scrub_frame(raw)}` (client.py).
    log.debug("<< %s", FrameScrub.scrub(rawMessage));

    Map<String, Object> message;
    try {
      message = gson.fromJson(rawMessage, new TypeToken<Map<String, Object>>() {}.getType());
    } catch (Exception e) {
      log.error("Failed to parse message: " + rawMessage, e);
      return;
    }

    if (message == null) return;

    String method = (String) message.get("method");
    String id = (String) message.get("id");

    // JSON-RPC response (has "result" or "error", no "method")
    if (method == null && (message.containsKey("result") || message.containsKey("error"))) {
      handleRpcResponse(id, message);
      return;
    }

    // Server event
    if (Constants.METHOD_EVENT.equals(method)) {
      // Send ACK immediately
      sendAck(id);
      handleEvent(message);
      return;
    }

    // Server ping
    if (Constants.METHOD_PING.equals(method)) {
      sendAck(id);
      return;
    }

    // Server-initiated disconnect
    if (Constants.METHOD_DISCONNECT.equals(method)) {
      sendAck(id);
      handleServerDisconnect(message);
      return;
    }
  }

  @SuppressWarnings("unchecked")
  private void handleRpcResponse(String id, Map<String, Object> message) {
    if (id == null) return;

    CompletableFuture<Map<String, Object>> future = pendingRequests.remove(id);
    String requestMethod = pendingMethods.remove(id);
    if (future == null) {
      log.debug("No pending request for id: %s", id);
      return;
    }

    // JSON-RPC-level error frame → RAISE RelayError carrying the server code
    // (parity with client.py:855-870). Never a swallowed empty map.
    if (message.containsKey("error")) {
      Object errObj = message.get("error");
      int code = RelayError.UNKNOWN_CODE;
      String msg = "Unknown error";
      if (errObj instanceof Map) {
        Map<String, Object> error = (Map<String, Object>) errObj;
        code = toIntCode(error.get("code"));
        msg = Objects.toString(error.getOrDefault("message", "Unknown error"), "Unknown error");
      } else if (errObj != null) {
        msg = errObj.toString();
      }
      future.completeExceptionally(new RelayError(code, msg));
      return;
    }

    Object resultObj = message.getOrDefault("result", Collections.emptyMap());
    Map<String, Object> result =
        resultObj instanceof Map ? (Map<String, Object>) resultObj : Collections.emptyMap();

    // The signalwire.connect handshake result carries no `code`; return it raw.
    // For every calling/messaging verb the result carries a code — any non-2xx
    // (500, etc.) RAISES; 404/410 also raise here and are unwrapped to a no-op by
    // executeOnCall (the A2 call-gone contract). Mirrors client.py:881-901.
    if (!Constants.METHOD_CONNECT.equals(requestMethod)) {
      Object codeObj = result.get("code");
      String code = codeObj != null ? codeObj.toString() : null;
      if (!Constants.isSuccessCode(code)) {
        String msg =
            Objects.toString(result.getOrDefault("message", "Unknown error"), "Unknown error");
        future.completeExceptionally(new RelayError(toIntCode(code), msg));
        return;
      }
    }
    future.complete(result);
  }

  /**
   * Parse a RELAY code (string or number) to an int, defaulting to {@link RelayError#UNKNOWN_CODE}.
   */
  private static int toIntCode(Object code) {
    if (code == null) {
      return RelayError.UNKNOWN_CODE;
    }
    try {
      if (code instanceof Number n) {
        return n.intValue();
      }
      return Integer.parseInt(code.toString().trim());
    } catch (NumberFormatException e) {
      return RelayError.UNKNOWN_CODE;
    }
  }

  @SuppressWarnings("unchecked")
  private void handleEvent(Map<String, Object> message) {
    Map<String, Object> outerParams =
        (Map<String, Object>) message.getOrDefault("params", Collections.emptyMap());

    RelayEvent event = RelayEvent.fromRawParams(outerParams);

    // Notify raw event handler
    if (onEventHandler != null) {
      try {
        onEventHandler.accept(event);
      } catch (Exception e) {
        log.error("Error in event handler", e);
      }
    }

    // Authorization state
    if (event instanceof RelayEvent.AuthorizationStateEvent) {
      authorizationState = ((RelayEvent.AuthorizationStateEvent) event).getAuthorizationState();
      return;
    }

    // Inbound message
    if (event instanceof RelayEvent.MessagingReceiveEvent) {
      handleInboundMessage((RelayEvent.MessagingReceiveEvent) event);
      return;
    }

    // Message state
    if (event instanceof RelayEvent.MessagingStateEvent) {
      handleMessageState((RelayEvent.MessagingStateEvent) event);
      return;
    }

    // Inbound call
    if (event instanceof RelayEvent.CallReceiveEvent) {
      handleInboundCall((RelayEvent.CallReceiveEvent) event);
      return;
    }

    // Dial completion - call_id is NESTED at params.call.call_id
    if (event instanceof RelayEvent.CallDialEvent) {
      handleDialEvent((RelayEvent.CallDialEvent) event);
      return;
    }

    // State events during dial - call not registered yet
    if (event instanceof RelayEvent.CallStateEvent) {
      RelayEvent.CallStateEvent stateEvent = (RelayEvent.CallStateEvent) event;
      String callId = stateEvent.getCallId();
      String tag = stateEvent.getTag();

      if (tag != null && pendingDials.containsKey(tag) && !calls.containsKey(callId)) {
        // Create the Call object so events route correctly
        Call call = new Call(callId, stateEvent.getNodeId());
        call.setTag(tag);
        call.setState(stateEvent.getCallState());
        call.setDirection(stateEvent.getDirection());
        call.setDevice(stateEvent.getDevice());
        call.setClient(this);
        calls.put(callId, call);
      }
      // Fall through to normal routing
    }

    // Normal routing by call_id
    String callId = event.getStringParam("call_id");
    if (callId != null) {
      Call call = calls.get(callId);
      if (call != null) {
        call.dispatchEvent(event);
        if (call.isEnded()) {
          call.resolveAllActions(event);
          calls.remove(callId);
        }
      }
    }
  }

  private void handleInboundCall(RelayEvent.CallReceiveEvent event) {
    // Drop the inbound call once the active-call cap is reached (relay/client.py:914).
    if (calls.size() >= maxActiveCalls) {
      log.error("Max active calls (" + maxActiveCalls + ") reached, dropping inbound call");
      return;
    }
    Call call = new Call(event.getCallId(), event.getNodeId());
    call.setState(event.getCallState());
    call.setDirection("inbound");
    call.setDevice(event.getDevice());
    call.setClient(this);
    calls.put(call.getCallId(), call);

    if (onCallHandler != null) {
      var unused =
          executor.submit(
              () -> {
                try {
                  onCallHandler.accept(call);
                } catch (Exception e) {
                  log.error("Error in onCall handler", e);
                }
              });
    }
  }

  private void handleDialEvent(RelayEvent.CallDialEvent event) {
    String tag = event.getTag();
    String dialState = event.getDialState();
    CompletableFuture<Call> future = pendingDials.get(tag);

    if (future == null) return;

    if (Constants.DIAL_STATE_ANSWERED.equals(dialState)) {
      Map<String, Object> callInfo = event.getCallInfo();
      String callId = event.getCallId();
      if (callId != null) {
        Call call = calls.get(callId);
        if (call == null) {
          String nodeId = RelayEvent.getStr(callInfo, "node_id", null);
          call = new Call(callId, nodeId);
          call.setTag(tag);
          call.setState(Constants.CALL_STATE_ANSWERED);
          call.setClient(this);
          calls.put(callId, call);
        }
        future.complete(call);
      }
    } else if (Constants.DIAL_STATE_FAILED.equals(dialState)) {
      future.completeExceptionally(new RuntimeException("Dial failed"));
    }
  }

  private void handleInboundMessage(RelayEvent.MessagingReceiveEvent event) {
    Message message = Message.fromReceiveEvent(event);
    messages.put(message.getMessageId(), message);

    if (onMessageHandler != null) {
      var unused =
          executor.submit(
              () -> {
                try {
                  onMessageHandler.accept(message);
                } catch (Exception e) {
                  log.error("Error in onMessage handler", e);
                }
              });
    }
  }

  private void handleMessageState(RelayEvent.MessagingStateEvent event) {
    String messageId = event.getMessageId();
    if (messageId != null) {
      Message message = messages.get(messageId);
      if (message != null) {
        message.updateFromEvent(event);
        if (message.isDone()) {
          messages.remove(messageId);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void handleServerDisconnect(Map<String, Object> message) {
    Map<String, Object> params =
        (Map<String, Object>) message.getOrDefault("params", Collections.emptyMap());
    Object restartObj = params.get("restart");
    boolean restart = Boolean.TRUE.equals(restartObj);

    if (restart) {
      log.info("Server requested restart - clearing session state");
      protocol = null;
      authorizationState = null;
      restartOnDisconnect = true;
    } else {
      log.info("Server-initiated disconnect - will reconnect with session");
    }
    // Don't set a closing flag; let the reconnect happen naturally when the socket closes
  }

  /**
   * Send a raw JSON-RPC frame on the underlying socket. Most callers use {@link #execute(String,
   * Map)}, which adds project_id/protocol automatically. This lower-level helper lets a caller emit
   * an arbitrary frame (for example a {@code method:"signalwire.event"} frame from inside an
   * on-event callback) when the higher-level API is not sufficient.
   *
   * @param frame a Gson-serializable map representing the frame
   */
  public void sendRaw(Map<String, Object> frame) {
    if (webSocket == null || !webSocket.isOpen()) return;
    webSocket.send(gson.toJson(frame));
  }

  private void sendAck(String id) {
    if (id == null || webSocket == null || !webSocket.isOpen()) return;
    Map<String, Object> ack = new LinkedHashMap<>();
    ack.put("jsonrpc", Constants.JSONRPC_VERSION);
    ack.put("id", id);
    ack.put("result", Collections.emptyMap());
    webSocket.send(gson.toJson(ack));
  }

  // ── Internal WebSocket ───────────────────────────────────────────

  private class InternalWebSocket extends WebSocketClient {

    public InternalWebSocket(URI serverUri) {
      super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
      log.info("WebSocket connected to %s", getURI());
      authenticate();
    }

    @Override
    public void onMessage(String message) {
      try {
        handleMessage(message);
      } catch (Exception e) {
        log.error("Error handling message", e);
      }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
      log.info("WebSocket closed: code=%d reason=%s remote=%s", code, reason, remote);
      handleDisconnect();
    }

    @Override
    public void onError(Exception ex) {
      log.error("WebSocket error", ex);
    }
  }

  // ── Test helpers (package-private) ────────────────────────────────

  ConcurrentHashMap<String, Call> getCalls() {
    return calls;
  }

  /**
   * The resolved max-active-calls cap (constructor override > RELAY_MAX_ACTIVE_CALLS env >
   * default).
   */
  int getMaxActiveCalls() {
    return maxActiveCalls;
  }

  ConcurrentHashMap<String, CompletableFuture<Call>> getPendingDials() {
    return pendingDials;
  }

  ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> getPendingRequests() {
    return pendingRequests;
  }

  ConcurrentHashMap<String, Message> getMessages() {
    return messages;
  }
}
