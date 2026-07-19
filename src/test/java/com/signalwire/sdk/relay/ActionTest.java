package com.signalwire.sdk.relay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** Tests for RELAY Action and subclasses. */
class ActionTest {

  private Call dummyCall() {
    return new Call("call-1", "node-1");
  }

  private RelayEvent dummyEvent() {
    return new RelayEvent.CallStateEvent(
        Constants.EVENT_CALL_STATE,
        0.0,
        Map.of("call_id", "call-1", "node_id", "node-1", "call_state", Constants.CALL_STATE_ENDED));
  }

  @Test
  void testActionCreation() {
    Action action = new Action("ctrl-1", dummyCall());
    assertEquals("ctrl-1", action.getControlId());
    assertFalse(action.isDone());
    assertNull(action.getState());
  }

  @Test
  void testActionUpdateState() {
    Action action = new Action("ctrl-1", dummyCall());
    action.updateState(Constants.ACTION_STATE_PLAYING, dummyEvent());
    assertEquals(Constants.ACTION_STATE_PLAYING, action.getState());
    assertFalse(action.isDone());
  }

  @Test
  void testActionTerminalState() {
    Action action = new Action("ctrl-1", dummyCall());
    action.updateState(Constants.ACTION_STATE_FINISHED, dummyEvent());
    assertEquals(Constants.ACTION_STATE_FINISHED, action.getState());
    assertTrue(action.isDone());
  }

  @Test
  void testActionResolve() {
    Action action = new Action("ctrl-1", dummyCall());
    action.resolve(dummyEvent());
    assertTrue(action.isDone());
  }

  @Test
  void testActionTerminalStates() {
    assertTrue(Constants.isTerminalActionState(Constants.ACTION_STATE_FINISHED));
    assertTrue(Constants.isTerminalActionState(Constants.ACTION_STATE_ERROR));
    assertTrue(Constants.isTerminalActionState(Constants.ACTION_STATE_NO_INPUT));
    assertTrue(Constants.isTerminalActionState(Constants.ACTION_STATE_NO_MATCH));
    assertFalse(Constants.isTerminalActionState(Constants.ACTION_STATE_PLAYING));
    assertFalse(Constants.isTerminalActionState(Constants.ACTION_STATE_PAUSED));
    assertFalse(Constants.isTerminalActionState(Constants.ACTION_STATE_RECORDING));
  }

  @Test
  void testActionStates() {
    assertEquals("playing", Constants.ACTION_STATE_PLAYING);
    assertEquals("paused", Constants.ACTION_STATE_PAUSED);
    assertEquals("finished", Constants.ACTION_STATE_FINISHED);
    assertEquals("error", Constants.ACTION_STATE_ERROR);
    assertEquals("no_input", Constants.ACTION_STATE_NO_INPUT);
    assertEquals("no_match", Constants.ACTION_STATE_NO_MATCH);
    assertEquals("recording", Constants.ACTION_STATE_RECORDING);
  }

  @Test
  void testPlayAndCollectAction() {
    var action = new Action.PlayAndCollectAction("ctrl-1", dummyCall());
    assertEquals("ctrl-1", action.getControlId());
    assertFalse(action.isDone());
  }

  @Test
  void testPlayAction() {
    var action = new Action.PlayAction("ctrl-1", dummyCall());
    assertEquals("ctrl-1", action.getControlId());
    assertFalse(action.isDone());
  }

  @Test
  void testRecordAction() {
    var action = new Action.RecordAction("ctrl-1", dummyCall());
    assertEquals("ctrl-1", action.getControlId());
    assertFalse(action.isDone());
  }

  @Test
  void testActionGetResult() {
    Action action = new Action("ctrl-1", dummyCall());
    assertNull(action.getResult());
    action.updateState(Constants.ACTION_STATE_FINISHED, dummyEvent());
    assertNotNull(action.getResult());
  }

  @Test
  void testActionResolveOnlyOnce() {
    Action action = new Action("ctrl-1", dummyCall());
    action.resolve(dummyEvent());
    assertTrue(action.isDone());
    // Second resolve should not throw
    action.resolve(dummyEvent());
    assertTrue(action.isDone());
  }

  @Test
  void testActionOnCompleted() {
    Action action = new Action("ctrl-1", dummyCall());
    var called = new java.util.concurrent.atomic.AtomicBoolean(false);
    action.setOnCompleted(a -> called.set(true));
    action.resolve(dummyEvent());
    assertTrue(called.get());
  }

  @Test
  void testActionGetCall() {
    Call call = dummyCall();
    Action action = new Action("ctrl-1", call);
    assertSame(call, action.getCall());
  }

  // ── Python-surface await() (= Action.wait) ───────────────────────

  @Test
  void testAwaitReturnsTerminalEvent() throws Exception {
    Action action = new Action("ctrl-1", dummyCall());
    RelayEvent terminal = dummyEvent();
    Thread producer =
        new Thread(
            () -> {
              try {
                Thread.sleep(30);
              } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
              }
              action.updateState(Constants.ACTION_STATE_FINISHED, terminal);
            });
    producer.start();
    RelayEvent got = action.await();
    producer.join();
    assertSame(terminal, got);
    assertTrue(action.isDone());
  }

  @Test
  void testAwaitWithTimeoutReturnsNullWhenNotDone() {
    Action action = new Action("ctrl-1", dummyCall());
    assertNull(action.await(50));
  }

  /**
   * waitForCompletion() must NOT swallow an interrupt: when the waiting thread is interrupted while
   * blocked, the method re-asserts the thread's interrupt status so cancellation propagates. Prior
   * bug (Action.java:88-108): a broad {@code catch (Exception)} cleared the interrupt flag,
   * silently dropping cancellation. This test goes RED under that bug (flag observed false) and
   * GREEN once the interrupt is re-set.
   */
  @Test
  void testWaitForCompletionPreservesInterrupt() throws InterruptedException {
    Action action = new Action("ctrl-1", dummyCall()); // never resolves → the get() blocks
    final boolean[] interruptSeen = {false};
    Thread waiter =
        new Thread(
            () -> {
              action.waitForCompletion();
              // After returning, the interrupt status must still be set.
              interruptSeen[0] = Thread.currentThread().isInterrupted();
            });
    waiter.start();
    Thread.sleep(50); // let it enter the blocking get()
    waiter.interrupt();
    waiter.join(2000);
    assertFalse(waiter.isAlive(), "waiter should have unblocked on interrupt");
    assertTrue(interruptSeen[0], "interrupt status must be preserved after waitForCompletion()");
  }

  /** Same interrupt-preservation contract for the timeout overload. */
  @Test
  void testWaitForCompletionWithTimeoutPreservesInterrupt() throws InterruptedException {
    Action action = new Action("ctrl-1", dummyCall());
    final boolean[] interruptSeen = {false};
    Thread waiter =
        new Thread(
            () -> {
              action.waitForCompletion(10_000);
              interruptSeen[0] = Thread.currentThread().isInterrupted();
            });
    waiter.start();
    Thread.sleep(50);
    waiter.interrupt();
    waiter.join(2000);
    assertFalse(waiter.isAlive(), "waiter should have unblocked on interrupt");
    assertTrue(
        interruptSeen[0], "interrupt status must be preserved after waitForCompletion(timeout)");
  }
}
