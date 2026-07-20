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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RouteTestPlan — the per-{@code via} call plan for the REST wire-test generator ({@code
 * scripts/generate_rest_tests.py}).
 *
 * <p>Companion capture to {@link RouteRegistry}. {@code RouteRegistry} answers "which (method,
 * path) routes does the SDK implement" (deduped, via-merged); this program answers the sibling
 * question the TEST generator needs: for EVERY {@code via} route method, what is the exact Java
 * call expression that reaches it off the live {@link RestClient}, AND what type-correct literal
 * argument tokens must be passed. It is the Java realisation of the reflection the ruby/php/go/ts
 * generators do (rest_test_plan.rb / rest_test_plan.php / buildCallIndex).
 *
 * <p>It REUSES {@code RouteRegistry}'s live-client walk shape (a {@link RestClient} backed by a
 * recording {@link HttpClient}; reflection over namespace → sub-resource accessors → route methods)
 * so the plan can never drift from the registry's route set. For each route method it records:
 *
 * <ul>
 *   <li>{@code via} — {@code "<ns>[.<res>].<member>"}, IDENTICAL to {@link RouteRegistry}'s via
 *       strings, so the generator joins registry routes → spec operationId → this plan by via with
 *       no ambiguity;
 *   <li>{@code method} — the HTTP verb captured from the recording client;
 *   <li>{@code path} — the captured path template (params already {@code {id}});
 *   <li>{@code chain} — the ordered accessor call chain to reach the resource off the client
 *       ({@code ["video","rooms"]} for {@code video.rooms.get}, {@code ["addresses"]} for the flat
 *       {@code addresses.get}); it is the via minus the terminal member;
 *   <li>{@code member} — the route method name ({@code get}, {@code create}, {@code listStreams},
 *       …);
 *   <li>{@code args} — the ordered Java literal argument tokens for the method's parameters. Java
 *       is statically typed and every route-method parameter is required, so each is emitted with a
 *       token of the RIGHT type (RULES §4/§6): {@code String}→{@code "x"}; {@code Map}→{@code
 *       java.util.Map.of()}; {@code List}→{@code java.util.List.of()}; {@code int/long}→{@code 1};
 *       {@code double/float}→{@code 1.0}; {@code boolean}→{@code true}; a generated closed {@code
 *       <Method>Request} type → {@code <FQN>.builder().build()} (its all-unset body is empty, which
 *       is what the wire-test needs). A wrongly-typed token would be a compile error in the
 *       generated file, so type-correctness is enforced by the Java compiler itself.
 * </ul>
 *
 * <p>Output: JSON {@code {"plan":[{via,method,path,chain,member,args}],"errors":[...]}} on stdout.
 * Exit 1 if any route method could not be reflected/invoked (never silently dropped — a dropped via
 * is a hole in the generated suite), mirroring {@link RouteRegistry}'s fail-loud contract. ONLY the
 * JSON reaches stdout; diagnostics go to stderr.
 *
 * <p>Run via the {@code routeTestPlan} Gradle task (wires the SDK classpath):
 *
 * <pre>
 *   ./gradlew --no-daemon -q routeTestPlan
 * </pre>
 *
 * <p>Package-private, {@code main()}-launched — a build-time tool, not a public SDK API, so it
 * stays out of the enumerated surface (like {@link RouteRegistry}).
 */
final class RouteTestPlan {

  private RouteTestPlan() {}

  /**
   * Sentinel for any path parameter — one path segment, no slash; normalised back to {@code {id}}.
   */
  private static final String SENTINEL = "__ID__";

  /** The SDK package prefix used to tell a namespace/resource accessor from a plain getter. */
  private static final String REST_PKG = "com.signalwire.sdk.rest";

  /** A captured (method, path) pair recorded by the recording HTTP client. */
  private record Call(String method, String path) {}

  private static final List<Call> CALLS = new ArrayList<>();

  /**
   * RecordingHttpClient — identical contract to {@link RouteRegistry}'s: overrides every verb to
   * record the on-the-wire path (basePrefix + resource path) and return an empty map, no network.
   */
  private static final class RecordingHttpClient extends HttpClient {
    private final String basePrefix;

