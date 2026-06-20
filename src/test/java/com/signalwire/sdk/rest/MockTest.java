/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MockTest is the Java port of the porting-sdk mock_signalwire HTTP-server harness. It mirrors the
 * Go pilot at {@code signalwire-go/pkg/rest/internal/mocktest/mocktest.go} and the Python conftest
 * fixtures (signalwire_client + mock).
 *
 * <p>Lifecycle is per-process: the first {@link #newClient()} call probes {@code
 * http://127.0.0.1:<port>/__mock__/health} and either confirms a running server or starts one as a
 * detached subprocess. Each test gets a freshly reset journal/scenario state (callers should invoke
 * {@link Harness#reset()} at the top of each test).
 *
 * <p>Default port is 8767 (Java's slot in the parallel rollout); override via {@code
 * MOCK_SIGNALWIRE_PORT} in the environment.
 */
public final class MockTest {

  private static final Gson GSON = new Gson();
  private static final int DEFAULT_PORT = 8767;
  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

  private static final Object STATE_LOCK = new Object();
  private static volatile Harness sharedHarness;
  private static volatile Throwable startupFailure;

  private MockTest() {
    // static helper
  }

  /**
   * Walk this repo's working directory upward looking for an adjacent {@code
   * porting-sdk/test_harness/<name>/<name>/__init__.py}. The adjacency contract is "porting-sdk
   * lives next to signalwire-java in ~/src/", so a fresh clone of either repo can find the mock
   * harness with no prior {@code pip install -e}.
   *
   * <p>Returns the absolute path to the directory containing the Python package (i.e. the value to
   * put on PYTHONPATH so that {@code python -m <name>} resolves), or {@code null} when no adjacent
   * porting-sdk is reachable.
   *
   * <p>Java's classloader can't reliably hand us the test source file's location (tests run from
   * compiled classes / test JAR), so we anchor the walk at {@code user.dir}, which Gradle sets to
   * the project root for the test JVM.
   */
  static String discoverPortingSdkPackage(String name) {
    String userDir = System.getProperty("user.dir");
    if (userDir == null || userDir.isEmpty()) {
      return null;
    }
    Path dir = Path.of(userDir).toAbsolutePath();
    while (dir != null) {
      Path parent = dir.getParent();
      if (parent == null) break;
      Path candidate = parent.resolve("porting-sdk").resolve("test_harness").resolve(name);
      Path init = candidate.resolve(name).resolve("__init__.py");
      if (Files.isRegularFile(init)) {
        return candidate.toString();
      }
      dir = parent;
    }
    return null;
  }

  /** Wraps the live mock server and exposes journal/scenario controls. */
  public static final class Harness {

    private final String url;
    private final int port;
    private final java.net.http.HttpClient http;

    /**
     * The unique random project this harness's client authenticates with ({@code test_proj_<hex>}).
     * Tests that assert on the AccountSid embedded in a LAML path read it from {@link #project()}
     * instead of hard-coding {@code test_proj}. Empty on an unscoped/raw harness.
     */
    private String project = "";

    /**
     * When set, {@link #journal()}/{@link #last()} return only the requests THIS test's client made
     * — identified by its {@code Authorization} header (Basic {@code project:token}, with a
     * per-test random project). REST is pure request/response, so the mock needs no session
     * handshake: each request is self-identifying via its auth header, and filtering the shared
     * global journal by that header makes the suite safe under parallelism with no SDK change and
     * no mock-server change. Empty => unscoped (legacy view, returns every entry — only correct
     * under serial execution).
     */
    private String authHeader = "";

    private Harness(String url, int port) {
      this.url = url;
      this.port = port;
      this.http =
          java.net.http.HttpClient.newBuilder()
              .version(java.net.http.HttpClient.Version.HTTP_1_1)
              .connectTimeout(HTTP_TIMEOUT)
              .build();
    }

    public String url() {
      return url;
    }

    public int port() {
      return port;
    }

    /** The per-test random project this harness's client authenticates as. */
    public String project() {
      return project;
    }

    /** Fetch the raw global journal (all clients' requests, arrival order). */
    private List<JournalEntry> rawJournal() {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url + "/__mock__/journal"))
              .timeout(HTTP_TIMEOUT)
              .GET()
              .build();
      try {
        HttpResponse<String> resp =
            http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
          throw new IllegalStateException(
              "MockTest: GET /__mock__/journal returned " + resp.statusCode());
        }
        return GSON.fromJson(resp.body(), new TypeToken<List<JournalEntry>>() {}.getType());
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("MockTest: journal fetch failed: " + e, e);
      }
    }

    /**
     * Returns this client's recorded requests in arrival order. Scoped to this harness's {@code
     * authHeader} when set (so a parallel test never sees another test's requests); an unscoped
     * harness sees the whole journal.
     */
    public List<JournalEntry> journal() {
      List<JournalEntry> entries = rawJournal();
      if (authHeader == null || authHeader.isEmpty()) {
        return entries;
      }
      java.util.List<JournalEntry> mine = new java.util.ArrayList<>();
      for (JournalEntry e : entries) {
        if (e.headers != null && authHeader.equals(e.headers.get("authorization"))) {
          mine.add(e);
        }
      }
      return mine;
    }

    /**
     * Returns the most recent journal entry. Throws if the journal is empty — every test that
     * exercises the SDK should produce at least one entry.
     */
    public JournalEntry last() {
      List<JournalEntry> entries = journal();
      if (entries == null || entries.isEmpty()) {
        throw new AssertionError(
            "MockTest: journal is empty - the SDK call did not reach the mock server");
      }
      return entries.get(entries.size() - 1);
    }

    /**
     * Clears journal + scenarios on the mock server. A scoped harness leaves the shared journal
     * alone (it only ever reads its own entries, identified by auth header, so there is nothing to
     * clear and a global wipe would race a concurrent test). Unscoped harnesses do the legacy
     * global reset.
     */
    public void reset() {
      if (authHeader != null && !authHeader.isEmpty()) {
        return;
      }
      postNoBody("/__mock__/journal/reset");
      postNoBody("/__mock__/scenarios/reset");
    }

    /**
     * Stages a one-shot response override for the named operation. The status + body returned here
     * will be served the next time the route is hit; subsequent hits fall back to spec synthesis.
     *
     * <p>The override is scoped to THIS client's auth header (REST's session key) via {@code
     * ?session_id=<auth-header>} so a concurrent test can't consume it and a stale one can't bleed
     * across tests. An unscoped harness arms a shared override (legacy serial behavior).
     */
    public void scenarioSet(String operationId, int status, Map<String, Object> body) {
      Map<String, Object> payload = new HashMap<>();
      payload.put("status", status);
      payload.put("response", body);
      String json = GSON.toJson(payload);
      String q =
          (authHeader != null && !authHeader.isEmpty())
              ? "?session_id=" + java.net.URLEncoder.encode(authHeader, StandardCharsets.UTF_8)
              : "";
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url + "/__mock__/scenarios/" + operationId + q))
              .timeout(HTTP_TIMEOUT)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
              .build();
      try {
        HttpResponse<String> resp =
            http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
          throw new IllegalStateException(
              "MockTest: scenarioSet(" + operationId + ") returned " + resp.statusCode());
        }
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("MockTest: scenarioSet failed: " + e, e);
      }
    }

    private void postNoBody(String path) {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url + path))
              .timeout(HTTP_TIMEOUT)
              .POST(HttpRequest.BodyPublishers.noBody())
              .build();
      try {
        http.send(req, HttpResponse.BodyHandlers.discarding());
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("MockTest: " + path + " failed: " + e, e);
      }
    }
  }

  /**
   * Lightweight view of a request the mock server recorded. Mirrors the dataclass in {@code
   * mock_signalwire.journal.JournalEntry}.
   */
  public static final class JournalEntry {
    public double timestamp;
    public String method;
    public String path;
    public Map<String, List<String>> queryParams;
    public Map<String, String> headers;
    public Object body;
    public String matchedRoute;
    public Integer responseStatus;

    // Gson maps via @SerializedName equivalents below; field names use
    // the JSON keys directly.

    // Matching the JSON keys from the mock server. Gson by default uses
    // exact field-name matching, so we provide explicit accessors and let
    // the FieldNamingPolicy keep them in snake_case via reflection helpers.

    @com.google.gson.annotations.SerializedName("query_params")
    private Map<String, List<String>> _queryParams;

    @com.google.gson.annotations.SerializedName("matched_route")
    private String _matchedRoute;

    @com.google.gson.annotations.SerializedName("response_status")
    private Integer _responseStatus;

    /** Returns the JSON body coerced to a string-keyed map, or null. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> bodyMap() {
      if (body instanceof Map) {
        return (Map<String, Object>) body;
      }
      return null;
    }

    public Map<String, List<String>> getQueryParams() {
      return _queryParams != null ? _queryParams : queryParams;
    }

    public String getMatchedRoute() {
      return _matchedRoute != null ? _matchedRoute : matchedRoute;
    }

    public Integer getResponseStatus() {
      return _responseStatus != null ? _responseStatus : responseStatus;
    }
  }

  private static final String REST_TOKEN = "test_tok";

  /**
   * Returns (RestClient, Harness) for a single test. The client is a real RestClient pointed at the
   * local mock server.
   *
   * <p>Isolation key: each client gets a UNIQUE RANDOM project ({@code test_proj_<12 hex>}), so its
   * {@code Authorization: Basic base64(project:token)} header is unique. The random suffix (not a
   * counter) keeps it collision-free across parallel test workers AND separate processes/machines
   * hitting one shared mock. The returned harness view filters the global journal by that header
   * (client-side) and scopes scenario overrides by it (server-side), so a test only ever sees /
   * consumes its own requests and scenarios — making the shared mock safe under parallelism with NO
   * SDK change and NO mock-server change.
   *
   * <p>Tests that assert on the AccountSid embedded in a LAML path must read it from {@link
   * Harness#project()} (or {@link Bound#project}) rather than hard-coding {@code test_proj}.
   */
  public static Bound newClient() {
    Harness shared = ensureServer();

    // Unique per-test project => unique Basic-Auth header => journal
    // filterable per client. Random (not a counter) so concurrent workers
    // and processes can't collide on the same project name.
    String project =
        "test_proj_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    String credentials = project + ":" + REST_TOKEN;
    String authHeader =
        "Basic "
            + java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

    RestClient client = RestClient.withBaseUrl(shared.url(), project, REST_TOKEN);

    // Per-call harness view scoped to this client's auth header. No reset is
    // needed: this client starts with zero entries in the (auth-filtered)
    // view.
    Harness mock = new Harness(shared.url(), shared.port());
    mock.authHeader = authHeader;
    mock.project = project;

    return new Bound(client, mock, project);
  }

  /** Tuple of the RestClient and harness handed back to a test. */
  public static final class Bound {
    public final RestClient client;
    public final Harness harness;

    /** The per-test random project this client authenticates with. */
    public final String project;

    Bound(RestClient client, Harness harness, String project) {
      this.client = Objects.requireNonNull(client);
      this.harness = Objects.requireNonNull(harness);
      this.project = Objects.requireNonNull(project);
    }
  }

  // ── Server lifecycle ──────────────────────────────────────────────

  private static Harness ensureServer() {
    Harness existing = sharedHarness;
    if (existing != null) {
      return existing;
    }
    synchronized (STATE_LOCK) {
      if (sharedHarness != null) {
        return sharedHarness;
      }
      if (startupFailure != null) {
        throw new IllegalStateException(
            "MockTest: previous startup failed: " + startupFailure.getMessage(), startupFailure);
      }
      int port = resolvePort();
      String base = "http://127.0.0.1:" + port;
      java.net.http.HttpClient probeClient =
          java.net.http.HttpClient.newBuilder()
              .version(java.net.http.HttpClient.Version.HTTP_1_1)
              .connectTimeout(Duration.ofSeconds(2))
              .build();
      if (probeHealth(probeClient, base)) {
        Harness h = new Harness(base, port);
        sharedHarness = h;
        return h;
      }
      try {
        Process p = spawnMockServer(port);
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
          if (probeHealth(probeClient, base)) {
            Runtime.getRuntime()
                .addShutdownHook(
                    new Thread(
                        () -> {
                          try {
                            p.destroy();
                          } catch (Exception ignored) {
                            // best-effort cleanup on shutdown; nothing to do on failure
                          }
                        },
                        "MockTestShutdown"));
            Harness h = new Harness(base, port);
            sharedHarness = h;
            return h;
          }
          if (!p.isAlive()) {
            startupFailure =
                new IllegalStateException(
                    "mock_signalwire process exited before becoming ready (exit "
                        + p.exitValue()
                        + ")");
            throw (IllegalStateException) startupFailure;
          }
          try {
            Thread.sleep(150);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MockTest: interrupted while waiting for mock", e);
          }
        }
        p.destroy();
        startupFailure =
            new IllegalStateException(
                "MockTest: `python -m mock_signalwire` did not become ready within "
                    + STARTUP_TIMEOUT
                    + " on port "
                    + port
                    + " (clone porting-sdk next to signalwire-java so tests can find "
                    + "porting-sdk/test_harness/mock_signalwire/, or pip install the "
                    + "mock_signalwire package)");
        throw (IllegalStateException) startupFailure;
      } catch (IOException e) {
        startupFailure =
            new IllegalStateException(
                "MockTest: failed to spawn `python -m mock_signalwire`: "
                    + e
                    + " (set MOCK_SIGNALWIRE_PORT to use a pre-running instance)",
                e);
        throw (IllegalStateException) startupFailure;
      }
    }
  }

  private static int resolvePort() {
    String raw = System.getenv("MOCK_SIGNALWIRE_PORT");
    if (raw != null && !raw.isBlank()) {
      try {
        int p = Integer.parseInt(raw.trim());
        if (p > 0) {
          return p;
        }
      } catch (NumberFormatException ignored) {
        // fall through to default
      }
    }
    return DEFAULT_PORT;
  }

  private static Process spawnMockServer(int port) throws IOException {
    ProcessBuilder pb =
        new ProcessBuilder(
            "python3",
            "-m",
            "mock_signalwire",
            "--host",
            "127.0.0.1",
            "--port",
            String.valueOf(port),
            "--log-level",
            "error");
    // Try to inject porting-sdk/test_harness/mock_signalwire/ into
    // PYTHONPATH so `python -m mock_signalwire` resolves without a
    // prior `pip install -e ...`. Adjacency contract: porting-sdk
    // next to signalwire-java in ~/src/. When the walk fails (e.g.
    // porting-sdk is not adjacent), we still spawn — the child falls
    // back to whatever is on the system Python's sys.path, and the
    // readiness probe surfaces a clear timeout error if neither mode
    // is available.
    String pkgDir = discoverPortingSdkPackage("mock_signalwire");
    if (pkgDir != null) {
      Map<String, String> env = pb.environment();
      String existing = env.get("PYTHONPATH");
      String sep = System.getProperty("path.separator", ":");
      String newPP = (existing != null && !existing.isEmpty()) ? pkgDir + sep + existing : pkgDir;
      env.put("PYTHONPATH", newPP);
    }

    // Detach by routing IO to /dev/null so the child does not block on
    // pipe buffers when the test JVM exits.
    Path devnull = Path.of("/dev/null");
    if (Files.exists(devnull)) {
      pb.redirectInput(devnull.toFile());
      pb.redirectOutput(devnull.toFile());
      pb.redirectError(devnull.toFile());
    } else {
      pb.redirectErrorStream(true);
    }
    return pb.start();
  }

  private static boolean probeHealth(java.net.http.HttpClient client, String base) {
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(base + "/__mock__/health"))
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build();
    try {
      HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
      if (resp.statusCode() != 200) {
        drain(resp.body());
        return false;
      }
      byte[] body = resp.body().readAllBytes();
      String text = new String(body, StandardCharsets.UTF_8);
      // The health endpoint always emits a JSON object containing
      // "specs_loaded"; treat any other shape as a probe failure.
      return text.contains("\"specs_loaded\"");
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static void drain(InputStream is) {
    if (is == null) return;
    try {
      is.readAllBytes();
    } catch (IOException ignored) {
      // best-effort
    }
  }

  // Test-only escape hatch for inspecting the active harness without
  // reinvoking newClient (e.g., to assert lifecycle behavior).
  static Optional<Harness> sharedHarnessForTesting() {
    return Optional.ofNullable(sharedHarness);
  }
}
