/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import com.signalwire.sdk.logging.Logger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Base class for long-running call actions tracked by {@code control_id}.
 *
 * <p>Actions support three completion patterns:
 *
 * <ol>
 *   <li><b>Wait inline</b>: {@code action.waitForCompletion()} blocks until terminal
 *   <li><b>Fire and forget</b>: don't wait, check {@code isDone()} later
 *   <li><b>Callback</b>: pass an {@code onCompleted} callback that fires on terminal state
 * </ol>
 *
 * <p>Subclasses add action-specific sub-commands (pause, resume, volume, etc.).
 */
public class Action {

  private static final Logger log = Logger.getLogger(Action.class);

  private final String controlId;
  private final Call call;
  private final CompletableFuture<RelayEvent> completionFuture;
  private volatile String state;
  private volatile RelayEvent result;
  private volatile boolean done;
  private Consumer<Action> onCompleted;

  public Action(String controlId, Call call) {
    this.controlId = controlId;
    this.call = call;
    this.completionFuture = new CompletableFuture<>();
    this.done = false;
  }

  public String getControlId() {
    return controlId;
  }

  public Call getCall() {
    return call;
  }

  public String getState() {
    return state;
  }

  public RelayEvent getResult() {
    return result;
  }

  public boolean isDone() {
    return done;
  }

  public void setOnCompleted(Consumer<Action> onCompleted) {
    this.onCompleted = onCompleted;
  }

  /**
   * Block until the action reaches a terminal state.
   *
   * @return the terminal event
   */
  public RelayEvent waitForCompletion() {
    try {
      return completionFuture.get();
    } catch (Exception e) {
      return result;
    }
  }

  /**
   * Block until the action reaches a terminal state, with timeout.
   *
   * @param timeoutMs timeout in milliseconds
   * @return the terminal event, or null on timeout
   */
  public RelayEvent waitForCompletion(long timeoutMs) {
    try {
      return completionFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      return result;
    }
  }

  /**
   * Wait for the action to complete, returning the terminal event. Java-idiom name for the
   * reference's {@code Action.wait}: the bare name {@code wait} collides with {@code
   * java.lang.Object.wait()} (final, non-overridable), so this port names it {@code await} and the
   * enumerator's rename table maps {@code await} → {@code wait} (adapter rename, not omission).
   */
  public RelayEvent await() {
    return waitForCompletion();
  }

  /**
   * Wait for the action to complete with a timeout. Java-idiom name for the reference's {@code
   * Action.wait(timeout)} (see {@link #await()} for why the name differs).
   *
   * @param timeoutMs timeout in milliseconds
   * @return the terminal event, or null on timeout
   */
  public RelayEvent await(long timeoutMs) {
    return waitForCompletion(timeoutMs);
  }

  /** Stop the action. */
  public void stop() {
    // Subclasses override with specific stop method
  }

  /**
   * Update state from an incoming event. Resolves the completion future when a terminal state is
   * reached.
   */
  public void updateState(String newState, RelayEvent event) {
    this.state = newState;
    if (isTerminal(newState)) {
      resolve(event);
    }
  }

  /** Resolve the action immediately (e.g., on call-gone 404/410). */
  public void resolve(RelayEvent event) {
    if (!done) {
      this.done = true;
      this.result = event;
      this.completionFuture.complete(event);
      fireOnCompleted();
    }
  }

  /**
   * Check if a state is terminal for this action type. Subclasses may override for custom terminal
   * states.
   */
  protected boolean isTerminal(String actionState) {
    return Constants.isTerminalActionState(actionState);
  }

  private void fireOnCompleted() {
    if (onCompleted != null) {
      try {
        onCompleted.accept(this);
      } catch (Exception e) {
        log.error("Error in onCompleted callback for action " + controlId, e);
      }
    }
  }

  @Override
  public String toString() {
    return String.format(
        "%s{controlId=%s, state=%s, done=%s}", getClass().getSimpleName(), controlId, state, done);
  }

  // ── Concrete action subclasses ───────────────────────────────────

  /** Play action with pause, resume, volume, and stop sub-commands. */
  public static class PlayAction extends Action {
    public PlayAction(String controlId, Call call) {
      super(controlId, call);
    }

    @Override
    public void stop() {
      getCall().executeOnCall(Constants.METHOD_PLAY_STOP, baseParams());
    }

    public void pause() {
      getCall().executeOnCall(Constants.METHOD_PLAY_PAUSE, baseParams());
    }

    public void resume() {
      getCall().executeOnCall(Constants.METHOD_PLAY_RESUME, baseParams());
    }

    public void volume(double volumeDb) {
      Map<String, Object> params = baseParams();
      params.put("volume", volumeDb);
      getCall().executeOnCall(Constants.METHOD_PLAY_VOLUME, params);
    }

