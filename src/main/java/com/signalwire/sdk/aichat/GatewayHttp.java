/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HTTP plumbing shared by {@link ChatGateway#router()} and {@link HandoffRouter#router()}: the
 * bounded JSON body read, JSON responses, and the routing rules of the reference's FastAPI router
 * (404 for an unknown path, 405 with {@code Allow} for a known path and the wrong method, 307 to
 * the slashed/unslashed path the way Starlette's {@code redirect_slashes} does).
 */
final class GatewayHttp {

  private GatewayHttp() {}

  /**
   * JSON for responses: compact, no HTML escaping, nulls kept (a {@code null} {@code last_activity}
   * is a value the browser reads), and floats written the way Python's {@code json.dumps} writes
   * them ({@code 1786258737.756596}, not {@code 1.786258737756596E9}).
   */
  static final Gson JSON =
      new GsonBuilder()
          .disableHtmlEscaping()
          .serializeNulls()
          .registerTypeAdapter(Double.class, new PyFloatAdapter())
          .registerTypeAdapter(double.class, new PyFloatAdapter())
          .create();

  private static final TypeAdapter<JsonElement> ELEMENT = new Gson().getAdapter(JsonElement.class);

  /** Writes a double with Python's {@code float.__repr__} spelling. */
  private static final class PyFloatAdapter extends TypeAdapter<Double> {
    @Override
    public void write(JsonWriter out, Double value) throws IOException {
      if (value == null) {
        out.nullValue();
      } else {
        out.jsonValue(pyFloatRepr(value));
      }
    }

    @Override
    public Double read(JsonReader in) throws IOException {
      return in.nextDouble();
    }
  }

  /**
   * Python's {@code repr(float)}: the shortest round-tripping digits, fixed notation when the
   * decimal exponent is in {@code [-4, 16)}, otherwise {@code d.ddde+XX}.
   */
  static String pyFloatRepr(double d) {
    if (Double.isNaN(d)) {
      return "NaN";
    }
    if (Double.isInfinite(d)) {
      return d > 0 ? "Infinity" : "-Infinity";
    }
    String sign = (d < 0 || (d == 0.0 && 1.0 / d < 0)) ? "-" : "";
    double a = Math.abs(d);
    if (a == 0.0) {
      return sign + "0.0";
    }
    BigDecimal bd = new BigDecimal(Double.toString(a)).stripTrailingZeros();
    String digits = bd.unscaledValue().toString();
    int exp = digits.length() - 1 - bd.scale();
    if (exp >= -4 && exp < 16) {
      String plain = bd.toPlainString();
      return sign + (plain.contains(".") ? plain : plain + ".0");
    }
    StringBuilder sb = new StringBuilder(sign).append(digits.charAt(0));
    if (digits.length() > 1) {
      sb.append('.').append(digits, 1, digits.length());
    }
    sb.append('e').append(exp < 0 ? '-' : '+');
    int ax = Math.abs(exp);
    if (ax < 10) {
      sb.append('0');
    }
    return sb.append(ax).toString();
  }

  /**
   * Parse a request body the way Python's {@code json.loads} does: strict JSON, a single value with
   * nothing after it, a duplicate key keeping its last value, integers as {@link Long} (or {@link
   * BigInteger}) and anything with a fraction or exponent as {@link Double}.
   *
   * @throws IllegalArgumentException if the bytes are not valid JSON.
   */
  static Object parseJson(byte[] raw) {
    String text = new String(raw, StandardCharsets.UTF_8);
    try (JsonReader reader = new JsonReader(new StringReader(text))) {
      // STRICT like json.loads; read through the JsonElement adapter, because
      // JsonParser.parseReader forces the reader lenient for the duration of the parse.
      reader.setStrictness(Strictness.STRICT);
      JsonElement el = ELEMENT.read(reader);
      if (reader.peek() != JsonToken.END_DOCUMENT) {
        throw new IllegalArgumentException("extra data after JSON value");
      }
      return toJava(el);
    } catch (IOException | RuntimeException e) {
      throw new IllegalArgumentException("invalid JSON body", e);
    }
  }

  private static Object toJava(JsonElement el) {
    if (el == null || el.isJsonNull()) {
      return null;
    }
    if (el.isJsonObject()) {
      JsonObject o = el.getAsJsonObject();
      Map<String, Object> m = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> e : o.entrySet()) {
        m.put(e.getKey(), toJava(e.getValue()));
      }
      return m;
    }
    if (el.isJsonArray()) {
      JsonArray a = el.getAsJsonArray();
      List<Object> l = new ArrayList<>(a.size());
      for (JsonElement e : a) {
        l.add(toJava(e));
      }
      return l;
    }
    JsonPrimitive p = el.getAsJsonPrimitive();
    if (p.isBoolean()) {
      return p.getAsBoolean();
    }
    if (p.isString()) {
      return p.getAsString();
    }
    String num = p.getAsNumber().toString();
    if (num.contains(".") || num.contains("e") || num.contains("E")) {
      return Double.parseDouble(num);
    }
    BigInteger bi = new BigInteger(num);
    return bi.bitLength() < 64 ? (Object) bi.longValue() : bi;
  }

  /**
   * Read a request's body, refusing one over {@code limit} bytes.
   *
   * <p>A declared {@code Content-Length} over the limit is refused before anything is read. The
   * body is then read in chunks and abandoned as soon as it passes the limit, so a chunked or
   * understated upload can't make the process hold more than {@code limit} bytes of it.
   *
   * @throws GatewayRejection 413 if the body is over {@code limit}.
   */
  static byte[] readBody(HttpExchange exchange, int limit) throws IOException {
    String declared = exchange.getRequestHeaders().getFirst("Content-Length");
    if (declared != null
        && !declared.isEmpty()
        && declared.chars().allMatch(c -> c >= '0' && c <= '9')) {
      if (new BigInteger(declared).compareTo(BigInteger.valueOf(limit)) > 0) {
        throw new GatewayRejection(413, "request too large");
      }
    }
    ByteArrayOutputStream received = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    InputStream in = exchange.getRequestBody();
    int n;
    while ((n = in.read(buf)) != -1) {
      received.write(buf, 0, n);
      if (received.size() > limit) {
        throw new GatewayRejection(413, "request too large");
      }
    }
    return received.toByteArray();
  }

  /**
   * Read and decode a JSON request body (the reference's {@code _read_json_body}).
   *
   * @throws GatewayRejection 413 if the body is over {@code limit}.
   * @throws IllegalArgumentException if the body isn't valid JSON.
   */
  static Object parseJsonBody(HttpExchange exchange, int limit) {
    byte[] raw;
    try {
      raw = readBody(exchange, limit);
    } catch (IOException e) {
      throw new IllegalArgumentException("could not read the request body", e);
    }
    return parseJson(raw);
  }

  /** Send {@code body} as {@code application/json} with {@code status} and extra headers. */
  static void sendJson(HttpExchange exchange, int status, Object body, Map<String, String> headers)
      throws IOException {
    byte[] out = JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
    for (Map.Entry<String, String> h : headers.entrySet()) {
      exchange.getResponseHeaders().set(h.getKey(), h.getValue());
    }
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, out.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(out);
    }
  }

  /** Starlette's unhandled-exception answer. */
  static void sendServerError(HttpExchange exchange) throws IOException {
    byte[] out = "Internal Server Error".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    exchange.sendResponseHeaders(500, out.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(out);
    }
  }

  static void notFound(HttpExchange exchange) throws IOException {
    sendJson(exchange, 404, Map.of("detail", "Not Found"), Map.of());
  }

  static void methodNotAllowed(HttpExchange exchange, String allow) throws IOException {
    sendJson(exchange, 405, Map.of("detail", "Method Not Allowed"), Map.of("Allow", allow));
  }

  /** A 307 to {@code path} on the same host, as Starlette's slash redirect sends it. */
  static void redirect(HttpExchange exchange, String path) throws IOException {
    String host = exchange.getRequestHeaders().getFirst("Host");
    String query = exchange.getRequestURI().getRawQuery();
    String location =
        (host == null ? "" : "http://" + host) + path + (query == null ? "" : "?" + query);
    exchange.getResponseHeaders().set("Location", location);
    exchange.sendResponseHeaders(307, -1);
    exchange.close();
  }

  /**
   * The route a request addresses, relative to the context the router is mounted on.
   *
   * <p>Mounted at a prefix ({@code createContext("/chat", router)}) the route is the sub-path
   * ({@code /chat/say} → {@code /say}). Because a JDK {@code HttpServer} cannot mount two handlers
   * on one context, a router may also be mounted once per route ({@code createContext("/chat/say",
   * router)}): a request for exactly the context path then addresses the route named by its last
   * segment.
   *
   * @param routes the router's routes, e.g. {@code /say}.
   * @return the matched route, {@code "redirect:<path>"} for a slash redirect, or {@code null}.
   */
  static String resolve(HttpExchange exchange, Set<String> routes) {
    String ctx = exchange.getHttpContext().getPath();
    if (ctx.endsWith("/")) {
      ctx = ctx.substring(0, ctx.length() - 1);
    }
    String path = exchange.getRequestURI().getPath();
    String rel = path.length() >= ctx.length() ? path.substring(ctx.length()) : "";
    if (routes.contains(rel)) {
      return rel;
    }
    int slash = ctx.lastIndexOf('/');
    String own = slash >= 0 ? ctx.substring(slash) : "";
    if (rel.isEmpty() && routes.contains(own)) {
      return own;
    }
    // redirect_slashes: the other spelling of the path names a route.
    if (rel.endsWith("/")) {
      String bare = rel.substring(0, rel.length() - 1);
      if (routes.contains(bare) || (bare.isEmpty() && routes.contains(own))) {
        return "redirect:" + path.substring(0, path.length() - 1);
      }
    } else if (routes.contains(rel + "/")) {
      return "redirect:" + path + "/";
    }
    return null;
  }

  /** Python truthiness of a decoded JSON value. */
  static boolean truthy(Object v) {
    if (v == null) {
      return false;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    if (v instanceof String s) {
      return !s.isEmpty();
    }
    if (v instanceof Number n) {
      return n.doubleValue() != 0.0;
    }
    if (v instanceof Map<?, ?> m) {
      return !m.isEmpty();
    }
    if (v instanceof java.util.Collection<?> c) {
      return !c.isEmpty();
    }
    return true;
  }

  /**
   * Length of {@code text} in UTF-8 bytes, counting a lone surrogate as 3 bytes (Python's {@code
   * "surrogatepass"}); never throws.
   */
  static int utf8Len(String text) {
    int n = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c < 0x80) {
        n += 1;
      } else if (c < 0x800) {
        n += 2;
      } else if (Character.isHighSurrogate(c)
          && i + 1 < text.length()
          && Character.isLowSurrogate(text.charAt(i + 1))) {
        n += 4;
        i++;
      } else {
        n += 3;
      }
    }
    return n;
  }

  /** Python's {@code str.strip()}: drop leading/trailing whitespace. */
  static String pyStrip(String s) {
    int start = 0;
    int end = s.length();
    while (start < end && isPySpace(s.charAt(start))) {
      start++;
    }
    while (end > start && isPySpace(s.charAt(end - 1))) {
      end--;
    }
    return s.substring(start, end);
  }

  private static boolean isPySpace(char c) {
    return Character.isWhitespace(c)
        || Character.isSpaceChar(c)
        || (c >= 0x1c && c <= 0x1f)
        || c == 0x85;
  }
}
