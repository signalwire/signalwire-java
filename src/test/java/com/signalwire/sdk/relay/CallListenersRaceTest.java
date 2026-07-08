/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Regression stress test for the {@code Call.eventListeners} race.
 *
 * <p>The defect: {@code eventListeners} was a plain {@code ArrayList}. Under real RELAY traffic,
 * {@link Call#dispatchEvent} iterates the list on the client's event thread while user threads
 * mutate it — {@link Call#on} adds, and {@link Call#waitFor}'s teardown ({@code finally} block)
 * removes its one-shot listener. A mutation during iteration throws {@code
 * ConcurrentModificationException} out of {@code dispatchEvent} (the per-listener try/catch only
 * guards {@code listener.accept}, not the iterator), killing event dispatch for the call.
 *
 * <p>This test drives only production paths: one thread hammers {@code dispatchEvent} while waiter
 * threads cycle {@code waitFor} (internal {@code on()} add + {@code finally} remove) plus extra
 * {@code on()} registrations on the same {@code Call}. Against the {@code ArrayList} implementation
 * it fails within milliseconds; with a thread-safe listener list it must pass every repetition.
 */
class CallListenersRaceTest {

  private static RelayEvent answeredEvent() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("call_state", Constants.CALL_STATE_ANSWERED);
    return new RelayEvent.CallStateEvent(Constants.EVENT_CALL_STATE, 0.0, params);
  }

  @RepeatedTest(20)
  void concurrentOnAndWaitForTeardownDuringDispatch() throws Exception {
    final Call call = new Call("race-call", "node-race");

    final int waiterThreads = 4;
    final int cyclesPerWaiter = 1500;
    final AtomicBoolean waitersDone = new AtomicBoolean(false);
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final CountDownLatch start = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(waiterThreads);
    final RelayEvent answered = answeredEvent();

    // Event thread: iterates eventListeners on every dispatch, like RelayClient
    // routing live calling.call.state traffic to the call.
    Thread dispatcher =
        new Thread(
            () -> {
              try {
                start.await();
                while (!waitersDone.get() && failure.get() == null) {
                  call.dispatchEvent(answered);
                }
              } catch (Exception t) {
                failure.compareAndSet(null, t);
              }
            },
            "race-dispatcher");

    // User threads: the real mutation paths — waitFor() adds its one-shot listener
    // via on() and removes it in its finally teardown; plus extra on() churn.
    Thread[] waiters = new Thread[waiterThreads];
    for (int i = 0; i < waiterThreads; i++) {
      waiters[i] =
          new Thread(
              () -> {
                try {
                  start.await();
                  for (int c = 0; c < cyclesPerWaiter && failure.get() == null; c++) {
                    call.setState(Constants.CALL_STATE_CREATED);
                    // Adds a listener, blocks until the dispatcher's answered event
                    // resolves it, then removes the listener in its finally block.
                    call.waitFor(Constants.CALL_STATE_ANSWERED, 2000);
                    call.on(event -> {});
                  }
                } catch (Exception t) {
                  failure.compareAndSet(null, t);
                } finally {
                  done.countDown();
                }
              },
              "race-waiter-" + i);
    }

    dispatcher.start();
    for (Thread w : waiters) {
      w.start();
    }
    start.countDown();

    assertTrue(done.await(120, TimeUnit.SECONDS), "waiter threads did not finish in time");
    waitersDone.set(true);
    dispatcher.join(TimeUnit.SECONDS.toMillis(30));
    assertFalse(dispatcher.isAlive(), "dispatcher thread did not stop");

    Throwable t = failure.get();
    if (t != null) {
      fail("concurrent listener mutation during dispatchEvent threw: " + t, t);
    }
  }
}
