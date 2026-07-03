/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.web;

import com.signalwire.sdk.core.ConfigLoader;
import com.signalwire.sdk.core.SecurityConfig;
import com.signalwire.sdk.logging.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static file serving service with an HTTP API.
 *
 * <p>Java port of the Python reference {@code signalwire.web.web_service.WebService}. Maps URL
 * route prefixes to local directories and serves their files over HTTP with security headers,
 * extension filtering, path-traversal protection, and optional basic auth.
 *
 * <p>Java idiom note: Python builds a FastAPI/uvicorn app; Java uses the JDK built-in {@link
 * HttpServer}. {@link #start(String, Integer)} launches the server (non-blocking) and returns the
 * bound port, so it is safe to start and {@link #stop()} in tests without hanging. Pass port 0 to
 * bind an ephemeral port.
 */
public class WebService {

  private static final List<String> DEFAULT_BLOCKED_EXTENSIONS =
      List.of(
          ".env",
          ".git",
          ".gitignore",
          ".key",
          ".pem",
          ".crt",
          ".pyc",
          "__pycache__",
          ".DS_Store",
          ".swp");

  private final Logger log = Logger.getLogger("web_service");

  private int port;
  private final Map<String, String> directories = new LinkedHashMap<>();
  private boolean enableDirectoryBrowsing;
  private long maxFileSize;
  private boolean enableCors;
  private List<String> allowedExtensions;
  private List<String> blockedExtensions;
  private SecurityConfig security;
  private String[] basicAuth;
  private HttpServer server;

  /** Initialize with defaults (port 8002, no directories, auto basic-auth). */
  public WebService() {
    this(8002, null, null, null, false, null, null, 100L * 1024 * 1024, true);
  }

  /** Convenience: explicit basic-auth pair. */
  public WebService(String[] basicAuth) {
    this(8002, null, basicAuth, null, false, null, null, 100L * 1024 * 1024, true);
  }

  /**
   * Full constructor.
   *
   * @param port default bind port
   * @param directories initial route -> directory map (may be {@code null})
   * @param basicAuth explicit {@code [user, pass]} (may be {@code null} -> use SecurityConfig)
   * @param configFile optional config file path
   * @param enableDirectoryBrowsing serve index.html for directory requests
   * @param allowedExtensions allow-list of file extensions (may be {@code null} -> all but blocked)
   * @param blockedExtensions block-list of file extensions/names (may be {@code null} -> defaults)
   * @param maxFileSize max servable file size in bytes
   * @param enableCors whether CORS is enabled
   */
  public WebService(
      int port,
      Map<String, String> directories,
      String[] basicAuth,
      String configFile,
      boolean enableDirectoryBrowsing,
      List<String> allowedExtensions,
      List<String> blockedExtensions,
      long maxFileSize,
      boolean enableCors) {
    loadConfig(configFile);
    this.port = port;
    this.enableDirectoryBrowsing = enableDirectoryBrowsing;
    this.maxFileSize = maxFileSize;
    this.enableCors = enableCors;
    if (directories != null) {
      this.directories.putAll(directories);
    }
    this.allowedExtensions = allowedExtensions;
    this.blockedExtensions =
        blockedExtensions != null ? blockedExtensions : new ArrayList<>(DEFAULT_BLOCKED_EXTENSIONS);
    this.security = new SecurityConfig(configFile, "web");
    this.basicAuth = basicAuth != null ? basicAuth : security.getBasicAuth();
    this.server = null;
  }

  public int getPort() {
    return port;
  }

  public Map<String, String> getDirectories() {
    return directories;
  }

  public SecurityConfig getSecurity() {
    return security;
  }

  /**
   * Add a directory to serve at {@code route}. Remounts immediately if running.
   *
   * @throws IllegalArgumentException when the path does not exist or is not a directory
   */
  public void addDirectory(String route, String directory) {
    route = normalizeRoute(route);
    Path p = Paths.get(directory);
    if (!Files.exists(p)) {
      throw new IllegalArgumentException("Directory does not exist: " + directory);
    }
    if (!Files.isDirectory(p)) {
      throw new IllegalArgumentException("Path is not a directory: " + directory);
    }
    directories.put(route, directory);
    if (server != null) {
      mountDirectory(route, directory);
    }
  }

  /** Remove the directory served at {@code route} (no-op when absent). */
  public void removeDirectory(String route) {
    route = normalizeRoute(route);
    directories.remove(route);
    if (server != null) {
      try {
        server.removeContext(route);
      } catch (IllegalArgumentException ignored) {
        // context not present; nothing to remove
      }
    }
  }

  /**
   * Start the service (non-blocking). Binds {@code port} (0 = OS-assigned ephemeral) on {@code
   * host} and returns the actually-bound port.
   */
  public int start(String host, Integer bindPort) {
    int requested = bindPort != null ? bindPort : port;
    try {
      server = HttpServer.create(new InetSocketAddress(host, requested), 0);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to bind web service", e);
    }
    server.setExecutor(null);
    for (Map.Entry<String, String> e : directories.entrySet()) {
      mountDirectory(e.getKey(), e.getValue());
    }
    server.start();
    return server.getAddress().getPort();
  }

  /** Convenience: {@code start("127.0.0.1", port)}. */
  public int start() {
    return start("127.0.0.1", port);
  }

  /** Stop the service and release the socket. Safe to call when not running. */
  public void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  /** Whether a file may be served (size + extension/name filters). */
  public boolean fileAllowed(Path path) {
    if (!Files.isRegularFile(path)) {
      return false;
    }
    try {
      if (Files.size(path) > maxFileSize) {
        return false;
      }
    } catch (IOException e) {
      return false;
    }
    if (blocked(path)) {
      return false;
    }
    if (allowedExtensions != null) {
      return allowedExtensions.contains(
          extension(path.getFileName().toString()).toLowerCase(Locale.ROOT));
    }
    return true;
  }

  // ------------------------------------------------------------------
  // Internal.
  // ------------------------------------------------------------------

  private void mountDirectory(String route, String directory) {
    server.createContext(route, exchange -> handleRequest(exchange, route, directory));
  }

  private void handleRequest(HttpExchange exchange, String route, String directory)
      throws IOException {
    try {
      if (!authorized(exchange)) {
        return;
      }
      String path = exchange.getRequestURI().getPath();
      String rel = path;
      if (rel.startsWith(route)) {
        rel = rel.substring(route.length());
      }
      if (rel.startsWith("/")) {
        rel = rel.substring(1);
      }
      Path base = Paths.get(directory).toAbsolutePath().normalize();
      Path full = base.resolve(rel).normalize();
      if (!full.equals(base) && !full.startsWith(base)) {
        deny(exchange, 403, "Access denied");
        return;
      }
      if (!Files.exists(full)) {
        deny(exchange, 404, "File not found");
        return;
      }
      servePath(exchange, full);
    } finally {
      exchange.close();
    }
  }

  private void servePath(HttpExchange exchange, Path full) throws IOException {
    if (Files.isDirectory(full)) {
      full = full.resolve("index.html");
    }
    if (!Files.isRegularFile(full)) {
      deny(exchange, 403, "Directory browsing disabled");
      return;
    }
    if (!fileAllowed(full)) {
      deny(exchange, 403, "File type not allowed");
      return;
    }
    writeFile(exchange, full);
  }

  private void writeFile(HttpExchange exchange, Path full) throws IOException {
    byte[] body = Files.readAllBytes(full);
    exchange.getResponseHeaders().set("Content-Type", mimeType(full));
    exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
    for (Map.Entry<String, String> h : security.getSecurityHeaders().entrySet()) {
      exchange.getResponseHeaders().set(h.getKey(), h.getValue());
    }
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(body);
    }
  }

  private void deny(HttpExchange exchange, int status, String message) throws IOException {
    byte[] body = message.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, body.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(body);
    }
  }

  private boolean authorized(HttpExchange exchange) throws IOException {
    String user = basicAuth != null && basicAuth.length > 0 ? basicAuth[0] : null;
    String pass = basicAuth != null && basicAuth.length > 1 ? basicAuth[1] : null;
    if (user == null || pass == null) {
      return true;
    }
    if (credentialsMatch(exchange, user, pass)) {
      return true;
    }
    exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"SignalWire Web Service\"");
    byte[] body = "Authentication required".getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(401, body.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(body);
    }
    return false;
  }

  private boolean credentialsMatch(HttpExchange exchange, String user, String pass) {
    String header = exchange.getRequestHeaders().getFirst("Authorization");
    if (header == null || !header.startsWith("Basic ")) {
      return false;
    }
    try {
      byte[] decoded = Base64.getDecoder().decode(header.substring(6).trim());
      String pair = new String(decoded, StandardCharsets.UTF_8);
      int idx = pair.indexOf(':');
      String inUser = idx >= 0 ? pair.substring(0, idx) : pair;
      String inPass = idx >= 0 ? pair.substring(idx + 1) : "";
      return secureCompare(user, inUser) && secureCompare(pass, inPass);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static boolean secureCompare(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  private boolean blocked(Path path) {
    String name = path.getFileName().toString();
    String ext = extension(name).toLowerCase(Locale.ROOT);
    String full = path.toString();
    for (String b : blockedExtensions) {
      if (b.startsWith(".")) {
        if (ext.equals(b) || name.equals(b)) {
          return true;
        }
      } else if (name.equals(b) || full.contains(b)) {
        return true;
      }
    }
    return false;
  }

  private static String extension(String name) {
    int dot = name.lastIndexOf('.');
    return dot >= 0 ? name.substring(dot) : "";
  }

  private static String mimeType(Path full) {
    String guessed = URLConnection.guessContentTypeFromName(full.getFileName().toString());
    return guessed != null ? guessed : "application/octet-stream";
  }

  private static String normalizeRoute(String route) {
    return route.startsWith("/") ? route : "/" + route;
  }

  @SuppressWarnings("unchecked")
  private void loadConfig(String configFile) {
    this.port = 8002;
    if (configFile == null) {
      configFile = ConfigLoader.findConfigFile("web");
    }
    if (configFile == null) {
      return;
    }
    ConfigLoader loader = new ConfigLoader(List.of(configFile));
    if (!loader.hasConfig()) {
      return;
    }
    Map<String, Object> service = loader.getSection("service");
    if (service == null || service.isEmpty()) {
      return;
    }
    if (service.containsKey("port")) {
      this.port = (int) parseLong(service.get("port"), 8002);
    }
    Object dirs = service.get("directories");
    if (dirs instanceof Map) {
      for (Map.Entry<String, Object> e : ((Map<String, Object>) dirs).entrySet()) {
        directories.put(e.getKey(), String.valueOf(e.getValue()));
      }
    }
    if (service.containsKey("max_file_size")) {
      this.maxFileSize = parseLong(service.get("max_file_size"), 100L * 1024 * 1024);
    }
    if (service.get("allowed_extensions") instanceof List) {
      this.allowedExtensions = stringList((List<Object>) service.get("allowed_extensions"));
    }
    if (service.get("blocked_extensions") instanceof List) {
      this.blockedExtensions = stringList((List<Object>) service.get("blocked_extensions"));
    }
    if (service.containsKey("enable_directory_browsing")) {
      this.enableDirectoryBrowsing = truthy(service.get("enable_directory_browsing"));
    }
  }

  private static List<String> stringList(List<Object> in) {
    List<String> out = new ArrayList<>();
    for (Object o : in) {
      out.add(String.valueOf(o));
    }
    return out;
  }

  private static long parseLong(Object v, long fallback) {
    if (v == null) {
      return fallback;
    }
    if (v instanceof Number) {
      return ((Number) v).longValue();
    }
    try {
      return Long.parseLong(String.valueOf(v).trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static boolean truthy(Object v) {
    if (v instanceof Boolean) {
      return (Boolean) v;
    }
    if (v == null) {
      return false;
    }
    return Arrays.asList("true", "1", "yes").contains(String.valueOf(v).toLowerCase(Locale.ROOT));
  }
}
