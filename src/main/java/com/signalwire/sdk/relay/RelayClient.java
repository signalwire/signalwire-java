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
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * RELAY WebSocket connection manager.
 * <p>
 * Manages WebSocket connections to the SignalWire RELAY service using JSON-RPC 2.0.
 * Implements the four correlation mechanisms:
 * <ol>
 *   <li>JSON-RPC id -> CompletableFuture for RPC response matching</li>
 *   <li>call_id -> Call for event routing</li>
 *   <li>control_id -> Action per Call for action event routing</li>
 *   <li>tag -> CompletableFuture&lt;Call&gt; for dial correlation</li>
 * </ol>
 * <p>
 * Also handles:
 * <ul>
 *   <li>Event ACK for every {@code signalwire.event}</li>
 *   <li>Ping/pong for {@code signalwire.ping}</li>
 *   <li>Exponential backoff reconnection</li>
 *   <li>Authorization state for fast reconnection</li>
 *   <li>Server-initiated disconnect with restart flag</li>
 *   <li>Dynamic context subscription via receive/unreceive</li>
 *   <li>Message tracking by message_id</li>
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
 */
public class RelayClient {

    private static final Logger log = Logger.getLogger(RelayClient.class);
    private static final Gson gson = new Gson();

    // ── Configuration ────────────────────────────────────────────────
    private final String project;
    private final String token;
    private final String space;
    private final List<String> contexts;

    // ── Connection state ─────────────────────────────────────────────
    private volatile InternalWebSocket webSocket;
    private volatile String protocol;
    private volatile String authorizationState;
    private volatile boolean connected;
    private volatile boolean running;
    private volatile boolean restartOnDisconnect;

    // ── Correlation maps ─────────────────────────────────────────────
    /** JSON-RPC id -> CompletableFuture<Map> for RPC response matching */
    private final ConcurrentHashMap<String, CompletableFuture<Map<String, Object>>> pendingRequests
            = new ConcurrentHashMap<>();

    /** call_id -> Call for event routing */
    private final ConcurrentHashMap<String, Call> calls = new ConcurrentHashMap<>();

    /** tag -> CompletableFuture<Call> for dial correlation */
    private final ConcurrentHashMap<String, CompletableFuture<Call>> pendingDials
            = new ConcurrentHashMap<>();

    /** message_id -> Message for messaging state routing */
    private final ConcurrentHashMap<String, Message> messages = new ConcurrentHashMap<>();

    // ── Event handlers ───────────────────────────────────────────────
    private Consumer<Call> onCallHandler;
    private Consumer<Message> onMessageHandler;
    private Consumer<RelayEvent> onEventHandler;

    // ── Reconnection state ───────────────────────────────────────────
    private long reconnectDelay = Constants.RECONNECT_INITIAL_DELAY_MS;

