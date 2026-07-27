package com.signalwire.sdk.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signalwire.sdk.relay.RelayMockTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the mock READINESS contract: a mock server that answers {@code /__mock__/health} with HTTP
 * 200 is only usable when it declares that it actually loaded its contract sources.
 *
 * <p>Why this test exists. Both harnesses used to treat "the health JSON mentions {@code
 * specs_loaded} / {@code schemas_loaded}" as ready. That is satisfiable by a mock that loaded
 * NOTHING: {@code mock_signalwire} emits {@code {"status":"ok","specs_loaded":0,...,
 * "total_routes":0}} — HTTP 200, well-formed, and 404 on every route — when it cannot find its
 * {@code rest-apis/} spec tree. That happens whenever {@code python -m mock_signalwire} resolves to
 * a copy other than this repo's (a stale install in an unrelated venv), which in turn happens
 * whenever adjacency discovery finds no sibling {@code porting-sdk} and the PYTHONPATH injection is
 * skipped — e.g. a git worktree checked out outside {@code ~/src/}.
 *
 * <p>The observed cost of the old contract was a full suite reporting 547 failures, every one of
 * them {@code "no route for <METHOD> <path>"} raised as a {@code RestError} from SDK code. That
 * reads as a mass SDK/routing regression and invites diagnoses ranging from a routing bug to a
 * TOCTOU port race between concurrent harnesses. It was none of those: the environment handed the
 * suite an empty server and the readiness probe waved it through.
 *
 * <p>These are pure predicate tests over the health payload — no server, no ports, no sleeps — so
 * they are deterministic and safe under the suite's class-parallel execution.
 */
@DisplayName("mock readiness contract")
class MockHealthContractTest {

  /** Verbatim health body from a mock_signalwire that could not find its rest-apis/ spec tree. */
  private static final String EMPTY_REST_HEALTH =
      "{\"status\":\"ok\",\"specs_loaded\":0,\"spec_load_errors\":[{\"spec\":\"calling\","
          + "\"error\":\"spec not found: /some/venv/lib/rest-apis/calling/openapi.yaml\"}],"
          + "\"total_routes\":0}";

  /** Verbatim health body from a correctly resolved mock_signalwire. */
  private static final String GOOD_REST_HEALTH =
      "{\"status\":\"ok\",\"specs_loaded\":14,\"spec_load_errors\":[],\"total_routes\":236}";

  @Nested
  @DisplayName("REST (mock_signalwire)")
  class Rest {

    @Test
    @DisplayName("a mock serving 0 specs / 0 routes is NOT ready")
    void emptyMockIsRefused() {
      assertFalse(
          MockTest.healthIsUsable(EMPTY_REST_HEALTH),
          "a mock_signalwire reporting specs_loaded:0 answers 200 but 404s every route; "
              + "accepting it as ready is what turned a broken environment into 547 bogus "
              + "SDK failures");
    }

    @Test
    @DisplayName("a fully loaded mock IS ready")
    void loadedMockIsAccepted() {
      assertTrue(MockTest.healthIsUsable(GOOD_REST_HEALTH));
    }

    @Test
    @DisplayName("specs loaded but ZERO routes registered is NOT ready")
    void specsWithoutRoutesIsRefused() {
      assertFalse(
          MockTest.healthIsUsable(
              "{\"status\":\"ok\",\"specs_loaded\":14,\"spec_load_errors\":[],\"total_routes\":0}"),
          "specs that parse but register no routes still 404 everything");
    }

    @Test
    @DisplayName("routes registered but ZERO specs loaded is NOT ready")
    void routesWithoutSpecsIsRefused() {
      assertFalse(
          MockTest.healthIsUsable(
              "{\"status\":\"ok\",\"specs_loaded\":0,\"spec_load_errors\":[],"
                  + "\"total_routes\":236}"));
    }

    @Test
    @DisplayName("mentioning the key is not enough — the OLD contract's exact hole")
    void mereKeyPresenceIsNotReadiness() {
      // The superseded probe was `text.contains("\"specs_loaded\"")`. Every string below
      // satisfies that and must still be refused.
      assertFalse(MockTest.healthIsUsable("{\"specs_loaded\":0,\"total_routes\":0}"));
      assertFalse(MockTest.healthIsUsable("{\"specs_loaded\":\"14\",\"total_routes\":\"236\"}"));
      assertFalse(MockTest.healthIsUsable("\"specs_loaded\" is a field name"));
    }

    @Test
    @DisplayName("a negative count is NOT ready")
    void negativeCountIsRefused() {
      assertFalse(
          MockTest.healthIsUsable("{\"specs_loaded\":-1,\"total_routes\":-1}"),
          "a negative count is nonsense, not readiness");
    }

    @Test
    @DisplayName("a non-mock server answering 200 is NOT ready")
    void foreignServerIsRefused() {
      assertFalse(MockTest.healthIsUsable("{\"status\":\"ok\"}"));
      assertFalse(MockTest.healthIsUsable("<html><body>hello</body></html>"));
      assertFalse(MockTest.healthIsUsable(""));
      assertFalse(MockTest.healthIsUsable(null));
    }

    @Test
    @DisplayName("whitespace around the value does not change the verdict")
    void whitespaceTolerant() {
      assertTrue(MockTest.healthIsUsable("{\"specs_loaded\" : 14 , \"total_routes\" : 236 }"));
      assertFalse(MockTest.healthIsUsable("{\"specs_loaded\" : 0 , \"total_routes\" : 0 }"));
    }
  }

  @Nested
  @DisplayName("RELAY (mock_relay)")
  class Relay {

    @Test
    @DisplayName("a mock serving 0 schemas is NOT ready")
    void emptyMockIsRefused() {
      assertFalse(RelayMockTest.healthIsUsable("{\"status\":\"ok\",\"schemas_loaded\":0}"));
    }

    @Test
    @DisplayName("a fully loaded mock IS ready")
    void loadedMockIsAccepted() {
      assertTrue(RelayMockTest.healthIsUsable("{\"status\":\"ok\",\"schemas_loaded\":42}"));
    }

    @Test
    @DisplayName("mentioning the key is not enough — the OLD contract's exact hole")
    void mereKeyPresenceIsNotReadiness() {
      assertFalse(RelayMockTest.healthIsUsable("{\"schemas_loaded\":0}"));
      assertFalse(RelayMockTest.healthIsUsable("\"schemas_loaded\" is a field name"));
    }

    @Test
    @DisplayName("a non-mock server answering 200 is NOT ready")
    void foreignServerIsRefused() {
      assertFalse(RelayMockTest.healthIsUsable("{\"status\":\"ok\"}"));
      assertFalse(RelayMockTest.healthIsUsable(null));
    }
  }
}
