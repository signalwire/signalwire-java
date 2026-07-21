/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * RouteRegistry — enumerate the REST routes the Java SDK actually implements.
 *
 * <p>This is the set of routes the live {@link RestClient} actually dispatches, captured from the
 * REAL code path — not parsed from source (an AST scraper would have to re-implement the
 * CrudResource / base-path machinery and would drift) and not read from the test journal (which
 * only sees routes that happen to be tested, the exact blind spot the gate closes).
 *
 * <p>How it works: build a {@link RestClient} backed by a RECORDING {@link HttpClient} — a subclass
 * that overrides the five verb methods ({@code get/post/put/patch/delete}) to record {@code
 * (method, path)} and return an empty map instead of doing network I/O. Every route — CRUD-base,
 * the cXML/LaML compat POSTs, the {@code createSubscriberToken} helpers, the {@code listAddresses}
 * sub-paths — funnels through that single chokepoint. We then use reflection to walk the client's
 * public namespace accessors → each namespace's sub-resource accessors → each resource's public
 * route methods, invoking each with sentinel arguments synthesised by parameter type ({@code
 * String} path params become the literal sentinel, normalised back to {@code {id}}; {@code Map}
 * bodies/params become empty maps). The captured path is thus a template comparable to the spec's
 * {@code path_template}.
 *
 * <p>The client is built with project id = the sentinel so compat's {@code {AccountSid}} path
 * segment (which the SDK fills from the project id) normalises to {@code {id}} and the spec matcher
 * resolves it from config, exactly as Go's registry does.
 *
 * <p>A method that cannot be invoked is NOT silently skipped — a dropped method is a route missing
 * from Set B, which would turn a real divergence into a false "Java matches the spec" pass. Methods
 * that genuinely do not map to a single canonical route must be listed explicitly in {@link
 * #REGISTRY_SKIP} with a reason; everything else that fails to invoke, or invokes but issues no
 * HTTP request, is a hard ERROR (non-zero exit + recorded in {@code "errors"}), mirroring
 * python_route_registry.py / cmd/route-registry / route-registry.ts.
 *
 * <p>Output: JSON {@code {"routes":[{"method","path_template","via"}],"skipped":[...],
 * "errors":[...]}} on stdout. Exit 1 if any uninvokable, un-skip-listed method (Set B incomplete).
 * ONLY the JSON is written to stdout; diagnostics go to stderr so the shared diff can consume
 * stdout directly via {@code --registry-json}.
 *
 * <p>Run via the {@code routeRegistry} Gradle task (wires the SDK classpath):
 *
 * <pre>
 *   ./gradlew --no-daemon -q routeRegistry
 * </pre>
 *
 * <p>Package-private: this is an internal registry tool (a {@code main()} entry point — no {@code
 * public} needed to launch it), NOT a public SDK API, so it stays out of the enumerated public
 * surface.
 */
final class RouteRegistry {

  private RouteRegistry() {}

  /**
   * Sentinel for any path parameter (resource id, sid, AccountSid, …). One path segment, no slash;
   * normalised back to {@code {id}} so Set B path templates line up with the spec's.
   */
  private static final String SENTINEL = "__ID__";

  /** The SDK package prefix used to tell a namespace/resource accessor from a plain getter. */
  private static final String REST_PKG = "com.signalwire.sdk.rest";

  /**
   * Methods that do NOT map to a single canonical REST route, keyed by {@code
   * "<Namespace>.<Resource>.<Method>"} or a {@code "<Namespace>.<Resource>.*"} wildcard. EVERY
   * entry needs a reason; a method that merely fails to invoke or issues no HTTP request is an
   * ERROR, not an implicit skip — add it here (with justification) or fix the harness so it
   * invokes.
   */
  private static final Map<String, String> REGISTRY_SKIP = new LinkedHashMap<>();

  static {
    // (No entries.) The generated cXML applications resource (CxmlApplications
    // extends BaseResource) declares only list/get/update/delete/listAddresses —
    // it has NO create method at all (there is no POST /cxml_applications
    // canonical route), matching the Python oracle. The walker therefore never
    // sees a fabric.cxmlApplications.create method, so the old skip entry for it
    // is obsolete and removed.
  }

  /** A captured (method, path) pair recorded by the recording HTTP client. */
  private record Call(String method, String path) {}

  /** The recording sink shared by the recording HTTP client. */
  private static final List<Call> CALLS = new ArrayList<>();

  /**
   * RecordingHttpClient — overrides every verb method to record (method, on-the-wire path) and
   * return an empty map, doing NO network I/O. {@code super(...)} builds a real
   * java.net.http.HttpClient that is never used (all verbs are overridden), so no socket is ever
   * opened.
   *
   * <p>The verb argument {@code path} is the resource-relative path (e.g. {@code
   * /fabric/resources}); the SDK's {@link HttpClient#buildUrl} prepends the base URL's path
   * component ({@code /api}) before dispatch. We record that SAME on-the-wire path so Set B lines
   * up with the spec's {@code path_template}, which carries the server prefix (e.g. {@code
   * /api/relay/rest/…}). This mirrors Go's registry recording {@code req.URL.Path} (the full
   * dispatched path) rather than the resource-relative argument.
   */
  private static final class RecordingHttpClient extends HttpClient {
    /** The base URL's path component (e.g. {@code /api}) — the prefix buildUrl prepends. */
    private final String basePrefix;

    RecordingHttpClient() {
      super("recording.invalid", SENTINEL, "t");
      String p = java.net.URI.create(getBaseUrl()).getPath();
      if (p == null) {
        p = "";
      }
      // Strip a trailing slash so basePrefix + path doesn't double the separator.
      if (p.endsWith("/")) {
        p = p.substring(0, p.length() - 1);
      }
      this.basePrefix = p;
    }

    /** The full on-the-wire path the SDK would dispatch for {@code path} (basePrefix + path). */
    private String wire(String path) {
      if (path == null || path.isEmpty()) {
        return basePrefix;
      }
      return basePrefix + (path.startsWith("/") ? path : "/" + path);
    }

    @Override
    public Map<String, Object> get(String path) {
      CALLS.add(new Call("GET", wire(path)));
      return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> get(String path, Map<String, String> queryParams) {
      CALLS.add(new Call("GET", wire(path)));
      return Collections.emptyMap();
    }

    // The RequestOptions-carrying verb overloads (plan 4.2 / PY-9): the generated
    // resource verbs thread a trailing per-request RequestOptions through to these, so
    // the recorder must intercept them too — otherwise the full overload falls through
    // to the real transport and every route errors "failed to reach the server". The
    // options are irrelevant to the captured (method, path).
    @Override
    public Map<String, Object> get(
        String path, Map<String, String> queryParams, RequestOptions requestOptions) {
      CALLS.add(new Call("GET", wire(path)));
      return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> post(String path, Map<String, Object> body) {
      CALLS.add(new Call("POST", wire(path)));
      return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> post(
        String path, Map<String, Object> body, RequestOptions requestOptions) {
      CALLS.add(new Call("POST", wire(path)));
      return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> put(String path, Map<String, Object> body) {
      CALLS.add(new Call("PUT", wire(path)));
      return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> put(
        String path, Map<String, Object> body, RequestOptions requestOptions) {
      CALLS.add(new Call("PUT", wire(path)));
      return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> patch(String path, Map<String, Object> body) {
      CALLS.add(new Call("PATCH", wire(path)));
      return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> patch(
        String path, Map<String, Object> body, RequestOptions requestOptions) {
      CALLS.add(new Call("PATCH", wire(path)));
      return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> delete(String path) {
      CALLS.add(new Call("DELETE", wire(path)));
      return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> delete(String path, RequestOptions requestOptions) {
      CALLS.add(new Call("DELETE", wire(path)));
      return Collections.emptyMap();
    }
  }

  /** Aggregated route record (de-duped by method+path, accumulating the {@code via} accessors). */
  private static final class RouteRec {
    final String method;
    final String pathTemplate;
    final List<String> via = new ArrayList<>();

    RouteRec(String method, String pathTemplate) {
      this.method = method;
      this.pathTemplate = pathTemplate;
    }
  }

  private record SkipRec(String key, String reason) {}

  private record ErrRec(String key, String error) {}

  // de-dup routes by "METHOD PATH" so several accessors reaching the same route record it once.
  private final Map<String, RouteRec> routes = new TreeMap<>();
  private final List<SkipRec> skipped = new ArrayList<>();
  private final List<ErrRec> errors = new ArrayList<>();
  // visited resource OBJECTS (identity) to avoid cycles in the accessor graph.
  private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

  private static String skipReason(String key) {
    String r = REGISTRY_SKIP.get(key);
    if (r != null) {
      return r;
    }
    int i = key.lastIndexOf('.');
    if (i >= 0) {
      return REGISTRY_SKIP.get(key.substring(0, i) + ".*");
    }
    return null;
  }

  /** Is this a namespace/resource accessor (returns an SDK rest object, takes no args)? */
  private static boolean isAccessor(Method m) {
    if (m.getParameterCount() != 0) {
      return false;
    }
    Class<?> rt = m.getReturnType();
    if (rt == HttpClient.class || rt == PaginatedIterator.class) {
      return false; // helper getters, not namespace/sub-resource accessors
    }
    Package p = rt.getPackage();
    return p != null && p.getName().startsWith(REST_PKG);
  }

  /** The package holding the generated typed response DTOs the route methods return. */
  private static final String GEN_TYPES_PKG = "com.signalwire.sdk.rest.namespaces.generated.types";

  /**
   * A route method's wire-response return type. Historically every route returned a {@code Map}
   * (the decoded wire body); the JAVA-1 typed-returns flip made the generated resource methods
   * return their closed spec-typed {@code *Response} DTO instead (and a fabric {@code
   * listAddresses} base override returns {@code Object} so subclasses can covariantly return their
   * typed DTO). All three shapes are the wire response — recognise them so a flipped route is not
   * silently dropped from Set B (which would masquerade as "the SDK is missing this route").
   */
  private static boolean isWireResponseType(Class<?> rt) {
    if (Map.class.isAssignableFrom(rt)) {
      return true;
    }
    if (rt == Object.class) {
      return true; // FabricResource.listAddresses base (covariantly overridden to a DTO)
    }
    Package p = rt.getPackage();
    return p != null && p.getName().startsWith(GEN_TYPES_PKG);
  }

  /** Is this a route method (returns the SDK's wire response — a Map or a typed response DTO)? */
  private static boolean isRoute(Method m) {
    if (!isWireResponseType(m.getReturnType())) {
      return false;
    }
    // Every generated verb ships a convenience overload + a RequestOptions-carrying full
    // overload that dispatch the IDENTICAL wire route (plan 4.2 / PY-9). Registering both
    // double-invokes the route and duplicates its `via` accessor. Skip the full overload
    // (last param RequestOptions) — its no-RO convenience sibling captures the same route.
    int n = m.getParameterCount();
    return n == 0 || m.getParameterTypes()[n - 1] != RequestOptions.class;
  }

  /** Public, non-static, instance methods not declared by Object, sorted by name then arity. */
  private static List<Method> publicMethods(Object obj) {
    List<Method> out = new ArrayList<>();
    for (Method m : obj.getClass().getMethods()) {
      if (m.getDeclaringClass() == Object.class) {
        continue;
      }
      if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) {
        continue;
      }
      out.add(m);
    }
    out.sort(Comparator.comparing(Method::getName).thenComparingInt(Method::getParameterCount));
    return out;
  }

  /** Synthesise a sentinel argument for a parameter by type. */
  private static Object sentinelFor(Parameter param) {
    Class<?> t = param.getType();
    if (t == String.class) {
      return SENTINEL;
    }
    if (Map.class.isAssignableFrom(t)) {
      return new LinkedHashMap<>(); // empty body / query params
    }
    if (List.class.isAssignableFrom(t)) {
      return new ArrayList<>();
    }
    if (t == boolean.class || t == Boolean.class) {
      return Boolean.FALSE;
    }
    if (t == int.class || t == Integer.class || t == long.class || t == Long.class) {
      return 0;
    }
    if (t == double.class || t == Double.class || t == float.class || t == Float.class) {
      return 0.0;
    }
    // Generated typed-input request objects (the write/command/set methods take a
    // closed `<Method>Request` builder arg instead of a raw Map — the Java NAMED
    // idiom for keyword params). They expose a static `builder()` → `build()`; an
    // all-unset request builds fine and its toBody() yields an empty body, which
    // is exactly what the recording client needs to capture the route. Without
    // this, sentinelFor would return null and the route method would NPE inside
    // request.toBody(), dropping every write route from Set B.
    Object built = tryBuildRequest(t);
    if (built != null) {
      return built;
    }
    return null; // best-effort for any unforeseen reference type
  }

  /**
   * If {@code t} is a generated {@code <Method>Request} type (has a static no-arg {@code builder()}
   * whose result has a no-arg {@code build()}), construct {@code t.builder().build()}. Returns null
   * for any type that is not a builder-backed request object.
   */
  private static Object tryBuildRequest(Class<?> t) {
    Package p = t.getPackage();
    if (p == null || !p.getName().startsWith(REST_PKG)) {
      return null;
    }
    try {
      Method builderFactory = t.getMethod("builder");
      if (!Modifier.isStatic(builderFactory.getModifiers())) {
        return null;
      }
      builderFactory.setAccessible(true);
      Object builder = builderFactory.invoke(null);
      if (builder == null) {
        return null;
      }
      Method build = builder.getClass().getMethod("build");
      build.setAccessible(true);
      return build.invoke(builder);
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  /** Invoke a route method with sentinel args; returns null on success or an error message. */
  private static String invoke(Object target, Method m) {
    Object[] args = new Object[m.getParameterCount()];
    Parameter[] params = m.getParameters();
    for (int i = 0; i < params.length; i++) {
      args[i] = sentinelFor(params[i]);
    }
    try {
      // A public method declared on a non-public class (e.g. the anonymous
      // CrudResource subclass QueueNamespace returns from queues()) is otherwise
      // inaccessible to reflective invoke; setAccessible makes it callable.
      m.setAccessible(true);
      m.invoke(target, args);
      return null;
    } catch (Exception t) {
      Throwable cause = (t.getCause() != null) ? t.getCause() : t;
      return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
  }

  private void handleRoute(String key, Object target, Method m) {
    String reason = skipReason(key);
    if (reason != null) {
      skipped.add(new SkipRec(key, reason));
      return;
    }
    CALLS.clear();
    String err = invoke(target, m);
    if (err != null) {
      errors.add(new ErrRec(key, err));
      return;
    }
    if (CALLS.isEmpty()) {
      errors.add(
          new ErrRec(
              key,
              "invoked but issued no HTTP request (client-side helper? "
                  + "add to REGISTRY_SKIP with a reason)"));
      return;
    }
    for (Call c : CALLS) {
      String path = c.path().replace(SENTINEL, "{id}");
      String rk = c.method() + " " + path;
      routes.computeIfAbsent(rk, ignored -> new RouteRec(c.method(), path)).via.add(key);
    }
  }

  /**
   * Walk a resource/namespace object: recurse into accessors (sub-resources) and invoke route
   * methods. {@code prefix} is the dotted accessor chain reaching {@code res} (e.g. {@code
   * "fabric"} or {@code "fabric.cxmlApplications"}); a route's {@code via} key is {@code
   * prefix.method}.
   */
  private void walk(String prefix, Object res) {
    if (res == null || !visited.add(res)) {
      return;
    }
    for (Method m : publicMethods(res)) {
      if (isAccessor(m)) {
        Object child;
        try {
          m.setAccessible(true);
          child = m.invoke(res);
        } catch (Exception t) {
          Throwable cause = (t.getCause() != null) ? t.getCause() : t;
          errors.add(
              new ErrRec(
                  prefix + "." + m.getName(),
                  "accessor threw: "
                      + cause.getClass().getSimpleName()
                      + ": "
                      + cause.getMessage()));
          continue;
        }
        walk(prefix + "." + m.getName(), child);
      } else if (isRoute(m)) {
        handleRoute(prefix + "." + m.getName(), res, m);
      }
      // else: String/HttpClient/void helper getter (getBasePath/getHttpClient) — ignore.
    }
  }

  private Map<String, Object> build() {
    RestClient client =
        RestClient.builder()
            .project(SENTINEL) // becomes the compat {AccountSid} segment → {id}
            .token("t")
            .space("recording.invalid")
            .httpClient(new RecordingHttpClient())
            .build();

    // Walk every public namespace accessor on the client (fabric(), calling(), …).
    for (Method m : publicMethods(client)) {
      if (isAccessor(m)) {
        Object ns;
        try {
          m.setAccessible(true);
          ns = m.invoke(client);
        } catch (Exception t) {
          Throwable cause = (t.getCause() != null) ? t.getCause() : t;
          errors.add(
              new ErrRec(
                  m.getName(),
                  "namespace accessor threw: "
                      + cause.getClass().getSimpleName()
                      + ": "
                      + cause.getMessage()));
          continue;
        }
        // The namespace name is the accessor name; the namespace may itself be a
        // flat resource (own route methods) and/or a container of sub-resources —
        // walk() handles both since it iterates accessors AND routes.
        walk(m.getName(), ns);
      }
    }

    List<Map<String, Object>> routeList = new ArrayList<>();
    for (RouteRec r : routes.values()) {
      Collections.sort(r.via);
      Map<String, Object> rm = new LinkedHashMap<>();
      rm.put("method", r.method);
      rm.put("path_template", r.pathTemplate);
      rm.put("via", r.via);
      routeList.add(rm);
    }
    routeList.sort(
        Comparator.comparing((Map<String, Object> r) -> (String) r.get("path_template"))
            .thenComparing(r -> (String) r.get("method")));

    skipped.sort(Comparator.comparing(SkipRec::key));
    errors.sort(Comparator.comparing(ErrRec::key));

    List<Map<String, Object>> skipList = new ArrayList<>();
    for (SkipRec s : skipped) {
      Map<String, Object> sm = new LinkedHashMap<>();
      sm.put("key", s.key());
      sm.put("reason", s.reason());
      skipList.add(sm);
    }
    List<Map<String, Object>> errList = new ArrayList<>();
    for (ErrRec e : errors) {
      Map<String, Object> em = new LinkedHashMap<>();
      em.put("key", e.key());
      em.put("error", e.error());
      errList.add(em);
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("routes", routeList);
    payload.put("skipped", skipList);
    payload.put("errors", errList);
    return payload;
  }

  public static void main(String[] args) {
    RouteRegistry reg = new RouteRegistry();
    Map<String, Object> payload = reg.build();

    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    System.out.println(gson.toJson(payload));

    if (!reg.errors.isEmpty()) {
      System.err.println(
          "route-registry: "
              + reg.errors.size()
              + " uninvokable/no-request method(s) (Set B incomplete)");
      System.exit(1);
    }
  }
}