    // ── Thread pool ──────────────────────────────────────────────────
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "relay-worker");
        t.setDaemon(true);
        return t;
    });

    private final CountDownLatch runLatch = new CountDownLatch(1);

    private RelayClient(Builder builder) {
        this.project = builder.project;
        this.token = builder.token;
        this.space = builder.space;
        this.contexts = builder.contexts != null ? new ArrayList<>(builder.contexts) : new ArrayList<>();
    }

    // ── Builder ──────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String project;
        private String token;
        private String space;
        private List<String> contexts;

        public Builder project(String project) { this.project = project; return this; }
        public Builder token(String token) { this.token = token; return this; }
        public Builder space(String space) { this.space = space; return this; }
        public Builder contexts(List<String> contexts) { this.contexts = contexts; return this; }

        public RelayClient build() {
            Objects.requireNonNull(project, "project is required");
            Objects.requireNonNull(token, "token is required");
            Objects.requireNonNull(space, "space is required");
            return new RelayClient(this);
        }
    }

    // ── Public API ───────────────────────────────────────────────────

    public String getProject() { return project; }
    public String getSpace() { return space; }
    public List<String> getContexts() { return Collections.unmodifiableList(contexts); }
    public boolean isConnected() { return connected; }

    /**
     * Register a handler for inbound calls.
     */
    public void onCall(Consumer<Call> handler) {
        this.onCallHandler = handler;
    }

    /**
     * Register a handler for inbound messages.
     */
    public void onMessage(Consumer<Message> handler) {
        this.onMessageHandler = handler;
    }

    /**
     * Register a handler for all raw events.
     */
    public void onEvent(Consumer<RelayEvent> handler) {
        this.onEventHandler = handler;
    }

    /**
     * Connect and run the client. Blocks until {@link #disconnect()} is called.
     */
    public void run() {
        running = true;
        connect();
        try {
            runLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running = false;
        }
    }

    /**
     * Disconnect the client.
     */
    public void disconnect() {
        running = false;
        if (webSocket != null) {
            webSocket.close();
        }
        runLatch.countDown();
        executor.shutdown();
    }

    /**
     * Subscribe to additional contexts dynamically.
     */
    public Map<String, Object> receive(List<String> newContexts) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("contexts", newContexts);
        return execute(Constants.METHOD_RECEIVE, params);
    }

    /**
     * Unsubscribe from contexts.
     */
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
    public Call dial(List<List<Map<String, Object>>> devices, Map<String, Object> options, long timeout) {
        String tag = UUID.randomUUID().toString();

        // Register pending dial BEFORE sending RPC
        CompletableFuture<Call> future = new CompletableFuture<>();
        pendingDials.put(tag, future);

        // Build params
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tag", tag);
        params.put("devices", devices);
        if (options != null) {
            params.putAll(options);
        }

        try {
            // Send RPC - response is just {"code":"200","message":"Dialing"}
            execute(Constants.METHOD_DIAL, params);

            // Wait for calling.call.dial event to resolve the future
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Dial failed: " + e.getMessage(), e);
        } finally {
            pendingDials.remove(tag);
        }
    }

    /**
     * Dial with default timeout of 120 seconds.
     */
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
    public Message sendMessage(String context, String fromNumber, String toNumber,
                               String body, List<String> mediaUrls) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("context", context);
        params.put("from_number", fromNumber);
        params.put("to_number", toNumber);
        if (body != null) {
            params.put("body", body);
        }
        if (mediaUrls != null && !mediaUrls.isEmpty()) {
            params.put("media", mediaUrls);
        }

        Map<String, Object> result = execute(Constants.METHOD_MESSAGING_SEND, params);

        String messageId = result != null ? Objects.toString(result.get("message_id"), null) : null;
        if (messageId == null) {
            messageId = UUID.randomUUID().toString();
        }

        Message message = new Message(messageId);
        message.setContext(context);
        message.setDirection("outbound");
        message.setFromNumber(fromNumber);
        message.setToNumber(toNumber);
        message.setBody(body);
        message.setMedia(mediaUrls);

        // Track by message_id for state routing
        messages.put(messageId, message);
        return message;
    }

    // ── RPC execution ────────────────────────────────────────────────

    /**
     * Execute an RPC method and wait for the response.
     */
    public Map<String, Object> execute(String method, Map<String, Object> params) {
        String requestId = UUID.randomUUID().toString();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", Constants.JSONRPC_VERSION);
        request.put("id", requestId);
        request.put("method", method);

        // Add protocol and project_id to params if we have them
        Map<String, Object> fullParams = new LinkedHashMap<>();
        if (protocol != null) {
            fullParams.put("protocol", protocol);
        }
        fullParams.put("project_id", project);
        if (params != null) {
            fullParams.putAll(params);
        }
        request.put("params", fullParams);

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        try {
            String json = gson.toJson(request);
            log.debug("Sending: %s", json);
            if (webSocket != null && webSocket.isOpen()) {
                webSocket.send(json);
            } else {
                future.completeExceptionally(new RuntimeException("WebSocket not connected"));
            }

            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("RPC execution failed for " + method, e);
            return Collections.emptyMap();
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    /**
     * Execute an RPC method on a call, handling 404/410 gracefully.
     */
    Map<String, Object> executeOnCall(String method, Map<String, Object> params) {
        Map<String, Object> result = execute(method, params);
        Object code = result.get("code");
        if (code != null && Constants.isCallGoneCode(code.toString())) {
            log.info("Call gone during %s (code %s)", method, code);
        }
        return result;
    }

    // ── Connection management ────────────────────────────────────────

    private void connect() {
        try {
            // Permit tests and audit fixtures to point the client at a
            // plain-ws loopback by passing space="ws://127.0.0.1:NNNN" (or
            // "ws://127.0.0.1:NNNN/path"). Production callers pass a bare
            // hostname like "example.signalwire.com", which is upgraded to
            // wss:// here.
            URI uri = space.startsWith("ws://") || space.startsWith("wss://")
                    ? new URI(space)
                    : new URI("wss://" + space);
            webSocket = new InternalWebSocket(uri);
            webSocket.connect();
        } catch (Exception e) {
            log.error("Connection failed", e);
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

        // Authentication
        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("project", project);
        auth.put("token", token);
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
        log.debug("Authenticating with project: %s", project);
        webSocket.send(json);

        // Handle auth response asynchronously
        future.thenAccept(result -> {
            String proto = Objects.toString(result.get("protocol"), null);
            if (proto != null) {
                protocol = proto;
            }
            connected = true;
            reconnectDelay = Constants.RECONNECT_INITIAL_DELAY_MS;
            log.info("Connected to %s (protocol: %s)", space, protocol);
        }).exceptionally(e -> {
            log.error("Authentication failed", (Exception) e);
            return null;
        });
    }

    private void scheduleReconnect() {
        if (!running) return;

        log.info("Reconnecting in %d ms...", reconnectDelay);
        executor.submit(() -> {
            try {
                Thread.sleep(reconnectDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // Exponential backoff
            reconnectDelay = Math.min(
                    (long) (reconnectDelay * Constants.RECONNECT_BACKOFF_MULTIPLIER),
                    Constants.RECONNECT_MAX_DELAY_MS
            );

            if (running) {
                connect();
            }
        });
    }

    private void handleDisconnect() {
        connected = false;

        // Reject all pending request futures
        for (Map.Entry<String, CompletableFuture<Map<String, Object>>> entry : pendingRequests.entrySet()) {
            entry.getValue().completeExceptionally(new RuntimeException("Disconnected"));
        }
        pendingRequests.clear();

        // Reject all pending dial futures
        for (Map.Entry<String, CompletableFuture<Call>> entry : pendingDials.entrySet()) {
            entry.getValue().completeExceptionally(new RuntimeException("Disconnected"));
        }
        pendingDials.clear();

        if (running) {
            scheduleReconnect();
        }
    }

    // ── Message handling ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleMessage(String rawMessage) {
        log.debug("Received: %s", rawMessage);

        Map<String, Object> message;
        try {
            message = gson.fromJson(rawMessage,
                    new TypeToken<Map<String, Object>>() {}.getType());
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
        if (future == null) {
            log.debug("No pending request for id: %s", id);
            return;
        }

        if (message.containsKey("error")) {
            Map<String, Object> error = (Map<String, Object>) message.get("error");
            future.completeExceptionally(new RuntimeException(
                    "RPC error: " + error.getOrDefault("message", "Unknown")));
        } else {
            Map<String, Object> result = (Map<String, Object>) message.getOrDefault("result", Collections.emptyMap());
            future.complete(result != null ? result : Collections.emptyMap());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleEvent(Map<String, Object> message) {
        Map<String, Object> outerParams = (Map<String, Object>) message.getOrDefault("params", Collections.emptyMap());

        RelayEvent event = RelayEvent.fromRawParams(outerParams);
        String eventType = event.getEventType();

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
        Call call = new Call(event.getCallId(), event.getNodeId());
        call.setState(event.getCallState());
        call.setDirection("inbound");
        call.setDevice(event.getDevice());
        call.setClient(this);
        calls.put(call.getCallId(), call);

        if (onCallHandler != null) {
            executor.submit(() -> {
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
            executor.submit(() -> {
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
        Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params", Collections.emptyMap());
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
     * Send a raw JSON-RPC frame on the underlying socket. Production code
     * uses {@link #execute(String, Map)}, which adds project_id/protocol
     * automatically. This helper exists for the porting-sdk RELAY-handshake
     * audit harness, which has to emit a {@code method:"signalwire.event"}
     * frame from inside the on-event callback so the fixture's dispatch
     * counter fires (see SUBAGENT_PLAYBOOK lesson on event-ACK semantics).
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
