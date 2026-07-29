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

  /**
   * The client-generated identifier this action was dispatched under. The server echoes it on every
   * related event, which is how concurrent actions on one call are told apart.
   *
   * @return the control id.
   */
  public String getControlId() {
    return controlId;
  }

  /**
   * The call this action is running on.
   *
   * @return the owning call.
   */
  public Call getCall() {
    return call;
  }

  /**
   * The most recent state reported for this action, e.g. {@code playing}, {@code recording}, {@code
   * finished}. Updated as events arrive.
   *
   * @return the last known state, or {@code null} before the first event.
   */
  public String getState() {
    return state;
  }

  /**
   * The terminal event that completed this action, carrying whatever result it produced (a
   * recording URL, a collected digit string, a detection outcome).
   *
   * @return the terminal event, or {@code null} while the action is still running.
   */
  public RelayEvent getResult() {
    return result;
  }

  /**
   * Whether the action has reached a terminal state. Method form of the same flag {@link
   * #getCompleted()} reads.
   *
   * @return {@code true} once the action has completed.
   */
  public boolean isDone() {
    return done;
  }

  /**
   * Whether the action has reached its terminal state — the reference's {@code completed} flag,
   * which starts false and is set true exactly once by the terminal-state resolve (call.py:90, then
   * :102 inside {@code _complete}). The reference exposes BOTH this attribute and an {@code
   * is_done()} method over the same state, so this port does too; {@link #isDone()} is the method
   * form and reads the identical field.
   *
   * @return true once the action has completed.
   */
  public boolean getCompleted() {
    return done;
  }

  /**
   * Register a callback to fire when the action completes.
   *
   * <p>Safe against the genuine race where the terminal event lands on the RELAY reader thread
   * before this registration: if the action has ALREADY resolved, the callback fires immediately
   * rather than being silently dropped. It fires exactly once either way.
   *
   * @param onCompleted the callback, invoked with this action.
   */
  public void setOnCompleted(Consumer<Action> onCompleted) {
    // If the action has ALREADY resolved (the terminal event landed on the RELAY
    // reader thread before this registration — a genuine race for a caller that
    // sets the callback after dispatching the action), fire immediately so a late
    // registration is never silently dropped. Otherwise store it for resolve() to
    // fire. Guarded on `done` (set inside resolve()) so exactly one fire happens.
    synchronized (this) {
      this.onCompleted = onCompleted;
      if (done && onCompleted != null) {
        Consumer<Action> cb = this.onCompleted;
        this.onCompleted = null; // prevent a double-fire if resolve() also races
        cb.accept(this);
      }
    }
  }

  /**
   * Block until the action reaches a terminal state.
   *
   * @return the terminal event
   */
  public RelayEvent waitForCompletion() {
    try {
      return completionFuture.get();
    } catch (InterruptedException e) {
      // Never swallow an interrupt: a blocked wait that is interrupted must
      // re-assert the thread's interrupt status so callers up the stack can
      // observe cancellation (JDK/Guava idiom — "Don't swallow InterruptedException").
      Thread.currentThread().interrupt();
      return result;
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
    } catch (InterruptedException e) {
      // Re-assert the interrupt status rather than swallowing it (see waitForCompletion()).
      Thread.currentThread().interrupt();
      return result;
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
   * <p>The unit is SECONDS and the type is boxed {@code Double}, matching the reference's {@code
   * timeout: float | None = None} and the port's {@code RequestOptions.timeout} spelling; {@code
   * null} means "wait indefinitely". {@link #waitForCompletion(long)} takes milliseconds.
   *
   * @param timeout timeout in seconds ({@code null} = no timeout)
   * @return the terminal event, or null on timeout
   */
  public RelayEvent await(Double timeout) {
    if (timeout == null || timeout <= 0.0) {
      return waitForCompletion();
    }
    return waitForCompletion((long) (timeout * 1000.0));
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
    // Set `done` + fire the callback atomically vs setOnCompleted(): the terminal
    // event arrives on the RELAY reader thread and can race a caller registering
    // its callback on another thread. The lock guarantees exactly one of the two
    // fires the callback (resolve() here, or setOnCompleted() when it observes
    // done==true), never zero and never twice.
    synchronized (this) {
      if (done) {
        return;
      }
      this.done = true;
      this.result = event;
      // Fire onCompleted BEFORE completing the future. The Python reference is
      // async single-threaded, so its `_done.set_result()` then `_on_completed()`
      // ordering guarantees any `await`-er resumes only after the callback has run.
      // Firing first preserves that guarantee (callback observed no later than
      // completion) race-free.
      Consumer<Action> cb = this.onCompleted;
      this.onCompleted = null; // one-shot: a later setOnCompleted sees done and fires itself
      if (cb != null) {
        try {
          cb.accept(this);
        } catch (Exception e) {
          log.error("Error in onCompleted callback for action " + controlId, e);
        }
      }
    }
    this.completionFuture.complete(event);
  }

  /**
   * Check if a state is terminal for this action type. Subclasses may override for custom terminal
   * states.
   */
  protected boolean isTerminal(String actionState) {
    return Constants.isTerminalActionState(actionState);
  }

  /**
   * A short diagnostic rendering: the concrete action subclass, its control id, its last known
   * state, and whether it has completed.
   *
   * @return the diagnostic string.
   */
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

    /** Stop playback, sending {@code calling.play.stop} for this control id. */
    @Override
    public void stop() {
      getCall().executeOnCall(Constants.METHOD_PLAY_STOP, baseParams());
    }

    /** Pause playback. Mirrors the reference PlayAction.pause. */
    public void pause() {
      pause(null);
    }

    /**
     * Pause playback with an optional {@code behavior} hint. Mirrors the reference {@code
     * PlayAction.pause(behavior: str | None)} — when {@code behavior} is non-null it rides in the
     * request params.
     *
     * @param behavior optional pause behavior; may be {@code null}
     */
    public void pause(String behavior) {
      Map<String, Object> params = baseParams();
      if (behavior != null) {
        params.put("behavior", behavior);
      }
      getCall().executeOnCall(Constants.METHOD_PLAY_PAUSE, params);
    }

    /** Resume playback paused by {@link #pause()}. */
    public void resume() {
      getCall().executeOnCall(Constants.METHOD_PLAY_RESUME, baseParams());
    }

    /**
     * Adjust playback volume mid-play.
     *
     * @param volumeDb the new volume in decibels, relative to the source level — negative
     *     attenuates, positive amplifies.
     */
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

    /**
     * Stop the recording, sending {@code calling.record.stop} for this control id. The action then
     * resolves with the terminal event carrying the recording's URL, duration, and size.
     */
    @Override
    public void stop() {
      getCall().executeOnCall(Constants.METHOD_RECORD_STOP, baseParams());
    }

    /** Pause the recording. Mirrors the reference RecordAction.pause. */
    public void pause() {
      pause(null);
    }

    /**
     * Pause the recording with an optional {@code behavior} hint. Mirrors the reference {@code
     * RecordAction.pause(behavior: str | None)} — when {@code behavior} is non-null it rides in the
     * request params.
     *
     * @param behavior optional pause behavior; may be {@code null}
     */
    public void pause(String behavior) {
      Map<String, Object> params = baseParams();
      if (behavior != null) {
        params.put("behavior", behavior);
      }
      getCall().executeOnCall(Constants.METHOD_RECORD_PAUSE, params);
    }

    /** Resume the recording paused by {@link #pause()}. */
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

    /** Stop detection, sending {@code calling.detect.stop} for this control id. */
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

    /** Stop collecting input, sending {@code calling.collect.stop} for this control id. */
    @Override
    public void stop() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      getCall().executeOnCall(Constants.METHOD_COLLECT_STOP, params);
    }

    /**
     * (Re)start the digit and speech input timers. Use it when a collect was dispatched with its
     * timers held off until some other point in the flow — for instance after a prompt has finished
     * playing — so the caller is not timed out while still being spoken to.
     */
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

    /**
     * Stop the play-and-collect operation, sending {@code calling.play_and_collect.stop} for this
     * control id.
     */
    @Override
    public void stop() {
      getCall().executeOnCall(Constants.METHOD_PLAY_AND_COLLECT_STOP, baseParams());
    }

    /** Pause the play-and-collect operation. Mirrors the reference CollectAction.pause. */
    public void pause() {
      pause(null);
    }

    /**
     * Pause the play-and-collect operation with an optional {@code behavior} hint. Mirrors the
     * reference {@code CollectAction.pause(behavior: str | None)} — when {@code behavior} is
     * non-null it rides in the request params.
     *
     * @param behavior optional pause behavior; may be {@code null}
     */
    public void pause(String behavior) {
      Map<String, Object> params = baseParams();
      if (behavior != null) {
        params.put("behavior", behavior);
      }
      getCall().executeOnCall(Constants.METHOD_PLAY_AND_COLLECT_PAUSE, params);
    }

    /** Resume the play-and-collect operation. Mirrors the reference CollectAction.resume. */
    public void resume() {
      getCall().executeOnCall(Constants.METHOD_PLAY_AND_COLLECT_RESUME, baseParams());
    }

    /**
     * Adjust the volume of the play phase mid-operation.
     *
     * @param volumeDb the new volume in decibels, relative to the source level.
     */
    public void volume(double volumeDb) {
      Map<String, Object> params = baseParams();
      params.put("volume", volumeDb);
      getCall().executeOnCall(Constants.METHOD_PLAY_AND_COLLECT_VOLUME, params);
    }

    private Map<String, Object> baseParams() {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("node_id", getCall().getNodeId().orElse(null));
      params.put("call_id", getCall().getCallId());
      params.put("control_id", getControlId());
      return params;
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

    /** Stop the payment session, sending {@code calling.pay.stop} for this control id. */
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

    /** Stop the outbound fax, sending {@code calling.send_fax.stop} for this control id. */
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

    /** Stop receiving the fax, sending {@code calling.receive_fax.stop} for this control id. */
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

    /**
     * Stop the media tap, sending {@code calling.tap.stop} for this control id. Media stops being
     * forwarded to the tap destination.
     */
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

    /** Stop the media stream, sending {@code calling.stream.stop} for this control id. */
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

    /** Stop transcription, sending {@code calling.transcribe.stop} for this control id. */
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

    /** Stop the AI agent on this call, sending {@code calling.ai.stop} for this control id. */
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