    RecordingHttpClient() {
      super("recording.invalid", SENTINEL, "t");
      String p = java.net.URI.create(getBaseUrl()).getPath();
      if (p == null) {
        p = "";
      }
      if (p.endsWith("/")) {
        p = p.substring(0, p.length() - 1);
      }
      this.basePrefix = p;
    }

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

    // RequestOptions-carrying overloads (plan 4.2 / PY-9) — intercept them too so the
    // generated full overload records instead of falling through to the real transport.
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

  private record PlanRec(
      String via,
      String method,
      String path,
      List<String> chain,
      String member,
      List<String> args) {}

  private record ErrRec(String key, String error) {}

  private final List<PlanRec> plan = new ArrayList<>();
  private final List<ErrRec> errors = new ArrayList<>();
  private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

  /** Is this a namespace/resource accessor (returns an SDK rest object, takes no args)? */
  private static boolean isAccessor(Method m) {
    if (m.getParameterCount() != 0) {
      return false;
    }
    Class<?> rt = m.getReturnType();
    if (rt == HttpClient.class || rt == PaginatedIterator.class) {
      return false;
    }
    Package p = rt.getPackage();
    return p != null && p.getName().startsWith(REST_PKG);
  }

  /** Is this a route method (returns a Map — the SDK's wire-response shape)? */
  private static boolean isRoute(Method m) {
    if (!Map.class.isAssignableFrom(m.getReturnType())) {
      return false;
    }
    // Skip the RequestOptions-carrying full overload (plan 4.2 / PY-9); its no-RO
    // convenience sibling dispatches the identical wire route, so registering both would
    // emit a duplicate test-plan entry for the same route.
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
    out.sort(
        java.util.Comparator.comparing(Method::getName)
            .thenComparingInt(Method::getParameterCount));
    return out;
  }

  /** The runtime sentinel argument for a parameter (to actually invoke the method). */
  private static Object sentinelValueFor(Class<?> t) {
    if (t == String.class) {
      return SENTINEL;
    }
    if (Map.class.isAssignableFrom(t)) {
      return new LinkedHashMap<>();
    }
    if (List.class.isAssignableFrom(t)) {
      return new ArrayList<>();
    }
    if (t == boolean.class || t == Boolean.class) {
      return Boolean.FALSE;
    }
    if (t == int.class || t == Integer.class) {
      return 0;
    }
    if (t == long.class || t == Long.class) {
      return 0L;
    }
    if (t == double.class || t == Double.class) {
      return 0.0d;
    }
    if (t == float.class || t == Float.class) {
      return 0.0f;
    }
    Object built = tryBuildRequest(t);
    if (built != null) {
      return built;
    }
    return null;
  }

  /**
   * The SOURCE-literal argument token for a parameter (emitted verbatim into the generated test).
   * Type-correct by construction so the generated file compiles: a wrongly-typed token is a Java
   * compile error. Returns null when the type is unforeseen (surfaced as a plan error).
   */
  private static String argLiteralFor(Class<?> t) {
    if (t == String.class) {
      return "\"x\"";
    }
    if (Map.class.isAssignableFrom(t)) {
      return "java.util.Map.of()";
    }
    if (List.class.isAssignableFrom(t)) {
      return "java.util.List.of()";
    }
    if (t == boolean.class || t == Boolean.class) {
      return "true";
    }
    if (t == int.class || t == Integer.class) {
      return "1";
    }
    if (t == long.class || t == Long.class) {
      return "1L";
    }
    if (t == double.class || t == Double.class) {
      return "1.0d";
    }
    if (t == float.class || t == Float.class) {
      return "1.0f";
    }
    if (isBuilderBackedRequest(t)) {
      // Nested request type: <Resource>.<Method>Request — the canonical name is the
      // dotted source reference the test can name directly (an import of the resource
      // class is not needed since we fully-qualify it).
      String fqn = t.getCanonicalName();
      if (fqn != null) {
        return fqn + ".builder().build()";
      }
    }
    return null;
  }

