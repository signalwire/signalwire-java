/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.tls;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Assumptions;

/**
 * Shared TLS test support for the three cross-port "every SDK does VERIFIED HTTPS + WSS" capability
 * quadrants. Mirrors the Go pilot's {@code tls_support_test.go} helpers (commit b6b2b6d) but
 * expressed with the JDK trust primitives Java needs, because Java does NOT consult the OS trust
 * store the way Go/Python honor {@code SSL_CERT_FILE}.
 *
 * <p>What it provides:
 *
 * <ul>
 *   <li>{@link #certsDir()} — locates {@code porting-sdk/test_harness/tls}, runs the idempotent
 *       {@code gen_certs.sh}, returns the certs dir; skips (JUnit assumption) when porting-sdk is
 *       not adjacent.
 *   <li>{@link #trustingSslContext(Path)} — builds an in-memory {@link KeyStore} from {@code
 *       ca.crt}, feeds it to a {@link TrustManagerFactory}, and returns an {@link SSLContext} that
 *       trusts ONLY the throwaway test CA. This is REAL verification: there is no trust-all {@code
 *       TrustManager} anywhere in this package.
 *   <li>{@link #spawnTlsMockRelay()} / {@link #spawnTlsMockSignalwire()} — launch {@code python -m
 *       mock_relay --tls} (WSS) / {@code python -m mock_signalwire --tls} (HTTPS) on dedicated
 *       ports with porting-sdk's package injected onto PYTHONPATH, polling the control plane for
 *       readiness and registering a JVM-shutdown kill.
 * </ul>
 *
 * <p>The negative half of each capability test builds a {@link #emptyTrustSslContext()} (a {@link
 * KeyStore} with no CA loaded) and asserts the handshake is rejected, proving the server presents a
 * cert that must actually be verified.
 */
public final class TlsSupport {

  static final int MOCK_RELAY_WS_PORT = 18877;
  static final int MOCK_RELAY_HTTP_PORT = 19877;
  static final int MOCK_SIGNALWIRE_PORT = 18867;

  // mock_signalwire loads 13 OpenAPI specs on boot (~15s cold start), so give
  // it a generous readiness window; mock_relay is fast.
  private static final Duration RELAY_READY_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration SIGNALWIRE_READY_TIMEOUT = Duration.ofSeconds(60);

  private TlsSupport() {}

  // ── CA trust (the in-memory keystore → TrustManagerFactory → SSLContext) ──

