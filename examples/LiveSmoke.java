/**
 * Live smoke driver (plan §6.5) — exercises the real SignalWire platform.
 *
 * <p>No-ops unless {@code SWSDK_LIVE_TESTS=1} AND real creds are present
 * ({@code SIGNALWIRE_PROJECT_ID} / {@code SIGNALWIRE_API_TOKEN} / {@code SIGNALWIRE_SPACE}),
 * so it is safe to invoke anywhere; the live-smoke CI workflow sets those from repo secrets and
 * skips when they are absent. Drives four things against the real platform:
 *
 * <ol>
 *   <li>auth + one REST list (phone numbers),
 *   <li>one SWML render (an AgentBase document),
 *   <li>one RELAY connect (WebSocket handshake, then close).
 * </ol>
 *
 * Exits non-zero on any failure so the workflow reds; prints a clear skip line when not armed.
 */
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.relay.RelayClient;
import com.signalwire.sdk.rest.RestClient;

public class LiveSmoke {

  public static void main(String[] args) throws Exception {
    if (!"1".equals(System.getenv("SWSDK_LIVE_TESTS"))) {
      System.out.println("[live-smoke] SWSDK_LIVE_TESTS!=1 — skipped (not armed).");
      return;
    }
    String project = System.getenv("SIGNALWIRE_PROJECT_ID");
    String token = System.getenv("SIGNALWIRE_API_TOKEN");
    String space = System.getenv("SIGNALWIRE_SPACE");
    if (isBlank(project) || isBlank(token) || isBlank(space)) {
      System.out.println("[live-smoke] creds absent — skipped (SIGNALWIRE_PROJECT_ID/API_TOKEN/SPACE).");
      return;
    }

    // 1. auth + one REST list.
    System.out.println("[live-smoke] REST: listing phone numbers ...");
    var rest = RestClient.builder().build();
    var numbers = rest.phoneNumbers().list();
    System.out.println("[live-smoke]   REST OK (" + numbers.size() + " field(s) in list response).");

    // 2. one SWML render.
    System.out.println("[live-smoke] SWML: rendering an AgentBase document ...");
    var agent = AgentBase.builder().name("live-smoke").route("/").build();
    agent.promptAddSection("Role", "You are a smoke-test assistant.");
    String swml = agent.renderSwmlJson("https://example.invalid/");
    if (isBlank(swml) || !swml.contains("version")) {
      throw new IllegalStateException("SWML render produced no document");
    }
    System.out.println("[live-smoke]   SWML OK (" + swml.length() + " bytes).");

    // 3. one RELAY connect (handshake then close).
    System.out.println("[live-smoke] RELAY: connecting ...");
    try (var relay = RelayClient.builder().build()) {
      relay.connect();
      System.out.println("[live-smoke]   RELAY OK (connected; closing).");
    }

    System.out.println("[live-smoke] all checks passed.");
  }

  private static boolean isBlank(String s) {
    return s == null || s.isEmpty();
  }
}