  private static boolean isBuilderBackedRequest(Class<?> t) {
    Package p = t.getPackage();
    if (p == null || !p.getName().startsWith(REST_PKG)) {
      return false;
    }
    try {
      Method builderFactory = t.getMethod("builder");
      if (!Modifier.isStatic(builderFactory.getModifiers())) {
        return false;
      }
      Object builder = builderFactory.invoke(null);
      if (builder == null) {
        return false;
      }
      builder.getClass().getMethod("build");
      return true;
    } catch (ReflectiveOperationException e) {
      return false;
    }
  }

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

  /** Reflect + invoke one route method; append its plan entry, or an error. */
  private void handleRoute(List<String> chain, Object target, Method m) {
    String member = m.getName();
    String via = String.join(".", chain) + "." + member;

    Parameter[] params = m.getParameters();
    Object[] args = new Object[params.length];
    List<String> literals = new ArrayList<>();
    for (int i = 0; i < params.length; i++) {
      Class<?> pt = params[i].getType();
      args[i] = sentinelValueFor(pt);
      String lit = argLiteralFor(pt);
      if (lit == null) {
        errors.add(new ErrRec(via, "no arg literal for parameter type " + pt.getName()));
        return;
      }
      literals.add(lit);
    }

    CALLS.clear();
    try {
      m.setAccessible(true);
      m.invoke(target, args);
    } catch (Exception t) {
      Throwable cause = (t.getCause() != null) ? t.getCause() : t;
      errors.add(new ErrRec(via, cause.getClass().getSimpleName() + ": " + cause.getMessage()));
      return;
    }
    if (CALLS.isEmpty()) {
      errors.add(new ErrRec(via, "invoked but issued no HTTP request"));
      return;
    }
    Call c = CALLS.get(0);
    String path = c.path().replace(SENTINEL, "{id}");
    plan.add(new PlanRec(via, c.method(), path, new ArrayList<>(chain), member, literals));
  }

  /** Walk a resource/namespace object: recurse into accessors, invoke route methods. */
  private void walk(List<String> chain, Object res) {
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
          List<String> ck = new ArrayList<>(chain);
          ck.add(m.getName());
          errors.add(
              new ErrRec(
                  String.join(".", ck),
                  "accessor threw: "
                      + cause.getClass().getSimpleName()
                      + ": "
                      + cause.getMessage()));
          continue;
        }
        List<String> ck = new ArrayList<>(chain);
        ck.add(m.getName());
        walk(ck, child);
      } else if (isRoute(m)) {
        handleRoute(chain, res, m);
      }
    }
  }

  private Map<String, Object> build() {
    RestClient client =
        RestClient.builder()
            .project(SENTINEL)
            .token("t")
            .space("recording.invalid")
            .httpClient(new RecordingHttpClient())
            .build();

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
        List<String> chain = new ArrayList<>();
        chain.add(m.getName());
        walk(chain, ns);
      }
    }

    plan.sort(java.util.Comparator.comparing(PlanRec::via));
    errors.sort(java.util.Comparator.comparing(ErrRec::key));

    List<Map<String, Object>> planList = new ArrayList<>();
    for (PlanRec pr : plan) {
      Map<String, Object> pm = new LinkedHashMap<>();
      pm.put("via", pr.via());
      pm.put("method", pr.method());
      pm.put("path", pr.path());
      pm.put("chain", pr.chain());
      pm.put("member", pr.member());
      pm.put("args", pr.args());
      planList.add(pm);
    }
    List<Map<String, Object>> errList = new ArrayList<>();
    for (ErrRec e : errors) {
      Map<String, Object> em = new LinkedHashMap<>();
      em.put("via", e.key());
      em.put("error", e.error());
      errList.add(em);
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("plan", planList);
    payload.put("errors", errList);
    return payload;
  }

  public static void main(String[] args) {
    RouteTestPlan tp = new RouteTestPlan();
    Map<String, Object> payload = tp.build();

    Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    System.out.println(gson.toJson(payload));

    if (!tp.errors.isEmpty()) {
      System.err.println(
          "route-test-plan: "
              + tp.errors.size()
              + " unreflectable route method(s) (plan incomplete)");
      System.exit(1);
    }
  }
}