    private Map<String, Object> baseParams() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      return params;
    }
  }

  /** Record action with pause, resume, and stop sub-commands. */
  public static class RecordAction extends Action {
    public RecordAction(String controlId, Call call) {
      super(controlId, call);
    }

    @Override
    public void stop() {
      getCall().executeOnCall(Constants.METHOD_RECORD_STOP, baseParams());
    }

    public void pause() {
      getCall().executeOnCall(Constants.METHOD_RECORD_PAUSE, baseParams());
    }

    public void pauseWithBehavior(String behavior) {
      Map<String, Object> params = baseParams();
      params.put("behavior", behavior);
      getCall().executeOnCall(Constants.METHOD_RECORD_PAUSE, params);
    }

    public void resume() {
      getCall().executeOnCall(Constants.METHOD_RECORD_RESUME, baseParams());
    }

    private Map<String, Object> baseParams() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      return params;
    }
  }

  /** Detect action with stop sub-command. */
  public static class DetectAction extends Action {
    public DetectAction(String controlId, Call call) {
      super(controlId, call);
    }

    @Override
    public void stop() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_DETECT_STOP, params);
    }
  }

  /** Collect action with stop and start_input_timers sub-commands. */
  public static class CollectAction extends Action {
    public CollectAction(String controlId, Call call) {
      super(controlId, call);
    }

    @Override
    public void stop() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_COLLECT_STOP, params);
    }

    public void startInputTimers() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_COLLECT_START_INPUT_TIMERS, params);
    }
  }

  /**
   * Play-and-collect action.
   *
   * <p>Shares one {@code control_id} across both play and collect phases. Events arrive as BOTH
   * {@code calling.call.play} and {@code calling.call.collect}. This action only resolves on
   * collect events, NOT play events.
   */
  public static class PlayAndCollectAction extends Action {
    public PlayAndCollectAction(String controlId, Call call) {
      super(controlId, call);
    }

    @Override
    public void stop() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_PLAY_AND_COLLECT_STOP, params);
    }

    public void volume(double volumeDb) {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      params.put("volume", volumeDb);
      getCall().executeOnCall(Constants.METHOD_PLAY_AND_COLLECT_VOLUME, params);
    }

    /**
     * Restart the digit/speech input timers on this standalone collect. Mirrors the reference
     * StandaloneCollectAction.start_input_timers (same wire method as CollectAction).
     */
    public void startInputTimers() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_COLLECT_START_INPUT_TIMERS, params);
    }
  }

  /** Pay action with stop sub-command. */
  public static class PayAction extends Action {
    public PayAction(String controlId, Call call) {
      super(controlId, call);
    }

    @Override
    public void stop() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_PAY_STOP, params);
    }
  }

  /** Send fax action with stop sub-command. */
  public static class SendFaxAction extends Action {
    public SendFaxAction(String controlId, Call call) {
      super(controlId, call);
    }

    @Override
    public void stop() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_SEND_FAX_STOP, params);
    }
  }

  /** Receive fax action with stop sub-command. */
  public static class ReceiveFaxAction extends Action {
    public ReceiveFaxAction(String controlId, Call call) {
      super(controlId, call);
    }

    @Override
    public void stop() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_RECEIVE_FAX_STOP, params);
    }
  }

  /** Tap action with stop sub-command. */
  public static class TapAction extends Action {
    public TapAction(String controlId, Call call) {
      super(controlId, call);
    }

    @Override
    protected boolean isTerminal(String actionState) {
      return Constants.ACTION_STATE_FINISHED.equals(actionState);
    }

    @Override
    public void stop() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_TAP_STOP, params);
    }
  }

  /** Stream action with stop sub-command. */
  public static class StreamAction extends Action {
    public StreamAction(String controlId, Call call) {
      super(controlId, call);
    }

    @Override
    protected boolean isTerminal(String actionState) {
      return Constants.ACTION_STATE_FINISHED.equals(actionState);
    }

    @Override
    public void stop() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_STREAM_STOP, params);
    }
  }

  /** Transcribe action with stop sub-command. */
  public static class TranscribeAction extends Action {
    public TranscribeAction(String controlId, Call call) {
      super(controlId, call);
    }

    @Override
    protected boolean isTerminal(String actionState) {
      return Constants.ACTION_STATE_FINISHED.equals(actionState);
    }

    @Override
    public void stop() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_TRANSCRIBE_STOP, params);
    }
  }

  /** AI action with stop sub-command. */
  public static class AiAction extends Action {
    public AiAction(String controlId, Call call) {
      super(controlId, call);
    }

    @Override
    protected boolean isTerminal(String actionState) {
      return Constants.ACTION_STATE_FINISHED.equals(actionState)
          || Constants.ACTION_STATE_ERROR.equals(actionState);
    }

    @Override
    public void stop() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_AI_STOP, params);
    }
  }
}