  /**
   * Builds an {@link SSLContext} that trusts ONLY the porting-sdk throwaway test CA ({@code ca.crt}
   * in {@code certsDir}). Java clients (Java-WebSocket SocketFactory, JDK HttpClient, the SDK's
   * HttpsServer reachers) are handed this context so the harness's CA-signed leaf cert verifies —
   * without touching the OS trust store and without any trust-all shortcut.
   */
  public static SSLContext trustingSslContext(Path certsDir) {
    try {
      Certificate ca;
      try (InputStream in = Files.newInputStream(certsDir.resolve("ca.crt"))) {
        ca = CertificateFactory.getInstance("X.509").generateCertificate(in);
      }
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null, null);
      trustStore.setCertificateEntry("signalwire-mock-test-ca", ca);

      TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(trustStore);

      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, tmf.getTrustManagers(), null);
      return ctx;
    } catch (Exception e) {
      throw new IllegalStateException("failed to build CA-trusting SSLContext", e);
    }
  }

  /**
   * Builds an {@link SSLContext} whose trust store is EMPTY (no CA loaded). Used by the negative
   * subtests: a handshake against the harness's CA-signed leaf cert must fail with this context,
   * proving verification is genuinely in force (not skipped).
   */
  public static SSLContext emptyTrustSslContext() {
    try {
      KeyStore empty = KeyStore.getInstance(KeyStore.getDefaultType());
      empty.load(null, null);
      TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(empty);
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, tmf.getTrustManagers(), null);
      return ctx;
    } catch (Exception e) {
      throw new IllegalStateException("failed to build empty-trust SSLContext", e);
    }
  }

  // ── Cert discovery (adjacency walk + idempotent gen_certs.sh) ──────────

  /**
   * Walks up from {@code user.dir} to an adjacent {@code porting-sdk/test_harness/tls}, runs the
   * idempotent {@code gen_certs.sh} (a no-op when the leaf cert still has &gt;30 days left), and
   * returns the {@code certs/} dir. Issues a JUnit assumption-skip when porting-sdk is not
   * adjacent, matching the mocktest adjacency contract.
   */
  public static Path certsDir() {
    Path tlsDir = findTlsDir();
    Assumptions.assumeTrue(
        tlsDir != null, "tls: porting-sdk/test_harness/tls not found adjacent to repo");
    try {
      Process p =
          new ProcessBuilder("bash", tlsDir.resolve("gen_certs.sh").toString())
              .redirectErrorStream(true)
              .start();
      // Drain so the child never blocks on a full pipe buffer.
      try (InputStream is = p.getInputStream()) {
        is.readAllBytes();
      }
      int rc = p.waitFor();
      Assumptions.assumeTrue(rc == 0, "tls: gen_certs.sh exited " + rc);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      Assumptions.abort("tls: gen_certs.sh failed: " + e);
    }
    return tlsDir.resolve("certs");
  }

  private static Path findTlsDir() {
    String userDir = System.getProperty("user.dir");
    if (userDir == null || userDir.isEmpty()) {
      return null;
    }
    Path dir = Path.of(userDir).toAbsolutePath();
    while (dir != null) {
      Path parent = dir.getParent();
      if (parent == null) break;
      Path candidate = parent.resolve("porting-sdk").resolve("test_harness").resolve("tls");
      if (Files.isRegularFile(candidate.resolve("gen_certs.sh"))) {
        return candidate;
      }
      dir = parent;
    }
    return null;
  }

  // ── --tls mock spawners ────────────────────────────────────────────────

  /**
   * Handle to a spawned {@code --tls} mock. {@code controlBase} is the URL of the control plane
   * (http for mock_relay which keeps it plain; https for mock_signalwire whose whole app is HTTPS).
   * {@code controlClient} trusts the test CA so journal/health reads work in either mode.
   */
  public static final class MockHandle implements AutoCloseable {
    public final Process process;
    public final String controlBase;
    public final HttpClient controlClient;
    public final int port; // ws port for relay, http port for signalwire

    MockHandle(Process process, String controlBase, HttpClient controlClient, int port) {
      this.process = process;
      this.controlBase = controlBase;
      this.controlClient = controlClient;
      this.port = port;
    }

    /** GET the mock's recorded journal (control plane). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> journal() {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(controlBase + "/__mock__/journal"))
              .timeout(Duration.ofSeconds(5))
              .GET()
              .build();
      try {
        HttpResponse<String> resp =
            controlClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
          throw new IllegalStateException("journal GET returned " + resp.statusCode());
        }
        return new com.google.gson.Gson()
            .fromJson(
                resp.body(),
                new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>() {}.getType());
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        throw new IllegalStateException("journal fetch failed: " + e, e);
      }
    }

    @Override
    public void close() {
      try {
        process.destroy();
      } catch (Exception ignored) {
        // best-effort
      }
    }
  }

  /**
   * Spawns {@code python -m mock_relay --tls} (WSS endpoint) on dedicated ports and waits for its
   * plain-HTTP control plane to answer {@code /__mock__/health}. The control plane stays HTTP even
   * in --tls mode, so the returned client is a plain JDK HttpClient.
   */
  public static MockHandle spawnTlsMockRelay() {
    String pkgDir = discoverPortingSdkPackage("mock_relay");
    Assumptions.assumeTrue(pkgDir != null, "tls: porting-sdk/test_harness/mock_relay not adjacent");
    List<String> cmd =
        List.of(
            "python3",
            "-m",
            "mock_relay",
            "--host",
            "127.0.0.1",
            "--ws-port",
            String.valueOf(MOCK_RELAY_WS_PORT),
            "--http-port",
            String.valueOf(MOCK_RELAY_HTTP_PORT),
            "--tls",
            "--log-level",
            "error");
    Process p = spawn(cmd, pkgDir, Map.of("SIGNALWIRE_MOCK_TLS", "1"));
    String controlBase = "http://127.0.0.1:" + MOCK_RELAY_HTTP_PORT;
    HttpClient control =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    // mock_relay's /__mock__/health reports "schemas_loaded".
    awaitHealth(p, control, controlBase, RELAY_READY_TIMEOUT, "schemas_loaded", "mock_relay --tls");
    return new MockHandle(p, controlBase, control, MOCK_RELAY_WS_PORT);
  }

  /**
   * Spawns {@code python -m mock_signalwire --tls} (whole app over HTTPS) on a dedicated port and
   * waits for {@code /__mock__/health} over HTTPS using a CA-trusting client. Cold start is ~15s
   * (13 OpenAPI specs), hence the 60s readiness budget.
   */
  public static MockHandle spawnTlsMockSignalwire(Path certsDir) {
    String pkgDir = discoverPortingSdkPackage("mock_signalwire");
    Assumptions.assumeTrue(
        pkgDir != null, "tls: porting-sdk/test_harness/mock_signalwire not adjacent");
    List<String> cmd =
        List.of(
            "python3",
            "-m",
            "mock_signalwire",
            "--host",
            "127.0.0.1",
            "--port",
            String.valueOf(MOCK_SIGNALWIRE_PORT),
            "--tls",
            "--log-level",
            "error");
    Process p = spawn(cmd, pkgDir, Map.of("SIGNALWIRE_MOCK_TLS", "1"));
    String controlBase = "https://127.0.0.1:" + MOCK_SIGNALWIRE_PORT;
    HttpClient control =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .sslContext(trustingSslContext(certsDir))
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    // mock_signalwire's /__mock__/health reports "specs_loaded" (13 OpenAPI
    // specs), distinct from mock_relay's "schemas_loaded".
    awaitHealth(
        p, control, controlBase, SIGNALWIRE_READY_TIMEOUT, "specs_loaded", "mock_signalwire --tls");
    return new MockHandle(p, controlBase, control, MOCK_SIGNALWIRE_PORT);
  }

  private static Process spawn(List<String> cmd, String pkgDir, Map<String, String> extraEnv) {
    ProcessBuilder pb = new ProcessBuilder(cmd);
    Map<String, String> env = pb.environment();
    String sep = System.getProperty("path.separator", ":");
    String existing = env.get("PYTHONPATH");
    env.put(
        "PYTHONPATH", (existing != null && !existing.isEmpty()) ? pkgDir + sep + existing : pkgDir);
    extraEnv.forEach(env::put);

    Path devnull = Path.of("/dev/null");
    if (Files.exists(devnull)) {
      pb.redirectInput(devnull.toFile());
      pb.redirectOutput(devnull.toFile());
      pb.redirectError(devnull.toFile());
    } else {
      pb.redirectErrorStream(true);
    }
    try {
      Process p = pb.start();
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
                  "tls-mock-shutdown"));
      return p;
    } catch (IOException e) {
      Assumptions.abort("tls: failed to spawn " + cmd + ": " + e);
      throw new AssertionError("unreachable"); // assumeTrue/abort throws
    }
  }

  private static void awaitHealth(
      Process p,
      HttpClient client,
      String base,
      Duration timeout,
      String readyMarker,
      String label) {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    while (System.currentTimeMillis() < deadline) {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(base + "/__mock__/health"))
              .timeout(Duration.ofSeconds(2))
              .GET()
              .build();
      try {
        HttpResponse<String> resp =
            client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 200 && resp.body().contains("\"" + readyMarker + "\"")) {
          return;
        }
      } catch (IOException ignored) {
        // not ready yet
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("tls: interrupted awaiting " + label, e);
      }
      if (!p.isAlive()) {
        throw new IllegalStateException(
            label + " exited before becoming ready (exit " + p.exitValue() + ")");
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("tls: interrupted awaiting " + label, e);
      }
    }
    p.destroy();
    throw new IllegalStateException(label + " not ready within " + timeout + " on " + base);
  }

  /**
   * Adjacency walk to {@code porting-sdk/test_harness/<name>/<name>/__init__.py}, returning the dir
   * to put on PYTHONPATH so {@code python -m <name>} resolves, or {@code null} when porting-sdk is
   * not adjacent. Identical contract to {@code RelayMockTest.discoverPortingSdkPackage}.
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

  /** Returns inbound (SDK→server) journal methods, for WSS wire assertions. */
  @SuppressWarnings("unchecked")
  static List<String> recvMethods(List<Map<String, Object>> journal) {
    List<String> out = new ArrayList<>();
    for (Map<String, Object> e : journal) {
      if ("recv".equals(e.get("direction"))) {
        Object m = e.get("method");
        if (m != null) out.add(m.toString());
      }
    }
    return out;
  }
}
